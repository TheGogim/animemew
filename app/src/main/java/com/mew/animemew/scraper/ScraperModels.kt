package com.mew.animemew.scraper

sealed class ServerStatus {
    data object Available : ServerStatus()       // OkHttp directo
    data object RequiresWebView : ServerStatus() // WebView invisible
    data object FileHost : ServerStatus()        // Skip (Mediafire/Mega)
}

data class ServerInfo(
    val name: String,
    val embedUrl: String,            // URL original del embed
    val streamUrl: String? = null,   // m3u8/mp4 resuelto (null si aún no se procesa)
    val status: ServerStatus,
    val requiresWebView: Boolean,
    val referer: String?,            // Header Referer que ExoPlayer debe mandar
    val isHls: Boolean,              // true=m3u8 (HLS), false=mp4 directo
    val priority: Int,                // menor = más prioritario
    val language: String = "sub"       // NUEVO v9.0: "sub" o "lat"
)

data class EpisodeData(
    val animeSlug: String,
    val episode: Int,
    val servers: List<ServerInfo>,
    val nextEpisodeUrl: String? = null,
    val previousEpisodeUrl: String? = null
)

// Search & Catalog
data class SearchResult(
    val slug: String,
    val title: String,
    val coverUrl: String?
)

data class AnimePage(
    val slug: String,
    val title: String,
    val coverUrl: String,
    val synopsis: String,
    val status: String,          // "Concluido" o "En emisión"
    val totalEpisodes: Int,      // número del último capítulo
    val genres: List<String>,
    val airedDate: String?,      // ej: "Jueves, 03 de Octubre de 2002"
    val duration: String?,       // ej: "23 min. por episodio"
    val episodeList: List<EpisodeSummary>
)

data class EpisodeSummary(
    val number: Int,
    val thumbnailUrl: String?,   // algunos caps tienen thumbnail, otros no
    val url: String              // https://jkanime.net/{slug}/{number}/
)
