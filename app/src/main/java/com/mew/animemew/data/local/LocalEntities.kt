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
    val waitingSinceTimestamp: Long? = null
)
