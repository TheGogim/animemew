package com.mew.animemew.scraper

import android.util.Base64
import android.util.Log

class JkanimeScraper {

    companion object {
        private const val TAG = "JkanimeScraper"
    }

    /** Entry point: dado un slug y un episodio, devuelve la lista cruda de servidores. */
    suspend fun fetchServers(slug: String, episode: Int, logger: (String) -> Unit = {}): EpisodeData {
        val url = "https://jkanime.net/$slug/$episode/"
        Log.i(TAG, "Scrapeando $url")
        logger("Solicitando HTML de $url")

        val html = try {
            HttpClient.get(url)
        } catch (e: Exception) {
            logger("Error HTTP al obtener página de servidores: ${e.message}")
            throw e
        }

        // 1. Iframes internos (Desu, Magi) del array JS "video[]"
        val iframes = parseIframes(html)
        logger("Encontrados ${iframes.size} iframes internos")

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
            val name = buttonNames[iframe.idx.toString()] ?: if (iframe.idx == 0) "Desu" else "Magi"
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

        // Parsear JSON
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
