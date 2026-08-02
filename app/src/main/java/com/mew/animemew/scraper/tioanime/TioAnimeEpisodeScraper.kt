package com.mew.animemew.scraper.tioanime

import android.util.Log
import com.mew.animemew.scraper.HttpClient
import com.mew.animemew.scraper.ServerInfo
import com.mew.animemew.scraper.ServerStatus

object TioAnimeEpisodeScraper {

    private const val BASE = "https://tioanime.com"
    private const val TAG = "TioAnimeEpisode"

    fun fetch(
        tioSlug: String,
        episode: Int,
        debugLogger: (String) -> Unit = {}
    ): List<ServerInfo> {
        val url = "$BASE/ver/$tioSlug-$episode"
        Log.i(TAG, "=== fetch llamada: $url ===")
        debugLogger("TioAnimeEpisode: GET $url")

        val html = try {
            HttpClient.get(url, referer = BASE)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error HTTP: ${e.message}")
            debugLogger("TioAnimeEpisode error: ${e.message}")
            return emptyList()
        }

        Log.i(TAG, "HTML recibido: ${html.length} chars")

        if (html.contains("404") && html.contains("Not Found")) {
            Log.w(TAG, "❌ 404 - episodio no encontrado")
            return emptyList()
        }

        val servers = mutableListOf<ServerInfo>()

        // Regex para arrays tipo ["Nombre","https://...",0,0]
        val arrayRegex = Regex(
            """\[\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*,\s*\d+\s*,\s*\d+\s*\]"""
        )
        val matches = arrayRegex.findAll(html).toList()
        Log.i(TAG, "Regex encontró ${matches.size} matches")

        matches.forEach { m ->
            val serverName = m.groupValues[1].trim()
            val embedUrl = m.groupValues[2].replace("\\/", "/")
            Log.i(TAG, "  Match: name='$serverName' url='$embedUrl'")

            if (embedUrl.startsWith("http")) {
                val displayName = serverName.replaceFirstChar { it.uppercase() }
                val requiresWebView = serverName.lowercase() in listOf(
                    "streamsb", "amus", "mepu", "netu", "maru", "fembed", "mixdrop", "mp4upload",
                    "yourupload", "okru"
                )
                // NUEVO: el referer debe ser el dominio del embed (no tioanime.com)
                // porque los servers de video validan el Referer.
                // Ej: yourupload.com → https://www.yourupload.com
                val referer = extractOrigin(embedUrl)
                servers.add(
                    ServerInfo(
                        name = displayName,
                        embedUrl = embedUrl,
                        streamUrl = null,
                        status = if (requiresWebView) ServerStatus.RequiresWebView else ServerStatus.Available,
                        requiresWebView = requiresWebView,
                        referer = referer,
                        isHls = embedUrl.contains(".m3u8"),
                        priority = 50
                    )
                )
                debugLogger("TioAnimeEpisode: servidor '$displayName' encontrado (referer=$referer)")
            }
        }

        Log.i(TAG, "✅ ${servers.size} servidores totales")
        return servers.distinctBy { it.embedUrl }
    }

    /**
     * NUEVO: extrae el origin de una URL.
     * "https://www.yourupload.com/embed/xxx" → "https://www.yourupload.com"
     * "https://ok.ru/videoembed/123" → "https://ok.ru"
     */
    private fun extractOrigin(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            BASE  // fallback a tioanime
        }
    }
}
