package com.mew.animemew.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.Anime
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.HomeRepository
import com.mew.animemew.data.HomeSectionConfig
import com.mew.animemew.data.airing.AiringController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.mew.animemew.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.mew.animemew.graphql.type.MediaSort
import com.mew.animemew.graphql.type.MediaStatus

// =========================================================
//  Estado de una sección del Home.
//  Contiene la configuración + los animes + paginación.
// =========================================================
data class HomeSectionState(
    val config: HomeSectionConfig,
    val animes: List<Anime> = emptyList(),
    val isLoading: Boolean = true,
    val currentPage: Int = 1,
    val hasMore: Boolean = true
)

class HomeViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val repository = AnimeRepository()
    private val homeRepository = HomeRepository(application)
    private val dao = com.mew.animemew.data.local.AnimeDatabase.getDatabase(application).animeDao()

    private val syncManager = SyncManager.getInstance(application)
    // NUEVO: controlador centralizado de "En espera"
    private val airingController = AiringController.getInstance(application)

    private val _watchHistory = MutableStateFlow<List<com.mew.animemew.data.local.WatchHistoryEntity>>(emptyList())
    val watchHistory: StateFlow<List<com.mew.animemew.data.local.WatchHistoryEntity>> = _watchHistory.asStateFlow()

    // NUEVO: secciones dinámicas (viene del server)
    private val _sections = MutableStateFlow<List<HomeSectionState>>(emptyList())
    val sections: StateFlow<List<HomeSectionState>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val perPage = 15

    init {
        syncManager.pullAsync()
        loadWatchHistory()
        loadHomeConfig()
        checkAiringAnimes()
    }

    // =========================================================
    //  NUEVO: Cargar configuración remota y luego las secciones
    // =========================================================
    private fun loadHomeConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // 1. Obtener configuración (server → caché → default)
            val config = homeRepository.getHomeConfig()

            // 2. Crear estado inicial para cada sección
            val initialSections = config.sections.map { sectionConfig ->
                HomeSectionState(config = sectionConfig)
            }
            _sections.value = initialSections

            // 3. Cargar cada sección EN PARALELO
            val jobs = config.sections.map { sectionConfig ->
                async { loadSection(sectionConfig) }
            }
            jobs.awaitAll()

            _isLoading.value = false
        }
    }

    private suspend fun loadSection(sectionConfig: HomeSectionConfig) {
        try {
            val animes = when (sectionConfig.type) {
                "popular" -> {
                    val page = repository.getPopularAnime(1, perPage)
                    page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                }
                "trending" -> {
                    val page = repository.getTrendingAnime(1, perPage)
                    page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                }
                "genre" -> {
                    val sort = parseSort(sectionConfig.sort)
                    val page = repository.searchAnime(1, perPage, null, listOf(sectionConfig.genre ?: return), sort)
                    page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                }
                // NUEVO: Sección de animes en emisión
                "airing" -> {
                    val sort = parseSort(sectionConfig.sort) ?: listOf(MediaSort.TRENDING_DESC)
                    val page = repository.searchAnime(1, perPage, null, null, sort, listOf(MediaStatus.RELEASING))
                    page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                }
                else -> emptyList()
            }

            // Actualizar la sección correspondiente
            val currentSections = _sections.value.toMutableList()
            val index = currentSections.indexOfFirst { it.config.id == sectionConfig.id }
            if (index >= 0) {
                currentSections[index] = currentSections[index].copy(
                    animes = animes,
                    isLoading = false,
                    hasMore = animes.size >= perPage
                )
                _sections.value = currentSections
            }
        } catch (e: Exception) {
            Log.e("HomeVM", "Error cargando sección ${sectionConfig.title}: ${e.message}")
            // Marcar como cargada (vacía) para que no se quede en skeleton
            val currentSections = _sections.value.toMutableList()
            val index = currentSections.indexOfFirst { it.config.id == sectionConfig.id }
            if (index >= 0) {
                currentSections[index] = currentSections[index].copy(isLoading = false, hasMore = false)
                _sections.value = currentSections
            }
        }
    }

    private fun parseSort(sortStr: String?): List<MediaSort>? {
        if (sortStr.isNullOrBlank()) return null
        return try {
            listOf(MediaSort.valueOf(sortStr))
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================
    //  Load more (scroll infinito) — dinámico por sectionId
    //  FIX: No marcar toda la sección como isLoading para no
    //  borrar los animes ya cargados de la pantalla.
    // =========================================================
    fun loadMore(sectionId: String) {
        viewModelScope.launch {
            val currentSections = _sections.value.toMutableList()
            val index = currentSections.indexOfFirst { it.config.id == sectionId }
            if (index < 0) return@launch

            val section = currentSections[index]
            if (!section.hasMore) return@launch

            try {
                val nextPage = section.currentPage + 1
                val config = section.config
                val newAnimes = when (config.type) {
                    "popular" -> {
                        val page = repository.getPopularAnime(nextPage, perPage)
                        page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                    }
                    "trending" -> {
                        val page = repository.getTrendingAnime(nextPage, perPage)
                        page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                    }
                    "genre" -> {
                        val sort = parseSort(config.sort)
                        val page = repository.searchAnime(nextPage, perPage, null, listOf(config.genre ?: return@launch), sort)
                        page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                    }
                    // NUEVO: Load more para airing
                    "airing" -> {
                        val sort = parseSort(config.sort) ?: listOf(MediaSort.TRENDING_DESC)
                        val page = repository.searchAnime(nextPage, perPage, null, null, sort, listOf(MediaStatus.RELEASING))
                        page?.media?.filterNotNull()?.map { it.toAnime() } ?: emptyList()
                    }
                    else -> emptyList()
                }

                val updatedSections = _sections.value.toMutableList()
                val idx = updatedSections.indexOfFirst { it.config.id == sectionId }
                if (idx >= 0) {
                    updatedSections[idx] = updatedSections[idx].copy(
                        animes = updatedSections[idx].animes + newAnimes,
                        currentPage = nextPage,
                        hasMore = newAnimes.size >= perPage
                    )
                    _sections.value = updatedSections
                }
            } catch (e: Exception) {
                val updatedSections = _sections.value.toMutableList()
                val idx = updatedSections.indexOfFirst { it.config.id == sectionId }
                if (idx >= 0) {
                    updatedSections[idx] = updatedSections[idx].copy(hasMore = false)
                    _sections.value = updatedSections
                }
            }
        }
    }

    // =========================================================
    //  Watch History + Airing Check
    // =========================================================

    private fun loadWatchHistory() {
        viewModelScope.launch {
            dao.getWatchHistory().collect { history ->
                _watchHistory.value = history
            }
        }
    }

    fun removeHistoryEntry(slug: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWatchHistory(slug)
            // NUEVO: registrar borrado para evitar que el sync lo traiga de vuelta
            syncManager.registerHistoryDeletion(slug)
            syncManager.pushAsync()
        }
    }

    /**
     * NUEVO: Verificar animes en emisión usando AiringController.
     *
     * El AiringController se encarga de:
     * 1. Para cada anime en "En espera":
     *    - Si no se ha cumplido la fecha + 3h buffer → mantener
     *    - Si se cumplió:
     *      * Consultar AniList para verificar estado real
     *      * Si sigue en emisión → buscar episodio en jkanime + tioanime
     *        - Si está disponible → habilitar (quitar "En espera")
     *        - Si no → reintentar en 1h
     *      * Si finalizó → marcar como Visto
     *
     * Esto evita el bug donde el episodio se "habilitaba" antes de
     * estar realmente disponible en los scrapers.
     */
    private fun checkAiringAnimes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = airingController.checkAllWaiting()
                if (updated > 0) {
                    Log.i("HomeVM", "✅ $updated animes actualizados desde AiringController")
                    syncManager.pushAsync()
                }
            } catch (e: Exception) {
                Log.e("HomeVM", "Error en checkAiringAnimes: ${e.message}")
            }
        }
    }

    /**
     * NUEVO: Verificación manual de animes en espera.
     * Llamado desde DetailScreen cuando el usuario entra a un anime
     * que está en espera, para forzar la verificación inmediata.
     */
    fun refreshAiringStatus() {
        checkAiringAnimes()
    }

    // =========================================================
    //  Helpers de conversión
    // =========================================================

    private fun com.mew.animemew.graphql.GetTrendingAnimeQuery.Medium.toAnime() = Anime(
        id = id.toString(),
        title = title?.romaji ?: title?.english ?: "Unknown",
        coverUrl = coverImage?.large ?: coverImage?.extraLarge ?: "",
        score = (averageScore ?: 0) / 10.0,
        type = format?.name ?: "Unknown"
    )

    private fun com.mew.animemew.graphql.GetPopularAnimeQuery.Medium.toAnime() = Anime(
        id = id.toString(),
        title = title?.romaji ?: title?.english ?: "Unknown",
        coverUrl = coverImage?.large ?: coverImage?.extraLarge ?: "",
        score = (averageScore ?: 0) / 10.0,
        type = format?.name ?: "Unknown"
    )

    private fun com.mew.animemew.graphql.SearchAnimeQuery.Medium.toAnime() = Anime(
        id = id.toString(),
        title = title?.romaji ?: title?.english ?: "Unknown",
        coverUrl = coverImage?.large ?: "",
        score = (averageScore ?: 0) / 10.0,
        type = format?.name ?: "Unknown"
    )
}
