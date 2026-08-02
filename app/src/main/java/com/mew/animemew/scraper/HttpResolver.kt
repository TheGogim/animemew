package com.mew.animemew.scraper

import android.util.Base64

object HttpResolver {

    /** Resuelve Desu y Magi: piden el iframe y extraen el m3u8 del <script>. */
    fun resolveIframe(iframeUrl: String): String? {
        val html = HttpClient.get(iframeUrl, referer = "https://jkanime.net/")

        // 1. Buscar m3u8 en texto plano
        Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""")
            .find(html)?.let { return it.value }

        // 2. Buscar dentro de atob('base64...')
        val b64Pattern = Regex("""atob\(['"]([A-Za-z0-9+/=]+)['"]\)""")
        for (match in b64Pattern.findAll(html)) {
            try {
                val decoded = String(
                    Base64.decode(match.groupValues[1], Base64.DEFAULT),
                    Charsets.UTF_8
                )
                Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""")
                    .find(decoded)?.let { return it.value }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Resuelve Mp4upload desde JKanime: el HTML del embed tiene "src: 'https://.../video.mp4'". */
    fun resolveMp4upload(embedUrl: String): String? {
        val html = HttpClient.get(embedUrl, referer = "https://jkanime.net/")
        // Patrón:  src: "https://a3.mp4upload.com:183/d/.../video.mp4"
        Regex("""src:\s*["'](https?://[^"']+/(?:video\.mp4|[^"']+\.mp4)[^"']*)["']""")
            .find(html)?.let { return it.groupValues[1] }
        return null
    }

    /**
     * NUEVO v9.0: Resuelve Mp4upload desde Latanime.
     *
     * Latanime usa el mismo servidor mp4upload pero con referer diferente.
     * La regex es la misma que JKanime pero el referer debe ser latanime.org.
     *
     * @param embedUrl URL del embed de mp4upload (ej: https://www.mp4upload.com/embed-xxx.html)
     * @return URL directa del .mp4 o null
     */
    fun resolveMp4uploadLatAnime(embedUrl: String): String? {
        val html = HttpClient.get(embedUrl, referer = "https://latanime.org/")
        // Patrón:  src: "https://a3.mp4upload.com:183/d/.../video.mp4"
        // FIX: permitir espacios antes del cierre de comillas
        Regex("""src:\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
            .find(html)?.let { return it.groupValues[1].trim() }
        return null
    }
}
