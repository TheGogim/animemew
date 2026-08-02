# Scraper de jkanime para Android — Guía extendida

Complementa al documento `scraper_android_kotlin.md`. Cubre:

1. **Parches al WebViewResolver** que faltaban (Context de aplicación, lifecycle, etc.)
2. **Guía completa de URLs y slugs** de jkanime (cómo construir la URL correcta)
3. **Scraper de catálogo** — lista de animes, episodios por anime, búsqueda por título
4. **Matching Anilist → jkanime** — cómo conectar tu maqueta de Anilist con el scraper

---

# PARTE 1 — Parches al WebViewResolver

## 1.1 Application class para contexto global

El `WebViewResolver` necesita un `Context` para crear WebViews en background.
La forma correcta es tener una `Application` class con un companion object.

```kotlin
// AnimeApp.kt
package com.tuapp.anime

import android.app.Application
import android.content.Context

class AnimeApp : Application() {

    companion object {
        /**
         * Contexto de aplicación (no de Activity).
         * Lo usan componentes que necesitan crear Views en background,
         * como el WebViewResolver.
         */
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
```

Y en `AndroidManifest.xml`:

```xml
<application
    android:name=".AnimeApp"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    ...>
    <!-- ... activities ... -->
</application>
```

## 1.2 WebViewResolver completo y robusto

Esta versión incluye todo lo que faltaba:

```kotlin
// WebViewResolver.kt
package com.tuapp.anime.scraper

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tuapp.anime.AnimeApp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class WebViewResolver {

    /**
     * Carga el embed en un WebView invisible y captura la primera URL
     * .m3u8 o .mp4 que el reproductor pida.
     *
     * - Crea el WebView en el hilo principal (requisito de Android).
     * - Hace cleanup garantizado aunque la corrutina se cancele.
     * - Si el stream no aparece en `timeoutMs`, devuelve null.
     */
    suspend fun resolveStreamUrl(
        embedUrl: String,
        timeoutMs: Long = 12_000
    ): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->

            // El WebView debe crearse en el hilo principal
            Handler(Looper.getMainLooper()).post {

                // Doble check: si la corrutina ya se canceló, ni crear el WebView
                if (cont.isCompleted) return@post

                val webView = createInvisibleWebView()
                val resolved = AtomicBoolean(false)

                // Listener de red — interceptar todo
                webView.webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        // Detectar m3u8 o mp4 (incluso con query string)
                        val isStream = url.contains(".m3u8") ||
                                       url.matches(Regex(".*\\.mp4.*"))

                        if (isStream && resolved.compareAndSet(false, true)) {
                            // ¡Lo encontramos! Resumir la corrutina
                            if (cont.isActive) {
                                cont.resume(url)
                            }
                            // Cerrar el WebView ASAP (en main thread)
                            Handler(Looper.getMainLooper()).post {
                                destroyQuietly(webView)
                            }
                        }
                        // Devolver null = "no interceptes, deja que cargue normal"
                        return null
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // No abortar por errores individuales (ads, imágenes, etc.)
                        // Solo abortar si el main frame falla
                        if (request?.isForMainFrame == true &&
                            !cont.isCompleted &&
                            resolved.compareAndSet(false, true)) {
                            cont.resume(null)
                        }
                    }
                }

                // Configurar el WebView como navegador real
                configureWebView(webView)

                // Cargar la página
                webView.loadUrl(embedUrl)
            }

            // Cleanup garantizado si la corrutina se cancela
            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    try { destroyQuietly(webView) } catch (_: Exception) {}
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = HttpClient.UA_MOBILE
            blockNetworkImage = true           // no cargar imágenes (más rápido)
            blockNetworkLoads = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
    }

    private fun createInvisibleWebView(): WebView {
        // Context de aplicación — funciona en background, no requiere Activity
        return WebView(AnimeApp.appContext)
    }

    private fun destroyQuietly(webView: WebView) {
        try {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {}
    }
}
```

**Mejoras clave respecto a la versión anterior:**

1. `AtomicBoolean` para `resolved` → evita race conditions si el stream aparece varias veces
2. `cont.invokeOnCancellation` → cleanup garantizado si la corrutina se cancela
3. `destroyQuietly` → nunca rompe aunque el WebView ya esté destruido
4. `cacheMode = LOAD_NO_CACHE` → siempre pide fresco
5. `isForMainFrame` check → no aborta por errores de ads en iframes secundarios
6. `AnimeApp.appContext` → referencia explícita al contexto global

## 1.3 Configuración del Manifest

Asegurate de tener el permiso de Internet (probablemente ya lo tenés):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Y si tu app usa cleartext (HTTP sin TLS) en algún lado:

```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

(jkanime es HTTPS así que no hace falta, pero algunos CDNs como `mp4upload.com:183` sí lo requieren).

---

# PARTE 2 — Guía de URLs y slugs de jkanime

## 2.1 Estructura de las URLs

Todas las URLs de jkanime siguen este patrón:

```
https://jkanime.net/{slug}/{episodio}/
                  │      │
                  │      └─ número de capítulo (1, 2, 3, ... 220)
                  │
                  └─ identificador único del anime (kebab-case en romaji)
```

**Ejemplos reales:**

| Anime | URL completa |
|-------|--------------|
| Naruto cap 1 | `https://jkanime.net/naruto/1/` |
| Naruto cap 220 (final) | `https://jkanime.net/naruto/220/` |
| Naruto Shippuden cap 1 | `https://jkanime.net/naruto-shippuden/1/` |
| TenSura cap 1 | `https://jkanime.net/tensei-shitara-slime-datta-ken/1/` |
| Kuroneko cap 12 | `https://jkanime.net/kuroneko-to-majo-no-kyoushitsu/12/` |
| Boku no Hero cap 1 | `https://jkanime.net/boku-no-hero-academia/1/` |

## 2.2 Reglas del slug

El **slug** es el identificador único de cada anime en jkanime. Sigue estas reglas:

1. **Todo en minúsculas** — `Naruto` → `naruto`
2. **Romaji (transcripción del japonés a latín)** — `転生したらスライムだった件` → `tensei-shitara-slime-datta-ken`
3. **Palabras separadas por guiones** — `Boku no Hero Academia` → `boku-no-hero-academia`
4. **Sin acentos ni caracteres especiales** — `Día` → `dia`
5. **Sin articles en inglés** — `The Slime` → `slime` (a veces, no siempre)

## 2.3 Cómo conseguir el slug correcto

**NO intentes adivinar el slug a partir del título de Anilist.** A veces jkanime usa transliteraciones distintas a las oficiales.

**Ejemplo problemático:**
- Anilist: `That Time I Got Reincarnated as a Slime`
- Slug jkanime: `tensei-shitara-slime-datta-ken` (versión japonesa romanizada)

Si intentás generar el slug a partir del título en inglés, vas a fallar.

**La solución correcta es buscar el anime en jkanime por título y obtener el slug de ahí.**

### Endpoint de búsqueda de jkanime

jkanime tiene una página de búsqueda que acepta casi cualquier título (inglés, japonés romanizado, español):

```
https://jkanime.net/buscar/{query}/
```

Ejemplo:

```
https://jkanime.net/buscar/naruto/        → lista todos los animes con "naruto"
https://jkanime.net/buscar/slime/         → lista animes con "slime"
https://jkanime.net/buscar/Tensei/        → funciona con mayúsculas
```

La página devuelve HTML con resultados. Cada resultado tiene:

```html
<a href="https://jkanime.net/tensei-shitara-slime-datta-ken/" class="...">
    <img src="...封面..." />
    <h5>Tensei shitara Slime Datta Ken</h5>
</a>
```

### Scraper de búsqueda

```kotlin
// SearchScraper.kt
package com.tuapp.anime.scraper

data class SearchResult(
    val slug: String,
    val title: String,
    val coverUrl: String?
)

object SearchScraper {

    /**
     * Busca animes por título en jkanime.
     * Acepta español, inglés, romaji.
     *
     * @param query texto a buscar (ej: "naruto", "slime", "Tensei shitara")
     * @return lista de resultados con slug + título + carátula
     */
    fun search(query: String): List<SearchResult> {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://jkanime.net/buscar/$encoded/"
        val html = try {
            HttpClient.get(url, referer = "https://jkanime.net/")
        } catch (e: Exception) {
            return emptyList()
        }

        // Cada resultado es:
        // <a href="https://jkanime.net/{slug}/" ...>
        //   <img ... src="{cover}" />
        //   <h5>{title}</h5>
        // </a>

        val pattern = Regex(
            """<a[^>]*href="https://jkanime\.net/([a-z0-9-]+)/"[^>]*>""" +
            """[\s\S]*?<img[^>]*src="([^"]+)"[\s\S]*?<h5[^>]*>([^<]+)</h5>"""
        )

        return pattern.findAll(html).map { m ->
            SearchResult(
                slug = m.groupValues[1],
                coverUrl = m.groupValues[2].takeIf { it.isNotEmpty() },
                title = m.groupValues[3].trim()
            )
        }.distinctBy { it.slug }.toList()
    }
}
```

### Cómo usarlo para matching Anilist → jkanime

```kotlin
suspend fun findJkanimeSlug(anilistTitle: String, anilistTitleRomaji: String? = null): String? {
    // 1. Probar con el título en romaji (más probable que coincida)
    val candidates = mutableListOf<String>()
    anilistTitleRomaji?.let { candidates.add(it) }
    candidates.add(anilistTitle)

    for (query in candidates) {
        val results = SearchScraper.search(query)
        if (results.isNotEmpty()) {
            // Heurística: el primer resultado suele ser el correcto,
            // pero validamos por similitud de título
            return pickBestMatch(query, results)?.slug
        }
    }
    return null
}

private fun pickBestMatch(query: String, results: List<SearchResult>): SearchResult? {
    val q = query.lowercase().trim()
    // Buscar match exacto primero
    results.firstOrNull { it.title.lowercase() == q }?.let { return it }
    // Si no, match por inclusión
    results.firstOrNull { it.title.lowercase().contains(q) || q.contains(it.title.lowercase()) }
        ?.let { return it }
    // Si no, el primero
    return results.firstOrNull()
}
```

## 2.4 Validación: ¿el anime existe en jkanime?

Antes de intentar scrapear episodios, validá que el slug exista:

```kotlin
suspend fun slugExists(slug: String): Boolean {
    return try {
        val html = HttpClient.get("https://jkanime.net/$slug/")
        // Si la página dice "Página no encontrada", no existe
        !html.contains("Página no encontrada") &&
        !html.contains("404 Not Found") &&
        html.contains("og:title")
    } catch (e: Exception) {
        false
    }
}
```

## 2.5 Slug → URL de episodio

Una vez que tenés el slug, la URL de cualquier episodio es trivial:

```kotlin
fun episodeUrl(slug: String, episode: Int): String =
    "https://jkanime.net/$slug/$episode/"
```

**Ejemplos:**

```kotlin
episodeUrl("naruto", 1)                          // https://jkanime.net/naruto/1/
episodeUrl("naruto-shippuden", 1)                // https://jkanime.net/naruto-shippuden/1/
episodeUrl("tensei-shitara-slime-datta-ken", 12) // https://jkanime.net/tensei-shitara-slime-datta-ken/12/
```

---

# PARTE 3 — Scraper de catálogo y episodios

## 3.1 Modelo de datos

```kotlin
// CatalogModels.kt
package com.tuapp.anime.scraper

data class AnimePage(
    val slug: String,
    val title: String,
    val coverUrl: String,
    val synopsis: String,
    val status: String,          // "Concluido" o "En emisión"
    val totalEpisodes: Int,      // número del último capítulo
    val genres: List<String>,
    val airedDate: String?,      // ej: "Jueves, 03 de Octubre de 2002"
    val duration: String?,       // ej: "23 min. por episodio"
    val episodeList: List<EpisodeSummary>
)

data class EpisodeSummary(
    val number: Int,
    val thumbnailUrl: String?,   // algunos caps tienen thumbnail, otros no
    val url: String              // https://jkanime.net/{slug}/{number}/
)
```

## 3.2 Scraper de página de anime

```kotlin
// AnimePageScraper.kt
package com.tuapp.anime.scraper

object AnimePageScraper {

    /**
     * Scrapea la página principal de un anime.
     * Devuelve toda la info disponible: título, sinopsis, episodios, etc.
     */
    fun fetch(slug: String): AnimePage? {
        val url = "https://jkanime.net/$slug/"
        val html = try {
            HttpClient.get(url, referer = "https://jkanime.net/")
        } catch (e: Exception) {
            return null
        }

        // 404 check
        if (html.contains("Página no encontrada")) return null

        val title = parseTitle(html) ?: slug
        val cover = parseCover(html) ?: ""
        val synopsis = parseSynopsis(html) ?: ""
        val status = parseStatus(html) ?: "En emisión"
        val totalEpisodes = parseTotalEpisodes(html) ?: 0
        val genres = parseGenres(html)
        val airedDate = parseAiredDate(html)
        val duration = parseDuration(html)
        val episodes = parseEpisodeList(html, slug, totalEpisodes)

        return AnimePage(
            slug = slug,
            title = title,
            coverUrl = cover,
            synopsis = synopsis,
            status = status,
            totalEpisodes = totalEpisodes,
            genres = genres,
            airedDate = airedDate,
            duration = duration,
            episodeList = episodes
        )
    }

    // ─── Parsers ─────────────────────────────────────────────────

    /** <title>Naruto - anime Naruto online JkAnime</title> */
    private fun parseTitle(html: String): String? {
        return Regex("""<title>([^<]+) - anime[^<]*</title>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    /** <meta property="og:image" content="..." /> */
    private fun parseCover(html: String): String? {
        return Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)
    }

    /** <p class="scroll">...</p> */
    private fun parseSynopsis(html: String): String? {
        return Regex("""<p\s+class="scroll">([^<]+)</p>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    /** <li><span>Estado:</span> Concluido</li> */
    private fun parseStatus(html: String): String? {
        return Regex("""<li>\s*<span>Estado:</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    /** <li><span>Episodios:</span> 220</li> */
    private fun parseTotalEpisodes(html: String): Int? {
        return Regex("""<li>\s*<span>Episodios:</span>\s*(\d+)\s*</li>""").find(html)
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Lista de géneros del sidebar */
    private fun parseGenres(html: String): List<String> {
        // Buscar <li><a href="https://jkanime.net/genero/.../">Nombre</a></li>
        return Regex(
            """<a\s+href="https://jkanime\.net/genero/[^"]+">([^<]+)</a>"""
        ).findAll(html).map { it.groupValues[1].trim() }.toList()
    }

    private fun parseAiredDate(html: String): String? {
        return Regex("""<li>\s*<span>\s*Emitido:\s*</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    private fun parseDuration(html: String): String? {
        return Regex("""<li>\s*<span>Duracion:</span>\s*([^<]+)</li>""").find(html)
            ?.groupValues?.get(1)?.trim()
    }

    /**
     * Construye la lista de episodios.
     *
     * jkanime carga los episodios por JS (array `data`), pero en el HTML
     * estático ya podemos ver el total. Generamos las URLs manualmente.
     *
     * Si el anime tiene thumbnails por episodio (algunos los tienen),
     * podríamos scrapearlos. Por ahora solo generamos URLs.
     */
    private fun parseEpisodeList(html: String, slug: String, total: Int): List<EpisodeSummary> {
        if (total <= 0) return emptyList()
        return (1..total).map { n ->
            EpisodeSummary(
                number = n,
                thumbnailUrl = null,  // jkanime no siempre tiene thumbnails por cap
                url = "https://jkanime.net/$slug/$n/"
            )
        }
    }
}
```

## 3.3 Scraper de directorio (catálogo completo)

Si querés mostrar "Todos los animes" o "Animes que empiezan con A":

```kotlin
// DirectoryScraper.kt
package com.tuapp.anime.scraper

data class DirectoryEntry(
    val slug: String,
    val title: String,
    val coverUrl: String,
    val type: String,        // "Anime", "Película", "OVA"
    val status: String       // "Concluido", "En emisión"
)

object DirectoryScraper {

    /**
     * Lista animes por página del directorio.
     * Cada página tiene ~24 animes.
     *
     * @param page número de página (1, 2, 3, ..., 161)
     */
    fun fetchPage(page: Int = 1): List<DirectoryEntry> {
        val url = if (page <= 1) "https://jkanime.net/directorio/"
                  else "https://jkanime.net/directorio?p=$page"
        val html = try {
            HttpClient.get(url, referer = "https://jkanime.net/")
        } catch (e: Exception) {
            return emptyList()
        }

        // Cada anime está en un <article> o <div class="anime__item">
        // con un link a su página y su carátula
        val pattern = Regex(
            """<a[^>]*href="https://jkanime\.net/([a-z0-9-]+)/"[^>]*>[\s\S]*?""" +
            """<img[^>]*src="([^"]+)"[\s\S]*?""" +
            """<h5[^>]*>([^<]+)</h5>"""
        )

        return pattern.findAll(html).map { m ->
            DirectoryEntry(
                slug = m.groupValues[1],
                coverUrl = m.groupValues[2],
                title = m.groupValues[3].trim(),
                type = "Anime",
                status = "Concluido"
            )
        }.distinctBy { it.slug }.toList()
    }
}
```

## 3.4 Scraper de estrenos (estrenos recientes)

```kotlin
// ReleasesScraper.kt
package com.tuapp.anime.scraper

data class ReleaseEntry(
    val slug: String,
    val title: String,
    val episode: Int,
    val coverUrl: String,
    val url: String
)

object ReleasesScraper {

    /** Lista los últimos capítulos estrenados (página principal de jkanime). */
    fun fetchLatest(): List<ReleaseEntry> {
        val html = try {
            HttpClient.get("https://jkanime.net/")
        } catch (e: Exception) {
            return emptyList()
        }

        // Bloques de "últimos episodios" — cada uno es:
        // <a href="https://jkanime.net/{slug}/{ep}/" ...>
        //   <img src="..." />
        //   <h5>{title}</h5>
        //   <span>Episodio {ep}</span>
        // </a>
        val pattern = Regex(
            """<a[^>]*href="https://jkanime\.net/([a-z0-9-]+)/(\d+)/"[^>]*>[\s\S]*?""" +
            """<img[^>]*src="([^"]+)"[\s\S]*?""" +
            """<h5[^>]*>([^<]+)</h5>"""
        )

        return pattern.findAll(html).map { m ->
            ReleaseEntry(
                slug = m.groupValues[1],
                episode = m.groupValues[2].toIntOrNull() ?: 1,
                coverUrl = m.groupValues[3],
                title = m.groupValues[4].trim(),
                url = "https://jkanime.net/${m.groupValues[1]}/${m.groupValues[2]}/"
            )
        }.distinctBy { it.url }.toList()
    }
}
```

## 3.5 Integración con Anilist (matching completo)

Tu app maqueta ya tiene Anilist. Esto es cómo lo conectás con jkanime:

```kotlin
// AnimeRepository.kt
package com.tuapp.anime.data

import com.tuapp.anime.scraper.AnimePage
import com.tuapp.anime.scraper.AnimePageScraper
import com.tuapp.anime.scraper.SearchScraper
import com.tuapp.anime.scraper.slugExists

/**
 * Fusiona metadata de Anilist con datos de jkanime.
 *
 * - Anilist: cover, sinopsis, géneros, score, trailer, relaciones
 * - jkanime: episodios reales, URLs para scrapear streams
 *
 * Anilist es la fuente principal para mostrar info,
 * jkanime es la fuente para reproducir.
 */
class AnimeRepository(private val anilistApi: AnilistApi) {

    /**
     * Dado un anime de Anilist, encuentra su slug en jkanime y devuelve
     * la info combinada.
     *
     * 1. Busca en cache local por ID de Anilist
     * 2. Si no está, busca por título en jkanime
     * 3. Valida que el slug exista
     * 4. Scrapea la página del anime en jkanime para tener episodios
     */
    suspend fun loadAnime(anilistId: Int): AnimeFull? {
        // 1. Traer metadata de Anilist
        val anilist = anilistApi.getAnime(anilistId) ?: return null

        // 2. Buscar slug en jkanime (con cache)
        val slug = slugCache.get(anilistId) ?:
                   findSlugForAnilist(anilist) ?:
                   return AnimeFull(anilist = anilist, jkanime = null)

        // 3. Scrapear página de jkanime para episodios
        val jkanime = AnimePageScraper.fetch(slug)

        // 4. Guardar slug en cache
        slugCache.put(anilistId, slug)

        return AnimeFull(
            anilist = anilist,
            jkanime = jkanime
        )
    }

    private suspend fun findSlugForAnilist(anilist: AnilistAnime): String? {
        // Probar varios títulos (romaji, inglés, español)
        val candidates = buildList {
            anilist.titleRomaji?.let { add(it) }
            anilist.titleEnglish?.let { add(it) }
            anilist.titleNative?.let { add(it) }
        }

        for (query in candidates) {
            val results = SearchScraper.search(query)
            val match = pickBestMatch(query, results)
            if (match != null && slugExists(match.slug)) {
                return match.slug
            }
        }
        return null
    }

    private fun pickBestMatch(
        query: String,
        results: List<SearchScraper.SearchResult>
    ): String? {
        if (results.isEmpty()) return null
        val q = query.lowercase().trim()

        // Match exacto
        results.firstOrNull { it.title.lowercase() == q }?.let { return it.slug }

        // Match por inclusión bidireccional
        results.firstOrNull {
            it.title.lowercase().contains(q) || q.contains(it.title.lowercase())
        }?.let { return it.slug }

        // Primer resultado (suele ser el más relevante en jkanime)
        return results.first().slug
    }

    companion object {
        // Cache simple en memoria (puedes migrar a Room después)
        private val slugCache = mutableMapOf<Int, String>()
    }
}

data class AnimeFull(
    val anilist: AnilistAnime,
    val jkanime: AnimePage?
)
```

## 3.6 Heurísticas de matching (importante)

El matching Anilist → jkanime no es 100% perfecto. Algunos casos edge:

| Anilist dice | jkanime usa | Nota |
|--------------|-------------|------|
| `That Time I Got Reincarnated as a Slime` | `tensei-shitara-slime-datta-ken` | Usa romaji |
| `Attack on Titan` | `shingeki-no-kyojin` | Romaji |
| `My Hero Academia` | `boku-no-hero-academia` | Romaji |
| `Demon Slayer` | `kimetsu-no-yaiba` | Romaji |
| `Jujutsu Kaisen` | `jujutsu-kaisen` | Igual en ambos |
| `One Piece` | `one-piece` | Igual en ambos |

**Por eso es importante buscar por todos los títulos disponibles en Anilist (romaji, inglés, nativo) y no solo uno.**

### Mejoras adicionales para el matching

```kotlin
// Helpers extra para mejorar el matching

private fun normalizeTitle(s: String): String =
    s.lowercase()
     .replace(Regex("[^a-z0-9 ]"), "")  // quitar símbolos
     .replace(Regex("\\s+"), " ")        // normalizar espacios
     .trim()

private fun levenshtein(a: String, b: String): Int {
    // Implementación simple de distancia de Levenshtein
    // Para comparar títulos cuando no hay match exacto
    val m = a.length
    val n = b.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    for (i in 1..m) for (j in 1..n) {
        dp[i][j] = minOf(
            dp[i-1][j] + 1,
            dp[i][j-1] + 1,
            dp[i-1][j-1] + (if (a[i-1] == b[j-1]) 0 else 1)
        )
    }
    return dp[m][n]
}

private fun similarity(a: String, b: String): Double {
    val na = normalizeTitle(a)
    val nb = normalizeTitle(b)
    if (na.isEmpty() || nb.isEmpty()) return 0.0
    val dist = levenshtein(na, nb)
    return 1.0 - dist.toDouble() / maxOf(na.length, nb.length)
}
```

Usar en `pickBestMatch`:

```kotlin
private fun pickBestMatch(
    query: String,
    results: List<SearchScraper.SearchResult>
): String? {
    if (results.isEmpty()) return null

    // 1. Score por similitud
    val scored = results.map { r ->
        r to maxOf(
            similarity(query, r.title),
            similarity(normalizeTitle(query), normalizeTitle(r.title))
        )
    }.sortedByDescending { it.second }

    // 2. Si el mejor score > 0.7, lo aceptamos
    return scored.firstOrNull { it.second > 0.7 }?.first?.slug
        ?: scored.firstOrNull()?.first?.slug  // si no, el primero
}
```

---

# PARTE 4 — Flujo de uso completo

## 4.1 Caso típico: usuario abre un anime desde Anilist

```kotlin
// En tu ViewModel de detalle de anime
class AnimeDetailViewModel : ViewModel() {

    private val repository = AnimeRepository(AnilistApi())

    private val _state = MutableStateFlow<AnimeDetailState>(Loading)
    val state: StateFlow<AnimeDetailState> = _state

    fun load(anilistId: Int) {
        viewModelScope.launch {
            _state.value = Loading
            try {
                val anime = repository.loadAnime(anilistId)
                _state.value = if (anime != null) Loaded(anime) else NotFound
            } catch (e: Exception) {
                _state.value = Error(e.message ?: "Error desconocido")
            }
        }
    }
}

sealed class AnimeDetailState {
    data object Loading : AnimeDetailState()
    data class Loaded(val anime: AnimeFull) : AnimeDetailState()
    data class NotFound(val message: String = "Anime no encontrado en jkanime") : AnimeDetailState()
    data class Error(val message: String) : AnimeDetailState()
}
```

## 4.2 Caso típico: usuario toca "Reproducir capítulo 12"

```kotlin
// En tu ViewModel de player
class PlayerViewModel : ViewModel() {

    private val resolver = EpisodeResolver()

    fun loadEpisode(slug: String, episode: Int) {
        viewModelScope.launch {
            // Flow que emite cada server resuelto en cuanto está listo
            resolver.resolve(slug, episode).collect { server ->
                // Agregar a la lista de servidores disponibles
                _servers.value = (_servers.value + server)
                    .distinctBy { it.name }
                    .sortedBy { it.priority }

                // Auto-seleccionar el primero (Desu normalmente llega primero)
                if (_selectedServer.value == null) {
                    _selectedServer.value = server
                }
            }
        }
    }
}
```

## 4.3 Caso edge: anime no está en jkanime

```kotlin
// En la UI, cuando AnimeDetailState es NotFound
@Composable
fun AnimeNotFoundScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Este anime no está disponible en jkanime",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Puedes ver la información de Anilist igualmente",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

## 4.4 Caso edge: capítulo no tiene servidores disponibles

```kotlin
@Composable
fun NoServersScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            "No se pudieron cargar servidores",
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}
```

---

# PARTE 5 — Tests y validación

## 5.1 Test del scraper (sin Android)

Podés testear la mayor parte del scraper sin un dispositivo real, usando
JUnit + MockWebServer o directamente probando contra jkanime:

```kotlin
// JkanimeScraperTest.kt
class JkanimeScraperTest {

    @Test
    fun `fetch Naruto cap 1 returns Desu and Magi servers`() = runBlocking {
        val scraper = JkanimeScraper()
        val data = scraper.fetchServers("naruto", 1)

        assertTrue(data.servers.any { it.name == "Desu" })
        assertTrue(data.servers.any { it.name == "Magi" })
        assertTrue(data.servers.none { it.name == "Mediafire" })  // skip
        assertTrue(data.servers.none { it.name == "Mega" })        // skip
    }

    @Test
    fun `fetch TenSura cap 12 returns more than 5 servers`() = runBlocking {
        val scraper = JkanimeScraper()
        val data = scraper.fetchServers("tensei-shitara-slime-datta-ken", 12)
        assertTrue(data.servers.size >= 5)
    }
}
```

## 5.2 Test de matching Anilist → jkanime

```kotlin
class MatchingTest {

    @Test
    fun `find TenSura by English title`() = runBlocking {
        val results = SearchScraper.search("That Time I Got Reincarnated as a Slime")
        assertTrue(results.any { it.slug == "tensei-shitara-slime-datta-ken" })
    }

    @Test
    fun `find Naruto by simple title`() = runBlocking {
        val results = SearchScraper.search("Naruto")
        assertTrue(results.any { it.slug == "naruto" })
    }
}
```

---

# PARTE 6 — Resumen de archivos del scraper

| Archivo | Función | Líneas aprox |
|---------|---------|-------------|
| `HttpClient.kt` | OkHttp con UA móvil, timeouts, follow redirects | 50 |
| `ServerCatalog.kt` | Tabla de CDNs conocidos + config por servidor | 100 |
| `JkanimeScraper.kt` | Parser del HTML de un capítulo (iframes + servers[]) | 150 |
| `HttpResolver.kt` | Resolver Desu/Magi/Mp4upload con HTTP directo | 70 |
| `WebViewResolver.kt` | Resolver Streamwish y similares con WebView invisible | 130 |
| `EpisodeResolver.kt` | Orquestador con Flow + paralelismo | 80 |
| `SearchScraper.kt` | Buscar animes por título | 60 |
| `AnimePageScraper.kt` | Scrapear info de un anime (episodios, sinopsis) | 150 |
| `DirectoryScraper.kt` | Lista de animes por página | 60 |
| `ReleasesScraper.kt` | Últimos estrenos | 60 |
| `AnimeRepository.kt` | Fusión Anilist + jkanime con matching | 120 |
| **Total** | | **~1100** |

---

# PARTE 7 — Orden de implementación recomendado

1. **Día 1:** `HttpClient` + `ServerCatalog` + `JkanimeScraper` + `HttpResolver`
   - Test: scrapear Naruto cap 1 y reproducirlo con ExoPlayer
2. **Día 2:** `AnimeApp` + `WebViewResolver` completo
   - Test: scrapear Streamwish y reproducirlo
3. **Día 3:** `EpisodeResolver` con Flow + `PlayerViewModel` + `PlayerScreen`
   - Test: UI completa con cambio de servidor
4. **Día 4:** `SearchScraper` + `AnimePageScraper` + `AnimeRepository`
   - Test: buscar anime desde tu maqueta Anilist, ver lista de episodios
5. **Día 5:** `DirectoryScraper` + `ReleasesScraper` (opcionales)
   - Test: pantalla de inicio con estrenos
6. **Día 6-7:** Cache en Room, manejo de errores, optimizaciones

---

# PARTE 8 — Notas finales

## Sobre el matching Anilist → jkanime

No va a ser 100% perfecto. Algunos animes van a fallar el matching. Para esos casos:

1. Mostrar mensaje claro: "Anime no encontrado en jkanime"
2. Permitir búsqueda manual: input donde el usuario escribe el título
3. Reportar para mejorar el matching (telemetría anónima opcional)

## Sobre el cache

Implementá cache en Room para:
- Slug de Anilist → jkanime (permanente)
- Lista de episodios por anime (TTL 7 días)
- Servidores resueltos por episodio (TTL 2.5 horas, igual que tokens)

## Sobre el manejo de errores

Nunca muestres "Error 403" o "Error de red" al usuario. Siempre:
- "No se pudo cargar este servidor. Probá otro."
- "Capítulo no disponible temporalmente."
- "Sin conexión a internet."

El usuario no tiene que saber nada de CDNs, tokens ni IPs.

## Sobre el performance

- **Tiempo objetivo:** desde que el usuario toca "Play" hasta que ve video, máximo 2 segundos
- **Cache hit:** <100ms (instantáneo)
- **Desu/Magi fresh:** ~600ms
- **Streamwish con WebView:** ~5 segundos (acceptable porque Desu ya está reproduciendo)

## Sobre la actualización del scraper

jkanime cambia su HTML cada varios meses. Cuando pase:

1. El scraper va a empezar a fallar (no se encontrará `video[0] = '...'`)
2. Andá a jkanime.net en navegador, abrí DevTools, mirá el HTML
3. Actualizá el regex correspondiente en `JkanimeScraper.kt`
4. Listo, no requiere recompilar la app (es solo texto)

Si usás Aniyomi como referencia, cuando cambian algo en jkanime, la comunidad manda PR en horas. Podés mirar esos PRs para ver qué cambió.
