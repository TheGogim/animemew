package com.mew.animemew.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "anime_list")
data class AnimeListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "local_anime")
data class LocalAnimeEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val coverUrl: String,
    val format: String
)

@Entity(
    tableName = "anime_list_cross_ref",
    primaryKeys = ["listId", "animeId"],
    indices = [
        Index(value = ["listId"]),
        Index(value = ["animeId"])
    ]
)
data class AnimeListCrossRef(
    val listId: Int,
    val animeId: Int
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val animeSlug: String,
    val title: String,
    val coverUrl: String,
    val episodeNumber: Int,
    val progressMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val totalEpisodes: Int = 0,
    val seasonIndex: Int = 0,
    val anilistId: Int = 0,
    val seasonTitle: String = "",
    val isAiring: Boolean = false,
    // NUEVO v10: timestamp del próximo episodio (de AniList)
    // sirve para actualizar "En espera" automáticamente
    val nextEpisodeTimestamp: Long? = null,
    // NUEVO v11: cuándo empezamos a esperar este episodio.
    // Sirve para saber cuánto tiempo llevamos esperando y
    // eventualmente hacer algo crítico si pasan muchos días.
    val waitingSinceTimestamp: Long? = null,
    // ===== NUEVO Fase 2 (v13): AiringController rediseñado =====
    // El episodio MÁS ALTO disponible en jkanime/tioanime (calculado por
    // AiringController). NO es lo que el usuario está viendo, es lo que
    // ya se publicó en scrapers. Si es 0 → no sabemos / no hay info.
    val nextAvailableEpisode: Int = 0,
    // true cuando nextAvailableEpisode > episodeNumber (hay al menos 1 ep
    // nuevo disponible que el usuario no ha visto). Lo usa HomeScreen para
    // mostrar un badge dorado "Nuevos episodios disponibles".
    val hasNewEpisode: Boolean = false,
    // ===== NUEVO Fase 3 (v14): La "Bolsa" de emisión =====
    // Número del próximo episodio según AniList (ej: si va en ep 9 y el
    // próximo es el 10, anilistNextEpNum = 10). 0 = no hay info / no está en emisión.
    val anilistNextEpNum: Int = 0,
    // Cuándo se emite el próximo episodio (Unix timestamp en SEGUNDOS, UTC).
    // Lo da AniList en nextAiringEpisode.airingAt. 0 = no hay info.
    val anilistNextEpAirAt: Long = 0,
    // ===== NUEVO Fase 4 (v15): epEsperado =====
    // El episodio que el usuario está esperando ver cuando entre en "En espera".
    // - Cuando markAsWaiting() se llama → epEsperado = episodeNumber + 1
    // - Cuando el AiringController detecta ese episodio disponible →
    //   episodeNumber = epEsperado, progressMs = 0, epEsperado = 0
    // - Mientras epEsperado != 0, el AiringController SOLO verifica ese episodio
    //   específicamente (no episodeNumber+1). Esto permite que si salieron
    //   varios eps nuevos (12, 13, 14), el usuario siga esperando el 12
    //   y no salte al 14 sin ver el 12 y 13.
    val epEsperado: Int = 0
)
