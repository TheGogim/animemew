package com.mew.animemew.scraper.latanime

import android.util.Base64
import android.util.Log
import com.mew.animemew.scraper.HttpClient
import com.mew.animemew.scraper.ServerInfo
import com.mew.animemew.scraper.ServerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =========================================================
//  LatAnimeEpisodeScraper — Obtiene servidores de video de un episodio.
//
//  URL: https://latanime.org/ver/{slug}-episodio-{N}
//
//  ACTUALIZADO: Solo se permiten servidores que SÍ funcionan.
//  Los demás se bloquean para evitar timeouts de 15s por servidor.
//
//  Servidores que SÍ funcionan (confirmado con logs reales):
//  - uqload ✅ (.m3u8 por red)
//  - voe ✅ (.m3u8 por JS)
//  - mp4upload ✅ (.mp4 por HTTP directo, cuando el video no está borrado)
//
//  Servidores BLOQUEADOS (siempre fallan, causan timeout de 15s c/u):
//  - filemoon, doodstream, ok.ru, embedv, lulustream,
//    streamtape, mixdrop, dsvplay, bysekoze, hexload,
//    savefiles, mega, download
// =========================================================

object LatAnimeEpisodeScraper {

    private const val TAG = "LatAnimeEpisode"
    private const val BASE_URL = "https://latanime.org"

    // Servidores que SÍ funcionan y se intentarán
    private val WORKING_SERVERS = setOf(
        "uqload",      // ✅ .m3u8 por red
        "voe",         // ✅ .m3u8 por JS
        "mp4upload"    // ✅ .mp4 por HTTP directo
    )

    // Todos los demás servidores se BLOQUEAN automáticamente

    /**
     * Obtiene los servidores de video disponibles para un episodio.
     * Solo devuelve servidores que funcionan (uqload, voe, mp4upload).
     *
     * @param slug Slug del anime en Latanime (ej: "black-clover-latino")
     * @param episode Número de episodio
     * @return Lista de ServerInfo con language="lat"
     */
    suspend fun fetch(slug: String, episode: Int, debugLogger: (String) -> Unit = {}): List<ServerInfo> {
        return withContext(Dispatchers.IO) {
            val url = "$BASE_URL/ver/$slug-episodio-$episode"
            debugLogger("LatAnime: descargando episodio $url")

            try {
                val html = HttpClient.get(url, referer = BASE_URL)

                if (html.contains("Página no encontrada") || html.contains("404 Not Found")) {
                    debugLogger("LatAnime: ❌ Episodio no encontrado (404)")
                    return@withContext emptyList()
                }

                // Parsear todos los data-player="BASE64"
                val serverPattern = Regex("""data-player="([^"]+)"[^>]*>([^<]+)""")
                val matches = serverPattern.findAll(html).toList()

                if (matches.isEmpty()) {
                    debugLogger("LatAnime: ❌ No se encontraron servidores")
                    return@withContext emptyList()
                }

                debugLogger("LatAnime: ${matches.size} servidores en la página")

                val servers = mutableListOf<ServerInfo>()
                for (match in matches) {
                    val b64 = match.groupValues[1]
                    val serverName = match.groupValues[2].trim().lowercase()

                    // Solo procesar servers que funcionan
                    if (serverName !in WORKING_SERVERS) {
                        debugLogger("LatAnime: ⏭️ '$serverName' bloqueado (no funciona)")
                        continue
                    }

                    // Decodificar Base64 → URL del iframe
                    val embedUrl = try {
                        String(Base64.decode(b64, Base64.DEFAULT))
                    } catch (e: Exception) {
                        debugLogger("LatAnime: ❌ Error decodificando Base64 de '$serverName'")
                        continue
                    }

                    if (!embedUrl.startsWith("http")) {
                        debugLogger("LatAnime: ❌ URL inválida para '$serverName': $embedUrl")
                        continue
                    }

                    debugLogger("LatAnime: ✅ Server '$serverName' → $embedUrl")

                    // mp4upload es HTTP directo, los demás necesitan WebView
                    val isHttpServer = serverName == "mp4upload"

                    servers.add(
                        ServerInfo(
                            name = serverName,
                            embedUrl = embedUrl,
                            streamUrl = null,
                            status = if (isHttpServer) ServerStatus.Available else ServerStatus.RequiresWebView,
                            requiresWebView = !isHttpServer,
                            referer = BASE_URL,
                            isHls = false,
                            priority = if (isHttpServer) 1 else 5,
                            language = "lat"
                        )
                    )
                }

                debugLogger("LatAnime: ${servers.size} servidores válidos (uqload, voe, mp4upload)")
                Log.i(TAG, "✅ ${servers.size} servidores para $slug ep$episode")
                return@withContext servers

            } catch (e: Exception) {
                debugLogger("LatAnime: ❌ Error: ${e.message}")
                Log.e(TAG, "Error en fetch: ${e.message}")
                return@withContext emptyList()
            }
        }
    }
}
