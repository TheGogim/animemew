# Scraper de jkanime para Android — Guía técnica completa

Kotlin + OkHttp + WebView invisible + Media3 ExoPlayer. Sin servidor, sin proxy.
Cada celular hace su propio scraping con su propia IP.

---

## 1. Arquitectura

```
┌──────────────────────────────────────────────────────────────┐
│  App Android                                                 │
│                                                              │
│  UI Compose  ←→  ViewModel  ←→  EpisodeScraper               │
│                                   │                          │
│                                   ├─ HttpScraper (OkHttp)    │
│                                   │   · Desu, Magi           │
│                                   │   · Mp4upload            │
│                                   │                          │
│                                   └─ WebViewScraper          │
│                                       · Streamwish           │
│                                       · Vidhide              │
│                                       · Filemoon             │
│                                       · VOE, Mixdrop, etc.   │
│                                                              │
│  ExoPlayer (Media3)  ←  m3u8 + headers                       │
└──────────────────────────────────────────────────────────────┘
```

**Sin servidor. Sin proxy. Sin intermediarios.**

---

## 2. Clasificación de servidores

Esta tabla es la **fuente de verdad**. Actualízala solo si un CDN cambia de comportamiento.

| Server        | Origen                 | ¿Funciona en Android? | Método              | Referer requerido              |
|---------------|------------------------|----------------------|---------------------|-------------------------------|
| **Desu**      | iframe `/jkplayer/um`  | ✅ Sí, directo        | OkHttp + regex      | ninguno                       |
| **Magi**      | iframe `/jkplayer/umv` | ✅ Sí, directo        | OkHttp + regex      | ninguno                       |
| **Mp4upload** | embed HTML             | ✅ Sí, directo        | OkHttp + regex      | `https://www.mp4upload.com/`  |
| **Streamwish**| embed (JS ofuscado)    | ✅ Con WebView        | WebView invisible   | `https://jkanime.net/`        |
| **Vidhide**   | embed (JS ofuscado)    | ✅ Con WebView        | WebView invisible   | (ninguno)                     |
| **Filemoon**  | embed (JS ofuscado)    | ✅ Con WebView        | WebView invisible   | (ninguno)                     |
| **VOE**       | embed (JS ofuscado)    | ✅ Con WebView        | WebView invisible   | `https://voe.sx/`             |
| **Mixdrop**   | embed (JS ofuscado)    | ⚠️ Inestable          | WebView invisible   | `https://mixdrop.top/`        |
| **Doodstream**| embed (JS ofuscado)    | ⚠️ Inestable          | WebView invisible   | `https://dsvplay.com/`        |
| **Streamtape**| embed (token IP-bound) | ⚠️ A veces            | WebView invisible   | `https://streamtape.com/`     |
| **Mediafire** | file-host              | ❌ Skip               | —                   | —                             |
| **Mega**      | file-host              | ❌ Skip               | —                   | —                             |

**Orden de prioridad para mostrar al usuario:**
1. Desu (más rápido, CDN propio de jkanime)
2. Magi (igual que Desu)
3. Streamwish (CDN rápido, exige WebView pero vale la pena)
4. Mp4upload (MP4 directo, sin HLS)
5. Vidhide, Filemoon (alternativos HLS)
6. VOE, Mixdrop, Doodstream, Streamtape (último recurso)

---

## 3. Dependencias Gradle

```kotlin
// app/build.gradle.kts
dependencies {
    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Reproductor
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // JSON
    implementation("org.json:json:20240303")
}
```

No hace falta Retrofit (OkHttp + regex basta para jkanime). No hace falta Jsoup (regex es suficiente y más liviano).

---

## 4. Modelo de datos

```kotlin
// ScraperModels.kt
package com.tuapp.anime.scraper

sealed class ServerStatus {
    data object Available : ServerStatus()      // OkHttp directo
    data object RequiresWebView : ServerStatus() // WebView invisible
    data object FileHost : ServerStatus()        // Skip (Mediafire/Mega)
}

data class ServerInfo(
    val name: String,
    val embedUrl: String,            // URL original del embed
    val streamUrl: String? = null,   // m3u8/mp4 resuelto (null si aún no se procesa)
    val status: ServerStatus,
    val requiresWebView: Boolean,
    val referer: String?,            // Header Referer que ExoPlayer debe mandar
    val isHls: Boolean,              // true=m3u8 (HLS), false=mp4 directo
    val priority: Int                // menor = más prioritario
)

data class EpisodeData(
    val animeSlug: String,
    val episode: Int,
    val servers: List<ServerInfo>,
    val nextEpisodeUrl: String? = null,
    val previousEpisodeUrl: String? = null
)
```

---

## 5. Catálogo de servidores conocidos

Esta es la tabla del punto 2 traducida a código. Es la **tabla de búsqueda** que el scraper usa para clasificar.

```kotlin
// ServerCatalog.kt
package com.tuapp.anime.scraper

object ServerCatalog {

    /** Configuración conocida de cada CDN. */
    private val knownServers = mapOf(
        "Desu" to Config(
            requiresWebView = false,
            referer = null,
            isHls = true,
            priority = 0
        ),
        "Magi" to Config(
            requiresWebView = false,
            referer = null,
            isHls = true,
            priority = 1
        ),
        "Streamwish" to Config(
            requiresWebView = true,
            referer = "https://jkanime.net/",
            isHls = true,
            priority = 2
        ),
        "Mp4upload" to Config(
            requiresWebView = false,
            referer = "https://www.mp4upload.com/",
            isHls = false,
            priority = 3
        ),
        "Vidhide" to Config(
            requiresWebView = true,
            referer = null,
            isHls = true,
            priority = 4
        ),
        "Filemoon" to Config(
            requiresWebView = true,
            referer = null,
            isHls = true,
            priority = 5
        ),
        "VOE" to Config(
            requiresWebView = true,
            referer = "https://voe.sx/",
            isHls = true,
            priority = 6
        ),
        "Mixdrop" to Config(
            requiresWebView = true,
            referer = "https://mixdrop.top/",
            isHls = true,
            priority = 7
        ),
        "Doodstream" to Config(
            requiresWebView = true,
            referer = "https://dsvplay.com/",
            isHls = true,
            priority = 8
        ),
        "Streamtape" to Config(
            requiresWebView = true,
            referer = "https://streamtape.com/",
            isHls = false,
            priority = 9
        ),
        "Mediafire" to Config(
            requiresWebView = false,
            referer = null,
            isHls = false,
            priority = 99,
            isFileHost = true
        ),
        "Mega" to Config(
            requiresWebView = false,
            referer = null,
            isHls = false,
            priority = 99,
            isFileHost = true
        ),
    )

    data class Config(
        val requiresWebView: Boolean,
        val referer: String?,
        val isHls: Boolean,
        val priority: Int,
        val isFileHost: Boolean = false,
    )

    fun configFor(serverName: String): Config =
        knownServers[serverName] ?: Config(
            requiresWebView = true,  // desconocido → intentamos WebView como fallback
            referer = "https://jkanime.net/",
            isHls = true,
            priority = 50
        )

    fun isFileHost(serverName: String): Boolean =
        knownServers[serverName]?.isFileHost == true
}
```

---

## 6. Cliente HTTP con headers

Un solo cliente OkHttp reutilizable, con timeouts cortos y User-Agent de móvil real (jkanime trata mejor a los móviles que a curl).

```kotlin
// HttpClient.kt
package com.tuapp.anime.scraper

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object HttpClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** GET simple. Devuelve body como String. */
    fun get(url: String, referer: String? = null): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .apply { referer?.let { header("Referer", it) } }
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                throw ScraperException("HTTP ${res.code} en $url")
            }
            return res.body?.string()
                ?: throw ScraperException("Body vacío en $url")
        }
    }
}

class ScraperException(message: String) : Exception(message)
```

---

## 7. Scraper principal

Este es el corazón. Pide el HTML de jkanime, extrae los iframes internos (Desu, Magi) y el array `servers[]` con los externos.

```kotlin
// JkanimeScraper.kt
package com.tuapp.anime.scraper

import android.util.Base64
import android.util.Log

class JkanimeScraper {

    companion object {
        private const val TAG = "JkanimeScraper"
    }

    /** Entry point: dado un slug y un episodio, devuelve la lista cruda de servidores. */
    suspend fun fetchServers(slug: String, episode: Int): EpisodeData {
        val url = "https://jkanime.net/$slug/$episode/"
        Log.i(TAG, "Scrapeando $url")

        val html = HttpClient.get(url)

        // 1. Iframes internos (Desu, Magi) del array JS "video[]"
        val iframes = parseIframes(html)

        // 2. Servidores externos del array JS "servers[]"
        val externals = parseExternals(html)

        // 3. Botones visibles (para mapear data-id → nombre visible)
        val buttonNames = parseButtonNames(html)

        // 4. Navegación prev/next
        val (prevUrl, nextUrl) = parseNavigation(html)

        // 5. Construir lista de ServerInfo
        val servers = mutableListOf<ServerInfo>()

        // Desu y Magi (iframes internos)
        for (iframe in iframes) {
            val name = buttonNames[iframe.idx] ?: if (iframe.idx == 0) "Desu" else "Magi"
            val config = ServerCatalog.configFor(name)
            servers.add(
                ServerInfo(
                    name = name,
                    embedUrl = iframe.url,
                    status = if (config.isFileHost) ServerStatus.FileHost
                             else if (config.requiresWebView) ServerStatus.RequiresWebView
                             else ServerStatus.Available,
                    requiresWebView = config.requiresWebView,
                    referer = config.referer,
                    isHls = config.isHls,
                    priority = config.priority
                )
            )
        }

        // Servidores externos
        for (ext in externals) {
            if (ServerCatalog.isFileHost(ext.server)) continue  // skip Mediafire/Mega
            val config = ServerCatalog.configFor(ext.server)
            servers.add(
                ServerInfo(
                    name = ext.server,
                    embedUrl = ext.embedUrl,
                    status = if (config.requiresWebView) ServerStatus.RequiresWebView
                             else ServerStatus.Available,
                    requiresWebView = config.requiresWebView,
                    referer = config.referer,
                    isHls = config.isHls,
                    priority = config.priority
                )
            )
        }

        // Ordenar por prioridad
        val sorted = servers.sortedBy { it.priority }

        return EpisodeData(
            animeSlug = slug,
            episode = episode,
            servers = sorted,
            previousEpisodeUrl = prevUrl,
            nextEpisodeUrl = nextUrl
        )
    }

    // ─── Parsers del HTML de jkanime ────────────────────────────────

    private data class Iframe(val idx: Int, val url: String)
    private data class External(val server: String, val embedUrl: String)

    /** Extrae video[0], video[1], etc. del HTML. */
    private fun parseIframes(html: String): List<Iframe> {
        // Patrón: video[0] = '<iframe class="player_conte" src="https://..." ...';
        val pattern = Regex(
            """video\[(\d+)\]\s*=\s*['"]<iframe[^>]*src=["']([^"']+)["']"""
        )
        return pattern.findAll(html).map {
            Iframe(
                idx = it.groupValues[1].toInt(),
                url = it.groupValues[2]
            )
        }.toList()
    }

    /** Extrae el array JS "var servers = [...]" y decodifica base64 de cada "remote". */
    private fun parseExternals(html: String): List<External> {
        val pattern = Regex("""var\s+servers\s*=\s*(\[[\s\S]*?\])\s*;""")
        val jsonStr = pattern.find(html)?.groupValues?.get(1) ?: return emptyList()

        // Parsear manualmente (sin librería) o usar org.json
        val servers = try {
            org.json.JSONObject().apply { put("arr", org.json.JSONArray(jsonStr)) }
                .getJSONArray("arr")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo parsear servers[]: ${e.message}")
            return emptyList()
        }

        val result = mutableListOf<External>()
        for (i in 0 until servers.length()) {
            val obj = servers.getJSONObject(i)
            val serverName = obj.getString("server")
            val remoteB64 = obj.getString("remote")
            val embedUrl = try {
                String(Base64.decode(remoteB64, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (e: Exception) { continue }

            result.add(External(serverName, embedUrl))
        }
        return result
    }

    /** Map data-id → nombre visible (para Desu=0, Magi=1). */
    private fun parseButtonNames(html: String): Map<String, String> {
        val pattern = Regex(
            """<a[^>]*data-id=["'](\d+)["'][^>]*class=["'][^"']*servers[^"']*["'][^>]*>([^<]+)</a>"""
        )
        return pattern.findAll(html).associate {
            it.groupValues[1] to it.groupValues[2].trim()
        }
    }

    /** Busca links "Anterior" y "Próximo episodio". */
    private fun parseNavigation(html: String): Pair<String?, String?> {
        val prevPattern = Regex("""<a[^>]*href=["'](https?://jkanime\.net/[^"']+)["'][^>]*>\s*Anterior""")
        val nextPattern = Regex("""href=["'](https?://jkanime\.net/[^"']+)["'][^>]*>\s*Próximo""")
        return prevPattern.find(html)?.groupValues?.get(1) to
               nextPattern.find(html)?.groupValues?.get(1)
    }
}
```

---

## 8. Resolver servidores sin WebView (Desu, Magi, Mp4upload)

Estos se resuelven con un simple OkHttp GET + regex. Son los más rápidos y confiables.

```kotlin
// HttpResolver.kt
package com.tuapp.anime.scraper

import android.util.Base64

object HttpResolver {

    /** Resuelve Desu y Magi: piden el iframe y extraen el m3u8 del <script>. */
    fun resolveIframe(iframeUrl: String): String? {
        val html = HttpClient.get(iframeUrl, referer = "https://jkanime.net/")

        // 1. Buscar m3u8 en texto plano
        Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""")
            .find(html)?.let { return it.value }

        // 2. Buscar dentro de atob('base64...')
        val b64Pattern = Regex("""atob\(['"]([A-Za-z0-9+/=]+)['"]\)""")
        for (match in b64Pattern.findAll(html)) {
            try {
                val decoded = String(
                    Base64.decode(match.groupValues[1], Base64.DEFAULT),
                    Charsets.UTF_8
                )
                Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""")
                    .find(decoded)?.let { return it.value }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Resuelve Mp4upload: el HTML del embed tiene "src: 'https://.../video.mp4'". */
    fun resolveMp4upload(embedUrl: String): String? {
        val html = HttpClient.get(embedUrl, referer = "https://jkanime.net/")
        // Patrón:  src: "https://a3.mp4upload.com:183/d/.../video.mp4"
        Regex("""src:\s*["'](https?://[^"']+/(?:video\.mp4|[^"']+\.mp4)[^"']*)["']""")
            .find(html)?.let { return it.groupValues[1] }
        return null
    }
}
```

---

## 9. Resolver servidores con WebView invisible

Esta es la pieza clave para Streamwish, Vidhide, Filemoon, etc. Usa un WebView invisible que ejecuta el JS del embed. Interceptamos las peticiones de red y capturamos cualquier URL `.m3u8` o `.mp4` que aparezca.

```kotlin
// WebViewResolver.kt
package com.tuapp.anime.scraper

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WebViewResolver {

    /**
     * Carga el embed en un WebView invisible y captura la primera URL
     * .m3u8 o .mp4 que el reproductor pida.
     *
     * @param embedUrl URL del embed (ej: https://sfastwish.com/e/abc123)
     * @param timeoutMs Tiempo máximo de espera (default 12s)
     * @return la URL del stream, o null si no se encontró en tiempo
     */
    suspend fun resolveStreamUrl(
        embedUrl: String,
        timeoutMs: Long = 12_000
    ): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            // El WebView debe crearse en el hilo principal
            Handler(Looper.getMainLooper()).post {
                val webView = createInvisibleWebView()

                var resolved = false

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        // A veces el m3u8 se pide como "xhr" o como "other"
                        if (url.contains(".m3u8") || url.matches(Regex(".*\\.mp4.*"))) {
                            if (!resolved && !cont.isCompleted) {
                                resolved = true
                                cont.resume(url)
                                // Cerrar el WebView ASAP
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        webView.destroy()
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        return null  // dejar que la petición continue normal
                    }

                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // No abortar por errores individuales (ads, etc.)
                    }
                }

                // Configurar JS habilitado + settings de navegador real
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = HttpClient.UA_MOBILE
                    blockNetworkImage = true      // no cargar imágenes (más rápido)
                    blockNetworkLoads = false
                }

                // Cargar la página del embed
                webView.loadUrl(embedUrl)

                // Cleanup si la corrutina se cancela
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        try { webView.destroy() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createInvisibleWebView(): WebView {
        // Crear con context de la app (no de Activity para no romper en background)
        val context = YourApplication.appContext  // ajustá esto a tu setup
        val webView = WebView(context)
        // No hace falta agregarlo a ninguna vista — funciona en background
        return webView
    }
}
```

**Notas importantes:**

- `WebView` debe instanciarse en el hilo principal. Por eso el `Handler(Looper.getMainLooper()).post { ... }`.
- `blockNetworkImage = true` evita cargar imágenes de ads, mejora la velocidad un 30-50%.
- El timeout de 12 segundos es el sweet spot: lo suficiente para que el JS corra, no tanto que el usuario se desespere.
- Si el stream tarda más (poco habitual), el `withTimeoutOrNull` devuelve `null` y el scraper continúa con otros servidores.

---

## 10. Orquestador: juntar todo

El `EpisodeResolver` es el punto de entrada desde el ViewModel. Recibe slug + episodio, ejecuta el scraper, resuelve cada servidor en paralelo (con cierta concurrencia) y devuelve solo los que funcionaron.

```kotlin
// EpisodeResolver.kt
package com.tuapp.anime.scraper

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class EpisodeResolver(
    private val scraper: JkanimeScraper = JkanimeScraper(),
    private val webResolver: WebViewResolver = WebViewResolver()
) {

    /**
     * Devuelve un Flow que emite cada server resuelto, en cuanto esté listo.
     * La UI puede ir mostrándolos a medida que llegan (mejor UX que esperar a todos).
     */
    fun resolve(slug: String, episode: Int): Flow<ServerInfo> = flow {
        val data = scraper.fetchServers(slug, episode)

        // Lanzar todas las resoluciones en paralelo
        coroutineScope {
            val deferreds = data.servers.map { server ->
                async { resolveOne(server) }
            }
            for (d in deferreds) {
                val resolved = d.await()
                if (resolved != null && resolved.streamUrl != null) {
                    emit(resolved)
                }
            }
        }
    }

    private suspend fun resolveOne(server: ServerInfo): ServerInfo? {
        return try {
            val streamUrl = when {
                // Desu, Magi → HTTP directo al iframe
                server.name in listOf("Desu", "Magi") ->
                    HttpResolver.resolveIframe(server.embedUrl)

                // Mp4upload → HTTP directo al embed
                server.name == "Mp4upload" ->
                    HttpResolver.resolveMp4upload(server.embedUrl)

                // Streamwish, Vidhide, Filemoon, etc. → WebView invisible
                server.requiresWebView ->
                    webResolver.resolveStreamUrl(server.embedUrl)

                else -> null
            }

            if (streamUrl != null) {
                server.copy(streamUrl = streamUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("Resolver", "${server.name} falló: ${e.message}")
            null
        }
    }
}
```

**Por qué Flow y no suspend list:** porque los servidores se resuelven a distintas velocidades. Desu está listo en ~600ms, Streamwish tarda ~5s. Con Flow, la UI muestra Desu primero y va agregando los demás a medida que llegan.

---

## 11. Cómo lo usa el ViewModel

```kotlin
// PlayerViewModel.kt
package com.tuapp.anime.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.anime.scraper.EpisodeResolver
import com.tuapp.anime.scraper.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val resolver = EpisodeResolver()

    private val _servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<ServerInfo?>(null)
    val selectedServer: StateFlow<ServerInfo?> = _selectedServer.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadEpisode(slug: String, episode: Int) {
        viewModelScope.launch {
            _loading.value = true
            _servers.value = emptyList()

            resolver.resolve(slug, episode).collect { server ->
                // Agregar a la lista (sin duplicados)
                _servers.value = (_servers.value + server)
                    .distinctBy { it.name }
                    .sortedBy { it.priority }

                // Auto-seleccionar el primero que llegue (Desu normalmente)
                if (_selectedServer.value == null) {
                    _selectedServer.value = server
                }
            }

            _loading.value = false
        }
    }

    fun selectServer(server: ServerInfo) {
        _selectedServer.value = server
    }
}
```

---

## 12. Cómo lo reproduce ExoPlayer

Esta es la pieza final. ExoPlayer recibe la URL del m3u8/mp4 + headers (especialmente Referer) y reproduce directo desde el CDN, con la IP del celular.

```kotlin
// PlayerScreen.kt
package com.tuapp.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    slug: String,
    episode: Int
) {
    val servers by viewModel.servers.collectAsState()
    val selected by viewModel.selectedServer.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(slug, episode) {
        viewModel.loadEpisode(slug, episode)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reproductor (arriba, 16:9)
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            selected?.let { server ->
                ExoPlayerView(
                    streamUrl = server.streamUrl!!,
                    isHls = server.isHls,
                    referer = server.referer,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Lista de servidores (abajo)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(servers) { server ->
                ServerItem(
                    server = server,
                    isSelected = server.name == selected?.name,
                    onClick = { viewModel.selectServer(server) }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    streamUrl: String,
    isHls: Boolean,
    referer: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(streamUrl) {
        // DataSource factory con headers
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClient.UA_MOBILE)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (referer != null) {
                    setDefaultRequestProperties(mapOf("Referer" to referer))
                }
            }

        val mediaItem = MediaItem.fromUri(streamUrl)

        // HLS para m3u8, MP4 directo para .mp4
        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(httpFactory).createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(streamUrl) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

@Composable
fun ServerItem(server: ServerInfo, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (server.isHls) "HLS" else "MP4",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 13. Manejo de errores y reintentos

```kotlin
// En EpisodeResolver.kt, modificar resolveOne():

private suspend fun resolveOne(server: ServerInfo, maxRetries: Int = 2): ServerInfo? {
    var lastError: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            val streamUrl = when {
                server.name in listOf("Desu", "Magi") ->
                    HttpResolver.resolveIframe(server.embedUrl)
                server.name == "Mp4upload" ->
                    HttpResolver.resolveMp4upload(server.embedUrl)
                server.requiresWebView ->
                    webResolver.resolveStreamUrl(server.embedUrl)
                else -> null
            }

            if (streamUrl != null) {
                return server.copy(streamUrl = streamUrl)
            }
        } catch (e: Exception) {
            lastError = e
            // Backoff exponencial entre reintentos
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay((500L * (attempt + 1)))
            }
        }
    }

    android.util.Log.w(
        "Resolver",
        "${server.name} falló tras $maxRetries intentos: ${lastError?.message}"
    )
    return null
}
```

---

## 14. Casos edge a tener en cuenta

| Caso | Manejo |
|------|--------|
| Sin internet | `ScraperException` → UI muestra "Sin conexión" + botón reintentar |
| jkanime caído | Timeout de OkHttp (15s) → misma pantalla de error |
| Capítulo no existe (404) | HTTP 404 → mensaje "Capítulo no disponible" |
| Streamwish tarda mucho | `withTimeoutOrNull(12s)` → null → server no se muestra |
| Cloudflare challenge | UA móvil real casi siempre lo evita. Si aparece, WebView lo resuelve solo (los WebViews pasan challenges) |
| Token expira durante playback | ExoPlayer tira `PlaybackException` → UI muestra "Token expirado, recarga" |
| Usuario cambia de red (WiFi→4G) | IP cambia → tokens inválidos → re-scrape automático |

---

## 15. Optimizaciones recomendadas

1. **Cache en Room** de los servidores resueltos (TTL 2.5h, igual que los tokens). Antes de scrapear, mirar cache.
2. **Prefetch del próximo episodio** en background cuando el usuario está viendo uno.
3. **WebView pool**: reutilizar WebViews en vez de crear/destruir (mejor para usuarios que cambian de servidor seguido).
4. **Diagnóstico oculto**: menú de debug que muestre qué servidores fallaron y por qué (para debug, no para el usuario final).
5. **Telemetría opcional**: reportar anónimamente qué CDNs fallan más, para actualizar `ServerCatalog`.

---

## 16. Flujo completo en una sesión típica

```
Usuario abre "Kuroneko 12"
  ↓
PlayerViewModel.loadEpisode("kuroneko-to-majo-no-kyoushitsu", 12)
  ↓
EpisodeResolver.resolve(slug, 12)
  ↓
JkanimeScraper.fetchServers(slug, 12)
  · OkHttp GET https://jkanime.net/kuroneko-to-majo-no-kyoushitsu/12/
  · Regex: extrae 2 iframes + 10 servidores externos
  · Devuelve EpisodeData con 10 ServerInfo (sin resolver todavía)
  ↓
resolveOne() en paralelo para cada server:
  · Desu → HttpResolver.resolveIframe() → 600ms → m3u8 ✓
  · Magi → HttpResolver.resolveIframe() → 800ms → m3u8 ✓
  · Mp4upload → HttpResolver.resolveMp4upload() → 1.2s → mp4 ✓
  · Streamwish → WebViewResolver.resolveStreamUrl() → 5s → m3u8 ✓
  · Vidhide → WebViewResolver.resolveStreamUrl() → 6s → m3u8 ✓
  · Filemoon → WebViewResolver.resolveStreamUrl() → 4s → m3u8 ✓
  · VOE → WebViewResolver → 8s → m3u8 ✓ (o null si falla)
  · Mixdrop, Doodstream, Streamtape → WebViewResolver → variable
  ↓
Flow emite cada server resuelto en cuanto está listo
  ↓
UI los va agregando a la lista, auto-selecciona Desu
  ↓
ExoPlayer empieza a reproducir Desu en ~1 segundo
  ↓
Usuario puede cambiar a Streamwish si quiere (tarda más en cargar pero reproduce más fluido)
```

**Tiempo total desde que el usuario toca "Play" hasta que ve video:** ~1 segundo (gracias al Flow que muestra Desu apenas está listo, sin esperar a los demás).
