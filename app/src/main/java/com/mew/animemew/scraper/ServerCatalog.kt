package com.mew.animemew.scraper

object ServerCatalog {

    /** Configuración conocida de cada CDN. */
    private val knownServers = mapOf(
        "Desu" to Config(
            requiresWebView = false,
            referer = null,
            isHls = true,
            priority = 0
        ),
        "Magi" to Config(
            requiresWebView = false,
            referer = null,
            isHls = true,
            priority = 1
        ),
        "Streamwish" to Config(
            requiresWebView = true,
            referer = "https://jkanime.net/",
            isHls = true,
            priority = 2
        ),
        "Mp4upload" to Config(
            requiresWebView = false,
            referer = "https://www.mp4upload.com/",
            isHls = false,
            priority = 3
        ),
        "Vidhide" to Config(
            requiresWebView = true,
            referer = null,
            isHls = true,
            priority = 4
        ),
        "Filemoon" to Config(
            requiresWebView = true,
            referer = null,
            isHls = true,
            priority = 5
        ),
        "VOE" to Config(
            requiresWebView = true,
            referer = "https://voe.sx/",
            isHls = true,
            priority = 6
        ),
        "Mixdrop" to Config(
            requiresWebView = true,
            referer = "https://mixdrop.top/",
            isHls = true,
            priority = 7
        ),
        "Doodstream" to Config(
            requiresWebView = true,
            referer = "https://dsvplay.com/",
            isHls = true,
            priority = 8
        ),
        "Streamtape" to Config(
            requiresWebView = true,
            referer = "https://streamtape.com/",
            isHls = false,
            priority = 9
        ),
        "Mediafire" to Config(
            requiresWebView = false,
            referer = null,
            isHls = false,
            priority = 99,
            isFileHost = true
        ),
        "Mega" to Config(
            requiresWebView = false,
            referer = null,
            isHls = false,
            priority = 99,
            isFileHost = true
        ),
    )

    data class Config(
        val requiresWebView: Boolean,
        val referer: String?,
        val isHls: Boolean,
        val priority: Int,
        val isFileHost: Boolean = false,
    )

    fun configFor(serverName: String): Config =
        knownServers[serverName] ?: Config(
            requiresWebView = true,  // desconocido → intentamos WebView como fallback
            referer = "https://jkanime.net/",
            isHls = true,
            priority = 50
        )

    fun isFileHost(serverName: String): Boolean =
        knownServers[serverName]?.isFileHost == true
}
