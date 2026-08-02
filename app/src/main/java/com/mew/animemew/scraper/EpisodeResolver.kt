package com.mew.animemew.scraper

import android.util.Log
import com.mew.animemew.scraper.latanime.LatAnimeEpisodeScraper
import com.mew.animemew.scraper.latanime.LatAnimeSearchScraper
import com.mew.animemew.scraper.tioanime.TioAnimeEpisodeScraper
import com.mew.animemew.scraper.tioanime.TioAnimeSearchScraper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

class EpisodeResolver(
    private val scraper: JkanimeScraper = JkanimeScraper(),
    private val webResolver: WebViewResolver = WebViewResolver()
) {

    private val TAG = "EpisodeResolver"

    // Servidores que NO intentaremos resolver (DRM o dominios caídos)
    private val BLOCKED_SERVERS = setOf(
        "Okru",      // DRM
        "Maru",      // DRM (mail.ru)
        "Netu",      // DRM (hqq.tv)
        "Doodstream", // dominio caído
        "StreamSB"   // suele fallar
    )

    fun resolve(
        slug: String,
        episode: Int,
        logger: (String) -> Unit = {},
        titleForTioAnime: String? = null,
        languagePreference: String = "sub",
        titleEnglish: String? = null,
        latSlugPreResolved: String? = null,
        preferredServerName: String? = null  // NUEVO v9.1: server cacheado
    ): Flow<ServerInfo> = channelFlow {
        Log.i(TAG, "=== resolve: slug=$slug ep=$episode title=$titleForTioAnime langPref=$languagePreference preferred=$preferredServerName ===")

        coroutineScope {
            // ================================================
            //  NUEVO v9.1: Si hay server preferido (cacheado), resolverlo PRIMERO
            //  de forma BLOQUEANTE (no en paralelo con Desu)
            //  Solo para subtitulados (languagePreference == "sub")
            // ================================================
            if (preferredServerName != null && languagePreference == "sub") {
                Log.i(TAG, "💾 CACHE: Resolviendo server preferido '$preferredServerName' PRIMERO...")
                logger("Cache: resolviendo servidor preferido '$preferredServerName'...")
                var cacheResolved = false

                try {
                    // Buscar en JKanime
                    val data = scraper.fetchServers(slug, episode, logger)
                    Log.i(TAG, "💾 CACHE: JKanime devolvió ${data.servers.size} servers")

                    val preferred = data.servers.find {
                        it.name.equals(preferredServerName, ignoreCase = true)
                    }
                    if (preferred != null) {
                        Log.i(TAG, "💾 CACHE: ✅ Encontrado '$preferredServerName' en JKanime, resolviendo...")
                        logger("Cache: ✅ Encontrado en JKanime, resolviendo...")
                        val resolved = resolveOne(preferred, logger)
                        if (resolved?.streamUrl != null) {
                            Log.i(TAG, "💾 CACHE: ⚡ Emitido: ${resolved.name}")
                            send(resolved)
                            cacheResolved = true
                        } else {
                            Log.w(TAG, "💾 CACHE: ❌ '$preferredServerName' falló, continuando con flujo normal")
                        }
                    } else {
                        Log.w(TAG, "💾 CACHE: '$preferredServerName' no está en JKanime, probando TioAnime...")
                    }

                    // Si no está en JKanime, buscar en TioAnime
                    if (!cacheResolved && titleForTioAnime != null) {
                        val cleanedTitle = titleForTioAnime.replace(":", " ")
                            .replace(Regex("\\s+"), " ").trim()
                        val results = TioAnimeSearchScraper.search(cleanedTitle, logger)
                        if (results.isNotEmpty()) {
                            val q = TioAnimeSearchScraper.normalizeTitle(cleanedTitle)
                            val best = results.firstOrNull {
                                TioAnimeSearchScraper.normalizeTitle(it.title) == q
                            } ?: results.first()
                            val tioServers = TioAnimeEpisodeScraper.fetch(best.slug, episode, logger)
                            Log.i(TAG, "💾 CACHE: TioAnime devolvió ${tioServers.size} servers")

                            val preferredTio = tioServers.find {
                                it.name.equals(preferredServerName, ignoreCase = true)
                            }
                            if (preferredTio != null) {
                                Log.i(TAG, "💾 CACHE: ✅ Encontrado '$preferredServerName' en TioAnime, resolviendo...")
                                logger("Cache: ✅ Encontrado en TioAnime, resolviendo...")
                                val resolved = resolveOne(preferredTio, logger)
                                if (resolved?.streamUrl != null) {
                                    Log.i(TAG, "💾 CACHE: ⚡ Emitido: ${resolved.name}")
                                    send(resolved)
                                    cacheResolved = true
                                }
                            }
                            // Guardar lentos de TioAnime para Fase 2
                            tioSlowServers.addAll(tioServers.filter {
                                it.name != "YourUpload" &&
                                !it.name.equals(preferredServerName, ignoreCase = true)
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💾 CACHE: Error: ${e.message}")
                }

                if (cacheResolved) {
                    Log.i(TAG, "💾 CACHE: ✅ Server preferido resuelto, continuando con resto en paralelo...")
                    logger("Cache: ✅ Servidor preferido cargado, buscando más...")
                } else {
                    Log.i(TAG, "💾 CACHE: ❌ Server preferido no disponible, flujo normal...")
                    logger("Cache: ❌ Servidor preferido no disponible, cargando normal...")
                }
            }

            // ================================================
            //  NUEVO v9.0: Si preference == "lat", resolver Latanime PRIMERO
            //  y emitir TODOS los servers lat antes de empezar con subs
            // ================================================
            var latSlug: String? = latSlugPreResolved

            if (languagePreference == "lat" && titleForTioAnime != null) {
                // Si no nos pasaron el slug pre-resuelto, buscarlo ahora
                if (latSlug == null) {
                    logger("LatAnime: resolviendo servidor latino...")
                    try {
                        latSlug = LatAnimeSearchScraper.search(titleForTioAnime, titleEnglish, logger)
                    } catch (e: Exception) {
                        Log.e(TAG, "LatAnime search error: ${e.message}")
                    }
                } else {
                    logger("LatAnime: anime encontrado en latino, resolviendo servidores...")
                }

                if (latSlug != null) {
                    logger("LatAnime: ✅ Anime encontrado, esperando servidores...")
                    // Resolver TODOS los servers de Latanime antes de continuar
                    try {
                        val latServers = LatAnimeEpisodeScraper.fetch(latSlug, episode, logger)
                        for (server in latServers) {
                            val resolved = resolveLatAnimeServer(server, logger)
                            if (resolved?.streamUrl != null) {
                                Log.i(TAG, "⚡ LAT emit: ${resolved.name}")
                                send(resolved)
                            }
                        }
                        logger("LatAnime: servidores lat resueltos, continuando con fallback sub...")
                    } catch (e: Exception) {
                        Log.e(TAG, "LatAnime fetch error: ${e.message}")
                        logger("LatAnime: error: ${e.message}")
                    }
                } else {
                    logger("LatAnime: ❌ No disponible en latino, usando subtitulado...")
                }
            }

            // ================================================
            //  FASE 1: Servidores RÁPIDOS (HTTP directo) — JKanime + TioAnime
            // ================================================
            Log.i(TAG, "FASE 1: resolviendo servidores rápidos (HTTP)...")
            launch {
                try {
                    val data = scraper.fetchServers(slug, episode, logger)
                    val fastServers = data.servers.filter { server ->
                        server.name in listOf("Desu", "Magi", "Mp4upload")
                    }
                    Log.i(TAG, "FASE 1: ${fastServers.size} servidores rápidos")

                    val deferreds = fastServers.map { server ->
                        async { resolveOne(server, logger) }
                    }
                    for (d in deferreds) {
                        val resolved = d.await()
                        if (resolved?.streamUrl != null) {
                            Log.i(TAG, "⚡ FAST emit: ${resolved.name}")
                            send(resolved)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "FASE 1 error: ${e.message}")
                }
            }

            // ================================================
            //  FASE 1b: TioAnime búsqueda + scrape (en paralelo)
            // ================================================
            launch {
                if (titleForTioAnime.isNullOrBlank()) return@launch
                try {
                    val cleanedTitle = titleForTioAnime.replace(":", " ")
                        .replace(Regex("\\s+"), " ").trim()
                    Log.i(TAG, "TioAnime: buscando '$cleanedTitle'")

                    val results = TioAnimeSearchScraper.search(cleanedTitle, logger)
                    if (results.isEmpty()) return@launch

                    val q = TioAnimeSearchScraper.normalizeTitle(cleanedTitle)
                    val best = results.firstOrNull {
                        TioAnimeSearchScraper.normalizeTitle(it.title) == q
                    } ?: results.first()
                    Log.i(TAG, "TioAnime: elegido '${best.title}'")

                    val tioServers = TioAnimeEpisodeScraper.fetch(best.slug, episode, logger)
                    Log.i(TAG, "TioAnime: ${tioServers.size} servidores")

                    // YourUpload es rápido (suele dar .mp4 directo)
                    val fast = tioServers.filter { it.name == "YourUpload" }
                    val deferreds = fast.map { server ->
                        async { resolveOne(server, logger) }
                    }
                    for (d in deferreds) {
                        val resolved = d.await()
                        if (resolved?.streamUrl != null) {
                            Log.i(TAG, "⚡ FAST emit: ${resolved.name}")
                            send(resolved)
                        }
                    }

                    // Guardar los lentos para la Fase 2
                    tioSlowServers.addAll(tioServers.filter { it.name != "YourUpload" })
                } catch (e: Exception) {
                    Log.e(TAG, "TioAnime error: ${e.message}")
                }
            }

            // ================================================
            //  FASE 2: Esperar 3s, luego servidores LENTOS (WebView)
            // ================================================
            launch {
                delay(3000)  // dar tiempo a que el player arranque con los rápidos
                Log.i(TAG, "FASE 2: resolviendo servidores lentos (WebView) en lotes...")

                // Recolectar servidores lentos de jkanime
                val jkSlowDeferred = async {
                    try {
                        val data = scraper.fetchServers(slug, episode, logger)
                        data.servers
                            .filter { it.name !in listOf("Desu", "Magi", "Mp4upload") }
                            .filter { it.name !in BLOCKED_SERVERS }
                    } catch (e: Exception) { emptyList() }
                }
                val jkSlow = jkSlowDeferred.await()
                val allSlow = jkSlow + tioSlowServers.toList()
                tioSlowServers.clear()

                Log.i(TAG, "FASE 2: ${allSlow.size} servidores lentos a procesar")

                // Procesar en lotes de 3 (no saturar el main thread con WebViews)
                allSlow.chunked(3).forEach { batch ->
                    val deferreds = batch.map { server ->
                        async { resolveOne(server, logger) }
                    }
                    for (d in deferreds) {
                        val resolved = d.await()
                        if (resolved?.streamUrl != null) {
                            Log.i(TAG, "🐢 SLOW emit: ${resolved.name}")
                            send(resolved)
                        }
                    }
                }

                Log.i(TAG, "FASE 2 completada")
            }
        }

        Log.i(TAG, "=== resolve completado ===")
    }

    // Buffer temporal para servidores lentos de TioAnime
    private val tioSlowServers = mutableListOf<ServerInfo>()

    private suspend fun resolveOne(server: ServerInfo, logger: (String) -> Unit, maxRetries: Int = 1): ServerInfo? {
        val st = server.name
        Log.i(TAG, "resolveOne: '$st' — requiresWebView=${server.requiresWebView}")

        if (st in BLOCKED_SERVERS) {
            Log.i(TAG, "  '$st' bloqueado — saltando")
            return null
        }

        if (st.lowercase() in listOf("mega", "mediafire", "1fichier")) {
            Log.i(TAG, "  '$st' es file host — saltando")
            return null
        }

        try {
            val streamUrl = when {
                st in listOf("Desu", "Magi") -> {
                    Log.i(TAG, "  '$st' → HttpResolver.resolveIframe")
                    HttpResolver.resolveIframe(server.embedUrl)
                }
                st == "Mp4upload" -> {
                    Log.i(TAG, "  '$st' → HttpResolver.resolveMp4upload")
                    HttpResolver.resolveMp4upload(server.embedUrl)
                }
                server.requiresWebView -> {
                    Log.i(TAG, "  '$st' → WebViewResolver")
                    webResolver.resolveStreamUrl(server.embedUrl)
                }
                else -> null
            }

            if (streamUrl != null) {
                Log.i(TAG, "  ✅ '$st' resuelto")
                return server.copy(streamUrl = streamUrl)
            } else {
                Log.w(TAG, "  ⚠️ '$st' devolvió null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ '$st' error: ${e.message}")
        }
        return null
    }

    /**
     * NUEVO v9.0: Resolver un servidor de Latanime.
     *
     * - mp4upload: HTTP directo con referer de latanime
     * - Todos los demás: WebViewResolver (ya intercepta cualquier .mp4/.m3u8)
     */
    private suspend fun resolveLatAnimeServer(server: ServerInfo, logger: (String) -> Unit): ServerInfo? {
        val st = server.name
        logger("LatAnime: resolviendo '$st'")

        return try {
            val streamUrl = when {
                // mp4upload: HTTP directo con regex de Latanime
                st == "mp4upload" -> {
                    Log.i(TAG, "  LatAnime '$st' → HttpResolver.resolveMp4uploadLatAnime")
                    HttpResolver.resolveMp4uploadLatAnime(server.embedUrl)
                }
                // Todos los demás: WebViewResolver
                else -> {
                    Log.i(TAG, "  LatAnime '$st' → WebViewResolver")
                    webResolver.resolveStreamUrl(server.embedUrl)
                }
            }

            if (streamUrl != null) {
                logger("LatAnime: ✅ '$st' resuelto")
                Log.i(TAG, "  ✅ LatAnime '$st' resuelto")
                server.copy(streamUrl = streamUrl, isHls = streamUrl.contains(".m3u8"))
            } else {
                logger("LatAnime: ⚠️ '$st' devolvió null")
                Log.w(TAG, "  ⚠️ LatAnime '$st' devolvió null")
                null
            }
        } catch (e: Exception) {
            logger("LatAnime: ❌ '$st' error: ${e.message}")
            Log.e(TAG, "  ❌ LatAnime '$st' error: ${e.message}")
            null
        }
    }
}
