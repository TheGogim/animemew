package com.mew.animemew.scraper.tioanime

import android.util.Log
import com.mew.animemew.scraper.HttpClient
import java.net.URLEncoder

object TioAnimeSearchScraper {

    private const val BASE = "https://tioanime.com"
    private const val TAG = "TioAnimeSearch"

    private val titleToSlugCache = mutableMapOf<String, String>()

    fun search(query: String, debugLogger: (String) -> Unit = {}): List<TioAnimeSearchResult> {
        val normalized = normalizeTitle(query)
        Log.i(TAG, "=== search llamada con '$query' (normalizado: '$normalized') ===")

        // 1. Caché
        titleToSlugCache[normalized]?.let { cachedSlug ->
            Log.i(TAG, "✅ Cache hit: '$normalized' → $cachedSlug")
            return listOf(TioAnimeSearchResult(cachedSlug, query, null))
        }

        // 2. Buscar
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE/directorio?q=$encoded"
        Log.i(TAG, "GET $url")

        val html = try {
            HttpClient.get(url, referer = BASE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error HTTP: ${e.message}")
            return emptyList()
        }

        Log.i(TAG, "HTML recibido: ${html.length} chars")

        val results = mutableListOf<TioAnimeSearchResult>()

        // NUEVO: estructura real de tioanime (verificada con curl):
        // <article class="anime">
        //     <a href="/anime/{slug}">
        //         <div class="thumb"><figure><img src="/uploads/portadas/X.jpg" alt="img"></figure></div>
        //         <h3 class="title">{titulo}</h3>
        //     </a>
        // </article>
        //
        // Regex: captura href="/anime/SLUG" + el <h3 class="title">TITULO</h3> que sigue
        val articleRegex = Regex(
            """<a[^>]+href="/anime/([^"]+)"[^>]*>\s*<div[^>]*>.*?<h3[^>]*>([^<]+)</h3>""",
            RegexOption.DOT_MATCHES_ALL
        )
        articleRegex.findAll(html).forEach { m ->
            val slug = m.groupValues[1].trim()
            val title = m.groupValues[2].trim()
            // Construir coverUrl completa
            val coverUrl = "https://tioanime.com/uploads/portadas/"
            if (slug.isNotBlank() && title.isNotBlank()) {
                results.add(TioAnimeSearchResult(slug, title, null))
                Log.i(TAG, "  Resultado: '$title' → /anime/$slug")
            }
        }

        Log.i(TAG, "${results.size} resultados para '$query'")

        if (results.isNotEmpty()) {
            val best = results.firstOrNull {
                normalizeTitle(it.title) == normalized
            } ?: results.first()
            titleToSlugCache[normalized] = best.slug
            Log.i(TAG, "Cacheado: '$normalized' → ${best.slug}")
        }

        return results.distinctBy { it.slug }
    }

    fun normalizeTitle(s: String): String {
        return s.lowercase()
            .replace(":", " ")
            .replace("-", " ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
