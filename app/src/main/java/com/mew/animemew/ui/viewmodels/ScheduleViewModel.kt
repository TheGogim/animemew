package com.mew.animemew.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.AnimeRepository
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.WatchHistoryEntity
import com.mew.animemew.graphql.type.MediaSort
import com.mew.animemew.graphql.type.MediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// =========================================================
//  ScheduleViewModel — lógica de la pestaña Horarios.
//
//  2 tabs:
//    - "Siguiendo": lee animes con isAiring=true de watch_history,
//      consulta AniList para cada uno (en paralelo), arma el schedule.
//    - "Esta temporada": trae los 50 animes más populares en emisión
//      (RELEASING) con sus nextAiringEpisode.
//
//  Recarga al abrir la pestaña (loadSchedule() desde init).
//
//  Zona horaria: America/Bogota (UTC-5, sin DST).
//  AniList da airingAt como Unix timestamp en UTC segundos.
//  Convertimos a ZonedDateTime en Bogotá para extraer día y hora.
// =========================================================

data class ScheduleItem(
    val anilistId: Int,
    val title: String,
    val coverUrl: String,
    val nextEpisode: Int,
    val airingAt: Long,                 // Unix timestamp UTC (segundos)
    val dayOfWeek: DayOfWeek,           // en hora Colombia
    val timeString: String,             // "9:30 AM" en hora Colombia
    val dateString: String,             // "15 sept" para mostrar contexto
    val isFollowing: Boolean            // true si está en watch_history
)

sealed class ScheduleState {
    object Loading : ScheduleState()
    data class Loaded(val items: List<ScheduleItem>) : ScheduleState()
    data class Error(val message: String) : ScheduleState()
    object Empty : ScheduleState()
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ScheduleVM"
    private val dao = AnimeDatabase.getDatabase(application).animeDao()
    private val repository = AnimeRepository()

    private val bogotaZone = ZoneId.of("America/Bogota")

    private val _state = MutableStateFlow<ScheduleState>(ScheduleState.Loading)
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    private val _activeTab = MutableStateFlow(0)  // 0 = siguiendo, 1 = temporada
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    init {
        loadSchedule()
    }

    fun setTab(index: Int) {
        if (_activeTab.value == index) return
        _activeTab.value = index
        loadSchedule()
    }

    fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = ScheduleState.Loading
            try {
                val items = when (_activeTab.value) {
                    0 -> loadFollowingSchedule()
                    else -> loadSeasonSchedule()
                }

                if (items.isEmpty()) {
                    _state.value = ScheduleState.Empty
                } else {
                    _state.value = ScheduleState.Loaded(items)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando schedule", e)
                _state.value = ScheduleState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Carga el schedule de los animes que el usuario está siguiendo.
     * Lee watch_history donde isAiring=true y consulta AniList en paralelo
     * para cada uno.
     */
    private suspend fun loadFollowingSchedule(): List<ScheduleItem> {
        val airingAnimes = dao.getAiringWatchHistory()
        Log.i(TAG, "loadFollowingSchedule: ${airingAnimes.size} animes en emisión")

        if (airingAnimes.isEmpty()) return emptyList()

        val followingIds = airingAnimes.map { it.anilistId }.toSet()

        // Consultar AniList en paralelo (máximo 5 a la vez para no saturar)
        val items = coroutineScope {
            airingAnimes.map { history ->
                async(Dispatchers.IO) {
                    fetchScheduleForFollowing(history, followingIds)
                }
            }.awaitAll()
        }

        return items.filterNotNull().sortedBy { it.airingAt }
    }

    private suspend fun fetchScheduleForFollowing(
        history: WatchHistoryEntity,
        followingIds: Set<Int>
    ): ScheduleItem? {
        if (history.anilistId <= 0) return null
        return try {
            val media = repository.getAnimeDetails(history.anilistId) ?: return null
            val airingAt = media.nextAiringEpisode?.airingAt?.toLong() ?: return null
            if (airingAt <= 0) return null
            val nextEp = media.nextAiringEpisode?.episode ?: return null

            val bogotaTime = Instant.ofEpochSecond(airingAt).atZone(bogotaZone)
            ScheduleItem(
                anilistId = history.anilistId,
                title = history.title,
                coverUrl = history.coverUrl,
                nextEpisode = nextEp,
                airingAt = airingAt,
                dayOfWeek = bogotaTime.dayOfWeek,
                timeString = formatTime(bogotaTime),
                dateString = formatDate(bogotaTime),
                isFollowing = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo schedule para ${history.title}: ${e.message}")
            null
        }
    }

    /**
     * Carga el schedule de los animes más populares en emisión esta temporada.
     * Trae 3 páginas de 20 animes (hasta 60) con sort=FAVOURITES_DESC,
     * status=RELEASING. Solo quedan los que tienen nextAiringEpisode.
     */
    private suspend fun loadSeasonSchedule(): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()
        val followingIds = dao.getAiringWatchHistory()
            .map { it.anilistId }
            .toSet()

        var page = 1
        var hasMore = true
        val maxPages = 3  // hasta 60 animes

        while (hasMore && page <= maxPages) {
            val result = try {
                repository.searchAnime(
                    page = page,
                    perPage = 20,
                    searchQuery = null,
                    genres = null,
                    sort = listOf(MediaSort.FAVOURITES_DESC),
                    status = listOf(MediaStatus.RELEASING)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error trayendo página $page: ${e.message}")
                break
            }

            val mediaList = result?.media?.filterNotNull() ?: break

            mediaList.forEach { media ->
                val airingAt = media.nextAiringEpisode?.airingAt?.toLong() ?: return@forEach
                if (airingAt <= 0) return@forEach
                val nextEp = media.nextAiringEpisode?.episode ?: return@forEach

                val bogotaTime = Instant.ofEpochSecond(airingAt).atZone(bogotaZone)
                items.add(ScheduleItem(
                    anilistId = media.id,
                    title = media.title?.romaji ?: media.title?.english ?: "Unknown",
                    coverUrl = media.coverImage?.large ?: media.coverImage?.extraLarge ?: "",
                    nextEpisode = nextEp,
                    airingAt = airingAt,
                    dayOfWeek = bogotaTime.dayOfWeek,
                    timeString = formatTime(bogotaTime),
                    dateString = formatDate(bogotaTime),
                    isFollowing = media.id in followingIds
                ))
            }

            hasMore = result.pageInfo?.hasNextPage ?: false
            page++
        }

        Log.i(TAG, "loadSeasonSchedule: ${items.size} animes con schedule encontrados")
        return items.sortedBy { it.airingAt }
    }

    /**
     * Formatea la hora en formato 12h: "9:30 AM".
     */
    private fun formatTime(zdt: ZonedDateTime): String {
        val hour = zdt.hour
        val minute = zdt.minute
        val ampm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "%d:%02d %s".format(hour12, minute, ampm)
    }

    /**
     * Formatea la fecha corta: "15 sept".
     */
    private fun formatDate(zdt: ZonedDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "CO"))
        return zdt.format(formatter)
    }

    /**
     * Devuelve el nombre del día de la semana en español.
     */
    fun dayOfWeekName(day: DayOfWeek): String {
        return day.getDisplayName(TextStyle.FULL, Locale("es", "CO")).uppercase()
    }
}
