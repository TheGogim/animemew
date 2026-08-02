package com.mew.animemew.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.scraper.EpisodeResolver
import com.mew.animemew.scraper.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mew.animemew.data.sync.SyncManager
import com.mew.animemew.data.season.SeasonChainResolver
import com.mew.animemew.data.airing.AiringController
import com.mew.animemew.data.local.AnimeListCrossRef
import com.mew.animemew.data.local.LocalAnimeEntity
import com.mew.animemew.data.local.WatchHistoryEntity
import com.mew.animemew.data.AnimeRepository

data class CurrentEpisodeData(
    val slug: String,
    val episode: Int,
    val title: String,
    val coverUrl: String,
    val totalEpisodes: Int,
    val anilistId: Int,
    val isAiring: Boolean = false,
    val nextEpisodeTimestamp: Long = 0L
)

data class NextEpisodeInfo(
    val slug: String,
    val episode: Int,
    val title: String,
    val coverUrl: String,
    val totalEpisodes: Int,
    val anilistId: Int,
    val label: String
)

sealed class PlayerState {
    data class Loading(val log: String = "") : PlayerState()
    data class Playing(
        val streamUrl: String,
        val referer: String?,
        val isHls: Boolean,
        val currentServerName: String,
        val availableServers: List<ServerInfo>,
        val startPositionMs: Long = 0L,
        val episodeLabel: String = ""
    ) : PlayerState()
    data class Error(val message: String, val log: String = "") : PlayerState()
}

class PlayerViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val resolver = EpisodeResolver()
    private val dao = com.mew.animemew.data.local.AnimeDatabase.getDatabase(application).animeDao()
    private val syncManager = SyncManager.getInstance(application)
    private val seasonChainResolver = SeasonChainResolver.getInstance(application)
    // NUEVO: repositorio para consultar AniList al terminar episodio
    private val animeRepository = AnimeRepository()
    // NUEVO: controlador centralizado de "En espera"
    private val airingController = AiringController.getInstance(application)

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _nextEpisodeInfo = MutableStateFlow<NextEpisodeInfo?>(null)
    val nextEpisodeInfo: StateFlow<NextEpisodeInfo?> = _nextEpisodeInfo.asStateFlow()

    private val _currentData = MutableStateFlow<CurrentEpisodeData?>(null)
    val currentData: StateFlow<CurrentEpisodeData?> = _currentData.asStateFlow()

    private val _currentLabel = MutableStateFlow("")
    val currentLabel: StateFlow<String> = _currentLabel.asStateFlow()

    private var isResolved = false
    private var lastSavedPosition = 0L
    private var seasonFinished = false
    private var hasStartedLoading = false
    // NUEVO: cuando true, el primer servidor se guarda pero NO se
    // reproduce hasta que el interstitial se cierre
    private var waitingForAd = false
    private var pendingFirstServer: ServerInfo? = null

    // NUEVO: contador de intentos de "ya lo desactivé" en diálogo de adblocker
    private var adblockRetryCount = 0

    // NUEVO: StateFlow para mostrar diálogo de adblocker desde la UI
    private val _showAdblockDialog = MutableStateFlow(false)
    val showAdblockDialog: StateFlow<Boolean> = _showAdblockDialog.asStateFlow()

    // NUEVO: StateFlow para forzar cierre de la app (después de 4 intentos)
    private val _forceCloseApp = MutableStateFlow(false)
    val forceCloseApp: StateFlow<Boolean> = _forceCloseApp.asStateFlow()

    fun loadEpisode(slug: String, episode: Int, title: String = "", coverUrl: String = "",
                     totalEpisodes: Int = 0, anilistId: Int = 0, isAiring: Boolean = false,
                     nextEpisodeTimestamp: Long = 0L) {
        val current = _currentData.value
        if (hasStartedLoading && current?.slug == slug && current.episode == episode && !isResolved) {
            return
        }
        hasStartedLoading = true
        isResolved = false

        seasonFinished = false
        _nextEpisodeInfo.value = null

        _currentData.value = CurrentEpisodeData(slug, episode, title, coverUrl, totalEpisodes, anilistId, isAiring, nextEpisodeTimestamp)
        Log.i("PlayerVM", "=== loadEpisode: $title E$episode (anilistId=$anilistId, isAiring=$isAiring, nextTs=$nextEpisodeTimestamp) ===")

        // NUEVO: verificar adblocker ANTES de hacer cualquier cosa.
        // Si se detecta, mostrar diálogo y NO cargar el episodio.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val adManager = com.mew.animemew.data.ads.AdManager.getInstance(getApplication())
            // NUEVO: Si el usuario es premium, no chequear adblocker
            val isBlocked = adManager.isAdblockerBlockingPlayback()
            Log.i("PlayerVM", "🛡️ Adblock check: isBlocked=$isBlocked")
            if (isBlocked) {
                Log.w("PlayerVM", "🛡️ Adblocker detectado, mostrando diálogo")
                _showAdblockDialog.value = true
                return@launch
            }

            // Si no hay adblocker, continuar con el flujo normal
            loadEpisodeInternal(slug, episode, title, coverUrl, totalEpisodes, anilistId, isAiring, nextEpisodeTimestamp)
        }
    }

    /**
     * NUEVO: Usuario presionó "Ya lo desactivé" en el diálogo de adblocker.
     * Re-verifica. Si era mentira, muestra diálogo de nuevo.
     * Si era verdad, continúa con la carga.
     * Después de 4 intentos fallidos, fuerza cierre de la app.
     */
    fun onUserDisabledAdblocker() {
        _showAdblockDialog.value = false
        adblockRetryCount++
        Log.i("PlayerVM", "Usuario dice que desactivó adblocker (intento $adblockRetryCount)")

        if (adblockRetryCount >= 4) {
            // 4to intento → cerrar app
            Log.w("PlayerVM", "🚫 4 intentos fallidos, forzando cierre de app")
            _forceCloseApp.value = true
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val adManager = com.mew.animemew.data.ads.AdManager.getInstance(getApplication())
            // NUEVO: Usar el método que respeta premium
            val isBlocked = adManager.isAdblockerBlockingPlayback()
            if (isBlocked) {
                Log.w("PlayerVM", "🛡️ Adblocker sigue activo, mostrando diálogo de nuevo")
                _showAdblockDialog.value = true
            } else {
                Log.i("PlayerVM", "✅ Adblocker desactivado, continuando con carga")
                val current = _currentData.value ?: return@launch
                loadEpisodeInternal(
                    current.slug, current.episode, current.title, current.coverUrl,
                    current.totalEpisodes, current.anilistId, current.isAiring, current.nextEpisodeTimestamp
                )
            }
        }
    }

    /**
     * NUEVO: Usuario presionó "Cerrar" en el diálogo de adblocker.
     * Resetea el contador y notifica a la UI para que saque al usuario del player.
     */
    fun onAdblockDialogDismiss() {
        _showAdblockDialog.value = false
        adblockRetryCount = 0
        // La UI se encarga de navegar atrás
    }

    /**
     * Lógica original de loadEpisode (extraída a función separada).
     */
    private fun loadEpisodeInternal(slug: String, episode: Int, title: String = "", coverUrl: String = "",
                     totalEpisodes: Int = 0, anilistId: Int = 0, isAiring: Boolean = false,
                     nextEpisodeTimestamp: Long = 0L) {
        // NUEVO: verificar si toca interstitial ANTES de cargar servidores.
        // Si toca, marcamos waitingForAd=true. El resolver empezará en paralelo
        // pero el primer servidor NO se reproducirá hasta que el ad se cierre.
        val adManager = com.mew.animemew.data.ads.AdManager.getInstance(getApplication())
        val needInterstitial = adManager.shouldShowInterstitial()
        waitingForAd = needInterstitial
        pendingFirstServer = null
        if (needInterstitial) {
            Log.i("PlayerVM", "📺 Mostrando interstitial antes de cargar episodio (carga en paralelo)")
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val debugLog = StringBuilder()
            val logger: (String) -> Unit = { msg ->
                debugLog.append("$msg\n")
                Log.i("PlayerVM-Scraper", msg)
                // Solo actualizar el estado de loading si NO estamos mostrando
                // un mensaje específico de "latino" (para que no se sobrescriba)
                val currentState = _state.value
                if (currentState !is PlayerState.Playing) {
                    val currentMsg = (currentState as? PlayerState.Loading)?.log ?: ""
                    // Si el mensaje actual contiene "latino", no sobrescribir con logs
                    if (!currentMsg.contains("latino") && !currentMsg.contains("Buscando")) {
                        _state.value = PlayerState.Loading(debugLog.toString())
                    }
                }
            }

            try {
                val history = dao.getWatchHistoryForAnime(slug)
                val initialPos = if (history != null && history.episodeNumber == episode) history.progressMs else 0L
                lastSavedPosition = initialPos

                val label = computeEpisodeLabel(anilistId, episode)
                _currentLabel.value = label

                // NUEVO v9.0: obtener preferencia de idioma del usuario
                val languagePrefs = com.mew.animemew.data.local.LanguagePreferences(getApplication())
                val languagePreference = languagePrefs.languageFlow.first()

                // NUEVO v9.0: Si preference == "lat", buscar slug de Latanime ANTES de resolver
                // y obtener título en inglés de AniList
                var titleEnglish: String? = null
                var latSlugPreResolved: String? = null

                if (languagePreference == "lat" && anilistId > 0) {
                    _state.value = PlayerState.Loading("Buscando versión latino...")
                    try {
                        val media = animeRepository.getAnimeDetails(anilistId)
                        titleEnglish = media?.title?.english
                        Log.i("PlayerVM", "Título inglés: $titleEnglish")

                        // Buscar slug de Latanime antes de abrir el player
                        latSlugPreResolved = com.mew.animemew.scraper.latanime.LatAnimeSearchScraper.search(
                            title, titleEnglish
                        ) { msg -> Log.i("PlayerVM", msg) }

                        if (latSlugPreResolved != null) {
                            Log.i("PlayerVM", "✅ Slug Latanime pre-resuelto: $latSlugPreResolved")
                            _state.value = PlayerState.Loading("Cargando servidores latino...")
                        } else {
                            Log.i("PlayerVM", "❌ No encontrado en Latanime, usando sub")
                            _state.value = PlayerState.Loading("No disponible en latino, cargando sub...")
                        }
                    } catch (e: Exception) {
                        Log.w("PlayerVM", "Error pre-buscando Latanime: ${e.message}")
                    }
                }

                val serversFound = mutableListOf<ServerInfo>()

                // NUEVO v9.1: obtener server preferido del cache (solo sub)
                var preferredServer: String? = null
                if (languagePreference == "sub" && anilistId > 0) {
                    preferredServer = com.mew.animemew.data.local.ServerCache(getApplication())
                        .getPreferredServer(anilistId)
                    if (preferredServer != null) {
                        Log.i("PlayerVM", "💾 Server cacheado: $preferredServer")
                    }
                }

                // NUEVO v9.0: pasar languagePreference, titleEnglish, latSlugPreResolved y preferredServer
                resolver.resolve(
                    slug, episode, logger,
                    titleForTioAnime = title,
                    languagePreference = languagePreference,
                    titleEnglish = titleEnglish,
                    latSlugPreResolved = latSlugPreResolved,
                    preferredServerName = preferredServer  // NUEVO v9.1
                ).collect { server ->
                    if (server.streamUrl != null) {
                        serversFound.add(server)

                        if (serversFound.size == 1) {
                            // NUEVO: si estamos esperando el ad, guardar el server
                            // pero NO reproducir todavía
                            if (waitingForAd) {
                                pendingFirstServer = server
                                Log.i("PlayerVM", "⏳ Primer servidor listo (${server.name}) esperando cierre de interstitial")
                                // Lanzar el interstitial AHORA (los servidores ya cargaron)
                                launchInterstitialAd()
                            } else {
                                // Sin interstitial: reproducir directo
                                _state.value = PlayerState.Playing(
                                    streamUrl = server.streamUrl,
                                    referer = server.referer,
                                    isHls = server.isHls,
                                    currentServerName = server.name,
                                    availableServers = serversFound.toList(),
                                    startPositionMs = lastSavedPosition,
                                    episodeLabel = label
                                )

                                launch {
                                    val next = computeNextEpisode(slug, episode, totalEpisodes, anilistId, title, coverUrl)
                                    _nextEpisodeInfo.value = next
                                }
                            }
                        } else {
                            // Servidores adicionales: actualizar lista si ya está reproduciendo
                            val currentState = _state.value
                            if (currentState is PlayerState.Playing) {
                                _state.value = currentState.copy(availableServers = serversFound.toList())
                            }
                            // Si estamos esperando el ad, también actualizar el pending
                            if (waitingForAd && pendingFirstServer != null) {
                                pendingFirstServer = pendingFirstServer!!.copy(
                                    // Mantener el primero pero agregar los demás a la lista
                                )
                                // Cuando se cierre el ad, usaremos pendingFirstServer + serversFound
                            }
                        }
                    }
                }

                if (serversFound.isEmpty()) {
                    _state.value = PlayerState.Error("No se encontró ningún servidor disponible.", debugLog.toString())
                } else {
                    isResolved = true
                }

            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.localizedMessage ?: "Error desconocido.", debugLog.toString())
            }
        }
    }

    /**
     * NUEVO: Lanza el interstitial desde el AdManager.
     * Cuando se cierra, llama a onAdClosed() que reproduce el primer
     * servidor pendiente (si ya estaba cargado).
     */
    private fun launchInterstitialAd() {
        val adManager = com.mew.animemew.data.ads.AdManager.getInstance(getApplication())
        // Necesitamos una Activity para mostrar el interstitial
        // La obtenemos del StateFlow de actividad si está disponible
        // Si no, cancelamos la espera y reproducimos
        val activity = currentActivity
        if (activity == null) {
            Log.w("PlayerVM", "No hay Activity para mostrar interstitial, reproduciendo directo")
            onAdClosed()
            return
        }

        adManager.showInterstitial(activity) {
            // onAdClosed callback
            onAdClosed()
        }
    }

    /**
     * NUEVO: Callback cuando el interstitial se cierra.
     * Si el primer servidor ya estaba cargado, lo reproduce.
     */
    private fun onAdClosed() {
        Log.i("PlayerVM", "✅ Interstitial cerrado, verificando servidor pendiente...")
        waitingForAd = false

        val pending = pendingFirstServer
        if (pending != null && pending.streamUrl != null) {
            Log.i("PlayerVM", "▶️ Reproduciendo servidor pendiente: ${pending.name}")
            val current = _currentData.value ?: return
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val label = _currentLabel.value
                _state.value = PlayerState.Playing(
                    streamUrl = pending.streamUrl!!,
                    referer = pending.referer,
                    isHls = pending.isHls,
                    currentServerName = pending.name,
                    availableServers = listOf(pending),
                    startPositionMs = lastSavedPosition,
                    episodeLabel = label
                )

                launch {
                    val next = computeNextEpisode(current.slug, current.episode, current.totalEpisodes, current.anilistId, current.title, current.coverUrl)
                    _nextEpisodeInfo.value = next
                }
            }
        } else {
            Log.i("PlayerVM", "⏳ Interstitial cerrado pero servidores aún cargando, esperando...")
            // El resolver todavía está corriendo, cuando encuentre el primer server
            // verificará que waitingForAd=false y reproducirá normalmente
        }
    }

    // NUEVO: referencia débil a la Activity actual para mostrar interstitials
    @Volatile
    private var currentActivity: android.app.Activity? = null

    /**
     * NUEVO: Llamar desde el PlayerScreen composable para setear la Activity.
     * Necesario para que el ViewModel pueda mostrar el interstitial.
     */
    fun setActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }

    private suspend fun computeEpisodeLabel(anilistId: Int, episode: Int): String {
        if (anilistId == 0) return "E$episode"
        val chain = seasonChainResolver.getCached(anilistId) ?: return "E$episode"
        val index = chain.indexOf(anilistId)
        if (index < 0) return "E$episode"
        return chain.seasons[index].episodeLabel(episode)
    }

    private suspend fun computeNextEpisode(
        currentSlug: String, currentEp: Int, currentTotal: Int,
        currentAnilistId: Int, currentTitle: String, currentCoverUrl: String
    ): NextEpisodeInfo? {
        if (currentEp < currentTotal) {
            val label = computeEpisodeLabel(currentAnilistId, currentEp + 1)
            return NextEpisodeInfo(currentSlug, currentEp + 1, currentTitle, currentCoverUrl, currentTotal, currentAnilistId, label)
        }
        if (currentAnilistId > 0) {
            val chain = seasonChainResolver.getCached(currentAnilistId)
            if (chain != null) {
                val currentIndex = chain.indexOf(currentAnilistId)
                if (currentIndex >= 0) {
                    var nextIndex = currentIndex + 1
                    while (nextIndex < chain.totalSeasons) {
                        val nextSeason = chain.seasons[nextIndex]
                        val slugResult = seasonChainResolver.resolveSlug(nextSeason)
                        if (slugResult != null) {
                            val label = nextSeason.episodeLabel(1)
                            return NextEpisodeInfo(slugResult.slug, 1, nextSeason.title, nextSeason.coverUrl, slugResult.totalEpisodes, nextSeason.anilistId, label)
                        }
                        nextIndex++
                    }
                }
            }
        }
        return null
    }

    fun playNext() {
        val next = _nextEpisodeInfo.value ?: return

        isResolved = false
        seasonFinished = false
        hasStartedLoading = false
        _nextEpisodeInfo.value = null
        lastSavedPosition = 0L

        val isAiring = _currentData.value?.isAiring ?: false
        val nextTs = _currentData.value?.nextEpisodeTimestamp ?: 0L
        loadEpisode(next.slug, next.episode, next.title, next.coverUrl, next.totalEpisodes, next.anilistId, isAiring, nextTs)
    }

    /**
     * NUEVO v10: Guarda el progreso desde el Cast (navegador).
     */
    fun saveCastProgress(progress: Long, total: Long) {
        val current = _currentData.value ?: return
        if (total <= 0) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            saveToHistory(
                current.slug, current.title, current.coverUrl,
                current.episode, progress, total,
                current.totalEpisodes, current.anilistId,
                current.isAiring, current.nextEpisodeTimestamp
            )
            syncManager.pushAsync()
        }
    }

    fun selectServer(server: ServerInfo, currentPositionMs: Long) {
        val currentState = _state.value
        lastSavedPosition = currentPositionMs
        if (currentState is PlayerState.Playing && server.streamUrl != null) {
            _state.value = currentState.copy(
                streamUrl = server.streamUrl,
                referer = server.referer,
                isHls = server.isHls,
                currentServerName = server.name,
                startPositionMs = lastSavedPosition
            )

            // NUEVO v9.1: Guardar server preferido si es subtitulado
            if (server.language != "lat") {
                val current = _currentData.value
                if (current != null && current.anilistId > 0) {
                    com.mew.animemew.data.local.ServerCache(getApplication())
                        .savePreferredServer(current.anilistId, server.name)
                    Log.i("PlayerVM", "💾 Server preferido guardado: ${server.name}")
                }
            }
        }
    }

    /**
     * NUEVO: Guarda el progreso del episodio.
     *
     * Reglas:
     * - Si es el último episodio Y vio 80%+:
     *   → handleSeasonFinish (marca como "En espera" o "Visto")
     *   → NO guarda progreso (progreso = 0 en "En espera")
     *
     * - Si NO es el último episodio Y vio 80%+:
     *   → Avanza al siguiente episodio automáticamente
     *   → Guarda progreso = 0 del siguiente episodio
     *
     * - Si vio menos del 80%:
     *   → Guarda el progreso actual (para "Seguir Viendo")
     */
    fun saveProgress(progress: Long, total: Long) {
        val current = _currentData.value ?: return
        if (total <= 0) return

        val percentage = progress.toFloat() / total.toFloat()
        val isLastEpisode = current.episode >= current.totalEpisodes

        Log.i("PlayerVM", "saveProgress: E${current.episode}/${current.totalEpisodes} " +
                "progress=${progress}ms total=${total}ms percent=${(percentage * 100).toInt()}% " +
                "isLast=$isLastEpisode")

        if (percentage >= 0.8f && isLastEpisode) {
            // ÚLTIMO episodio visto 80%+ → manejar fin de temporada
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                handleSeasonFinish(current.slug, current.title, current.coverUrl, current.anilistId, current.isAiring, current.totalEpisodes, current.nextEpisodeTimestamp)
            }
            return
        }

        var episodeToSave = current.episode
        var progressToSave = progress
        var durationToSave = total

        if (percentage >= 0.8f && !isLastEpisode) {
            // Episodio intermedio visto 80%+ → avanzar al siguiente
            episodeToSave = current.episode + 1
            progressToSave = 0L
            durationToSave = 0L
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            saveToHistory(current.slug, current.title, current.coverUrl, episodeToSave, progressToSave, durationToSave,
                          current.totalEpisodes, current.anilistId, current.isAiring, current.nextEpisodeTimestamp)
            syncManager.pushAsync()
        }
    }

    /**
     * NUEVO: handleSeasonFinish con AiringController.
     *
     * PASOS:
     * 1. Consultar AniList SIEMPRE (no solo si isAiring=false)
     *    para obtener el timestamp del próximo episodio
     * 2. Si AniList dice FINISHED:
     *    - Si hay episodio siguiente disponible → habilitar
     *    - Si no → marcar como Visto
     * 3. Si AniList dice RELEASING:
     *    - Marcar como "En espera" con timestamp + 3h buffer
     *    - AiringController verificará disponibilidad cuando se cumpla
     * 4. Si hay siguiente temporada (chain) → crear entrada en historial
     */
    private suspend fun handleSeasonFinish(slug: String, title: String, coverUrl: String, anilistId: Int, isAiring: Boolean, totalEpisodes: Int, nextEpisodeTimestamp: Long) {
        if (seasonFinished) return
        seasonFinished = true

        Log.i("PlayerVM", "=== Temporada finalizada: $title E$totalEpisodes (isAiring=$isAiring) ===")

        // NUEVO: Consultar AniList SIEMPRE para tener info fresca
        var actualIsAiring = isAiring
        var actualNextTs = nextEpisodeTimestamp
        var anilistTotalEps = totalEpisodes

        if (anilistId > 0) {
            try {
                Log.i("PlayerVM", "Consultando AniList para verificar estado de emisión...")
                val media = animeRepository.getAnimeDetails(anilistId)
                if (media != null) {
                    val status = media.status?.name
                    anilistTotalEps = media.episodes ?: totalEpisodes
                    Log.i("PlayerVM", "AniList status: $status, episodes: $anilistTotalEps (nosotros: $totalEpisodes)")

                    when (status) {
                        "RELEASING" -> {
                            actualIsAiring = true
                            // Obtener timestamp del próximo episodio
                            val airingAt = media.nextAiringEpisode?.airingAt?.toLong() ?: 0L
                            actualNextTs = if (airingAt > 0L) {
                                airingAt + AiringController.BUFFER_SECONDS
                            } else 0L
                            Log.i("PlayerVM", "✅ Anime sigue en emisión. Próximo ep ts=$airingAt, con buffer=$actualNextTs")
                        }
                        "FINISHED" -> {
                            // NUEVO: NO marcar como visto automáticamente.
                            // Si AniList tiene más episodios de los que vimos,
                            // dejamos en "En espera" para que el AiringController
                            // verifique disponibilidad en scrapers.
                            if (anilistTotalEps > totalEpisodes) {
                                // Hay más episodios que no conocíamos
                                actualIsAiring = true  // mantener en espera
                                actualNextTs = 0L  // sin fecha, el AiringController buscará disponibilidad
                                Log.i("PlayerVM", "Anime finalizado pero AniList tiene $anilistTotalEps eps y vimos $totalEpisodes → mantener en espera para habilitar siguientes")
                            } else {
                                // Vimos todos los episodios que AniList reporta
                                actualIsAiring = false
                                actualNextTs = 0L
                                Log.i("PlayerVM", "Anime finalizado y vimos todos los eps → marcar como Visto")
                            }
                        }
                        else -> {
                            // NOT_YET_RELEASED, CANCELLED, HIATUS — mantener estado actual
                            Log.i("PlayerVM", "Anime con status=$status, manteniendo estado")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Error consultando AniList: ${e.message}")
            }
        }

        // CASO A: Anime sigue en emisión (o finalizado pero con más eps) → "En espera"
        if (actualIsAiring) {
            Log.i("PlayerVM", "Marcando como 'En espera' (nextTs=$actualNextTs)")

            // FIX CRÍTICO: NO usar maxOf(totalEpisodes, anilistTotalEps).
            // totalEpisodes debe ser lo que jkanime tiene disponible (ej: 13),
            // NO lo que AniList dice que tendrá (ej: 24).
            // Si inflamos totalEpisodes, la app pensará que ep 13 no es el último
            // y avanzará a ep 14 automáticamente (que no existe).
            val historyEntry = WatchHistoryEntity(
                animeSlug = slug,
                title = title,
                coverUrl = coverUrl,
                episodeNumber = totalEpisodes,  // último episodio visto = 13
                progressMs = 0L,
                durationMs = 0L,
                totalEpisodes = totalEpisodes,  // mantener lo que jkanime reportó = 13
                seasonIndex = computeSeasonIndex(anilistId),
                anilistId = anilistId,
                seasonTitle = title,
                timestamp = System.currentTimeMillis(),
                isAiring = true,
                nextEpisodeTimestamp = actualNextTs.takeIf { it > 0L }
            )

            airingController.markAsWaiting(historyEntry, actualNextTs.takeIf { it > 0L })
            syncManager.pushAsync()
            return
        }

        // CASO B: Anime finalizado → marcar como Visto y avanzar a siguiente temporada
        Log.i("PlayerVM", "Anime finalizado — marcando como Visto")
        dao.deleteWatchHistory(slug)

        if (anilistId > 0) {
            dao.insertAnime(LocalAnimeEntity(anilistId, title, coverUrl, "TV"))
            dao.insertAnimeIntoList(AnimeListCrossRef(2, anilistId))
            dao.removeAnimeFromListById(3, anilistId)
        }

        val next = _nextEpisodeInfo.value
        if (next != null) {
            dao.insertWatchHistory(
                WatchHistoryEntity(
                    animeSlug = next.slug,
                    title = next.title,
                    coverUrl = next.coverUrl,
                    episodeNumber = next.episode,
                    progressMs = 0L,
                    durationMs = 0L,
                    totalEpisodes = next.totalEpisodes,
                    seasonIndex = computeSeasonIndex(next.anilistId),
                    anilistId = next.anilistId,
                    seasonTitle = next.title,
                    timestamp = System.currentTimeMillis(),
                    isAiring = false,
                    nextEpisodeTimestamp = null
                )
            )
            dao.insertAnime(LocalAnimeEntity(next.anilistId, next.title, next.coverUrl, "TV"))
            dao.insertAnimeIntoList(AnimeListCrossRef(3, next.anilistId))
        }

        syncManager.pushAsync()
    }

    private suspend fun computeSeasonIndex(anilistId: Int): Int {
        if (anilistId == 0) return 0
        val chain = seasonChainResolver.getCached(anilistId) ?: return 0
        val idx = chain.indexOf(anilistId)
        return if (idx >= 0) chain.seasons[idx].seasonNumber else 0
    }

    private suspend fun saveToHistory(slug: String, title: String, coverUrl: String,
                                       episode: Int, progressMs: Long, durationMs: Long,
                                       totalEpisodes: Int, anilistId: Int, isAiring: Boolean,
                                       nextEpisodeTimestamp: Long) {
        var seasonIndex = 0
        var seasonTitle = ""
        if (anilistId > 0) {
            val chain = seasonChainResolver.getCached(anilistId)
            if (chain != null) {
                val idx = chain.indexOf(anilistId)
                if (idx >= 0) {
                    seasonIndex = chain.seasons[idx].seasonNumber
                    seasonTitle = chain.seasons[idx].title
                }
            }
        }

        if (anilistId > 0) {
            dao.insertAnime(LocalAnimeEntity(anilistId, title, coverUrl, "TV"))
            dao.insertAnimeIntoList(AnimeListCrossRef(3, anilistId))
        }

        val tsToSave = if (isAiring && nextEpisodeTimestamp > 0) nextEpisodeTimestamp else null

        dao.insertWatchHistory(
            com.mew.animemew.data.local.WatchHistoryEntity(
                animeSlug = slug, title = title, coverUrl = coverUrl,
                episodeNumber = episode, totalEpisodes = totalEpisodes,
                progressMs = progressMs, durationMs = durationMs,
                seasonIndex = seasonIndex, anilistId = anilistId, seasonTitle = seasonTitle,
                timestamp = System.currentTimeMillis(),
                isAiring = isAiring,
                nextEpisodeTimestamp = tsToSave
            )
        )
    }
}
