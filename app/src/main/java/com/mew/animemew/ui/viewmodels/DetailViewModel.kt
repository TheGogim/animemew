package com.mew.animemew.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.AniListUnavailableException
import com.mew.animemew.data.AnimeDetails
import com.mew.animemew.data.AnimeRelation
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.AnimeListCrossRef
import com.mew.animemew.data.local.LocalAnimeEntity
import com.mew.animemew.data.season.SeasonChain
import com.mew.animemew.data.season.SeasonChainResolver
import com.mew.animemew.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AnimeDatabase.getDatabase(application).animeDao()
    private val syncManager = SyncManager.getInstance(application)
    private val seasonChainResolver = SeasonChainResolver.getInstance(application)
    // NUEVO: usar AnimeRepository para que detecte cuando AniList está caído
    private val repository = com.mew.animemew.data.AnimeRepository()

    private val _animeDetails = MutableStateFlow<AnimeDetails?>(null)
    val animeDetails: StateFlow<AnimeDetails?> = _animeDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // NUEVO: flag para saber si AniList está caído
    private val _isAniListDown = MutableStateFlow(false)
    val isAniListDown: StateFlow<Boolean> = _isAniListDown.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isWatched = MutableStateFlow(false)
    val isWatched: StateFlow<Boolean> = _isWatched.asStateFlow()

    private val _jkanimePage = MutableStateFlow<com.mew.animemew.scraper.AnimePage?>(null)
    val jkanimePage: StateFlow<com.mew.animemew.scraper.AnimePage?> = _jkanimePage.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _scrapingLog = MutableStateFlow<String>("")
    val scrapingLog: StateFlow<String> = _scrapingLog.asStateFlow()

    private val _seasonChain = MutableStateFlow<SeasonChain?>(null)
    val seasonChain: StateFlow<SeasonChain?> = _seasonChain.asStateFlow()

    private var currentAnilistId: Int = 0

    fun loadAnimeDetails(id: Int) {
        currentAnilistId = id
        Log.i("DetailVM", "=== loadAnimeDetails llamado con id=$id ===")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _seasonChain.value = null
            try {
                // FIX: usar AnimeRepository para que detecte cuando AniList está caído
                // (antes se hacía directo con AniListClient y no se detectaba el error)
                val media = repository.getAnimeDetails(id)

                if (media != null) {
                    val rawDescription = media.description ?: "Sin descripción disponible."
                    val cleanDescription = HtmlCompat.fromHtml(
                        rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY
                    ).toString()

                    val relationsList = media.relations?.edges?.filterNotNull()?.mapNotNull { edge ->
                        val node = edge.node ?: return@mapNotNull null
                        AnimeRelation(
                            id = node.id.toString(),
                            relationType = edge.relationType?.name ?: "Relacionado",
                            title = node.title?.romaji ?: node.title?.english ?: "Unknown",
                            coverUrl = node.coverImage?.large ?: "",
                            type = node.type?.name ?: "Unknown"
                        )
                    } ?: emptyList()

                    val details = AnimeDetails(
                        id = media.id.toString(),
                        title = media.title?.romaji ?: media.title?.english ?: "Unknown",
                        coverUrl = media.coverImage?.large ?: media.coverImage?.extraLarge ?: "",
                        bannerUrl = media.bannerImage ?: media.coverImage?.extraLarge ?: "",
                        score = (media.averageScore ?: 0) / 10.0,
                        type = media.format?.name ?: "Unknown",
                        status = media.status?.name ?: "Unknown",
                        releaseYear = media.seasonYear ?: 0,
                        seasons = 1,
                        ranking = 0,
                        genres = media.genres?.filterNotNull() ?: emptyList(),
                        description = cleanDescription,
                        trailerYoutubeId = if (media.trailer?.site == "youtube") media.trailer.id else null,
                        relations = relationsList,
                        nextEpisodeNumber = media.nextAiringEpisode?.episode,
                        nextEpisodeTimestamp = media.nextAiringEpisode?.airingAt?.toLong()
                    )
                    _animeDetails.value = details

                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        _isScraping.value = true
                        val debugLog = StringBuilder()

                        // FIX: No buscar en jkanime si el anime aún no ha salido
                        val animeStatus = media.status?.name
                        if (animeStatus == "NOT_YET_RELEASED") {
                            debugLog.append("Este anime aún no se ha estrenado (status=NOT_YET_RELEASED).\n")
                            debugLog.append("No hay episodios disponibles todavía.\n")
                            _jkanimePage.value = null
                            _isScraping.value = false
                            _scrapingLog.value = debugLog.toString()
                        } else {
                            debugLog.append("Iniciando búsqueda para: ${media.title?.romaji} (status=$animeStatus)\n")
                            try {
                                val jkanimeData = com.mew.animemew.scraper.ScraperRepository.getAnimePage(
                                    anilistId = id,
                                    titleRomaji = media.title?.romaji,
                                    titleEnglish = media.title?.english,
                                    titleNative = media.title?.native,
                                    debugLogger = { msg ->
                                        debugLog.append("$msg\n")
                                        Log.i("DetailVM-Scraper", msg)
                                    }
                                )
                                if (jkanimeData != null) {
                                    debugLog.append("¡Éxito! Se encontraron ${jkanimeData.totalEpisodes} episodios en ${jkanimeData.slug}\n")
                                    Log.i("DetailVM", "✅ Encontrado: ${jkanimeData.slug} con ${jkanimeData.totalEpisodes} eps")
                                } else {
                                    debugLog.append("No se encontró ningún anime que coincida en Jkanime.\n")
                                    Log.w("DetailVM", "❌ No encontrado en jkanime")
                                }
                                _jkanimePage.value = jkanimeData
                            } catch (e: Exception) {
                                debugLog.append("Error crítico: ${e.message}\n")
                                Log.e("DetailVM", "Error scraping: ${e.message}")
                            } finally {
                                _isScraping.value = false
                                _scrapingLog.value = debugLog.toString()
                            }
                        }
                    }

                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            Log.i("DetailVM", "Iniciando resolución de cadena para id=$id")
                            val chain = seasonChainResolver.resolve(id)
                            _seasonChain.value = chain
                            Log.i("DetailVM", "✅ Cadena resuelta: ${chain.totalSeasons} entradas")
                        } catch (e: Exception) {
                            Log.e("DetailVM", "❌ Error resolviendo cadena", e)
                        }
                    }

                    launch {
                        dao.isAnimeInList(1, id).collect { _isFavorite.value = it }
                    }
                    launch {
                        dao.isAnimeInList(2, id).collect { _isWatched.value = it }
                    }
                } else {
                    _error.value = "No se encontraron detalles."
                }
            } catch (e: AniListUnavailableException) {
                _isAniListDown.value = true
                _error.value = "AniList no disponible"
                Log.e("DetailVM", "❌ AniList caído: ${e.message}")
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    // NUEVO: Reintentar cuando AniList estaba caído
    fun retry() {
        _isAniListDown.value = false
        _error.value = null
        if (currentAnilistId != 0) {
            loadAnimeDetails(currentAnilistId)
        }
    }

    fun getCurrentSeasonIndex(): Int {
        val chain = _seasonChain.value ?: return 0
        return chain.indexOf(currentAnilistId).takeIf { it >= 0 } ?: 0
    }

    suspend fun getCachedChain(): SeasonChain? {
        return seasonChainResolver.getCached(currentAnilistId)
    }

    fun toggleFavorite() {
        val anime = _animeDetails.value ?: return
        viewModelScope.launch {
            val localAnime = LocalAnimeEntity(
                id = anime.id.toIntOrNull() ?: return@launch,
                title = anime.title,
                coverUrl = anime.coverUrl,
                format = anime.type
            )
            if (_isFavorite.value) {
                dao.removeAnimeFromListById(1, localAnime.id)
            } else {
                dao.insertAnime(localAnime)
                dao.insertAnimeIntoList(AnimeListCrossRef(1, localAnime.id))
            }
            syncManager.pushAsync()
        }
    }

    fun toggleWatched() {
        val anime = _animeDetails.value ?: return
        viewModelScope.launch {
            val localAnime = LocalAnimeEntity(
                id = anime.id.toIntOrNull() ?: return@launch,
                title = anime.title,
                coverUrl = anime.coverUrl,
                format = anime.type
            )
            if (_isWatched.value) {
                // Quitar de Vistos
                dao.removeAnimeFromListById(2, localAnime.id)
            } else {
                // Añadir a Vistos
                dao.insertAnime(localAnime)
                dao.insertAnimeIntoList(AnimeListCrossRef(2, localAnime.id))
                // NUEVO: quitar de Viendo (lista 3)
                dao.removeAnimeFromListById(3, localAnime.id)
                // NUEVO: quitar de "Seguir Viendo" (historial)
                dao.deleteWatchHistoryByAnilistId(localAnime.id)
            }
            syncManager.pushAsync()
        }
    }

    val allLists = dao.getAllLists()

    fun addToList(listId: Int) {
        val anime = _animeDetails.value ?: return
        viewModelScope.launch {
            val localAnime = LocalAnimeEntity(
                id = anime.id.toIntOrNull() ?: return@launch,
                title = anime.title,
                coverUrl = anime.coverUrl,
                format = anime.type
            )
            dao.insertAnime(localAnime)
            dao.insertAnimeIntoList(AnimeListCrossRef(listId, localAnime.id))
            syncManager.pushAsync()
        }
    }
}
