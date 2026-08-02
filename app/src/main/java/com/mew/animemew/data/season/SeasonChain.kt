package com.mew.animemew.data.season

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// =========================================================
//  Data classes para la cadena de temporadas.
//
//  isMainSeason = true  → TV / TV_SHORT → lleva número T1, T2...
//  isMainSeason = false → OVA / MOVIE / SPECIAL / ONA / MUSIC
//                        → se etiqueta por formato, se salta si
//                          no se encuentra en jkanime
// =========================================================

data class SeasonChain(
    @SerializedName("seasons")
    val seasons: List<SeasonInfo> = emptyList()
) {
    /** Índice en la lista del anilistId dado, o -1 si no está. */
    fun indexOf(anilistId: Int): Int = seasons.indexOfFirst { it.anilistId == anilistId }

    val totalSeasons: Int get() = seasons.size

    fun getOrNull(index: Int): SeasonInfo? = seasons.getOrNull(index)
}

data class SeasonInfo(
    @SerializedName("anilist_id")
    val anilistId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("cover_url")
    val coverUrl: String,
    @SerializedName("total_episodes")
    val totalEpisodes: Int,        // 0 si AniList no lo sabe (emisión)
    @SerializedName("format")
    val format: String,            // "TV", "OVA", "MOVIE", "SPECIAL", "ONA", "TV_SHORT", "MUSIC"
    @SerializedName("is_main_season")
    val isMainSeason: Boolean,     // true = TV/TV_SHORT → lleva T1, T2...
    @SerializedName("season_number")
    val seasonNumber: Int,         // 1, 2, 3... si isMainSeason, sino 0
    @SerializedName("status")
    val status: String = "FINISHED",  // NUEVO: "FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS"
    @SerializedName("slug")
    val slug: String? = null,
    @SerializedName("slug_resolved")
    val slugResolved: Boolean = false
) {
    /**
     * Etiqueta lista para mostrar en el player y en continuar viendo.
     * - TV:       "T2 E5"
     * - OVA:      "OVA 1"
     * - MOVIE:    "Película"
     * - SPECIAL:  "Especial 1"
     * - ONA:      "ONA 1"
     * - otro:     "E5"
     */
    fun episodeLabel(episode: Int): String {
        return when {
            isMainSeason -> "T$seasonNumber E$episode"
            format == "OVA" -> "OVA $episode"
            format == "MOVIE" -> "Película $episode"
            format == "SPECIAL" -> "Especial $episode"
            format == "ONA" -> "ONA $episode"
            format == "MUSIC" -> "MV $episode"
            else -> "E$episode"
        }
    }
}
