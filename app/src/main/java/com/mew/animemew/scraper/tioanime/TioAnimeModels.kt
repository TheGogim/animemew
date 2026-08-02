package com.mew.animemew.scraper.tioanime

import com.mew.animemew.scraper.ServerInfo

// =========================================================
//  TioAnime — data classes.
// =========================================================

data class TioAnimeSearchResult(
    val slug: String,
    val title: String,
    val coverUrl: String? = null,
    val type: String? = null,
    val status: String? = null
)

data class TioAnimeEpisodePage(
    val slug: String,
    val episode: Int,
    val servers: List<ServerInfo>
)
