package com.mew.animemew.scraper

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mew.animemew.AnimeApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class WebViewResolver {

    /**
     * Resuelve la URL del stream con DOS estrategias en paralelo:
     *
     * 1. Intercepta peticiones de red (para .m3u8/.mp4 directos)
     * 2. Después de 6s, inyecta JS para leer <video> y <source> tags
     *    (para servicios que cargan el video con JS ofuscado)
     *
     * Devuelve lo primero que encuentre.
     */
    suspend fun resolveStreamUrl(
        embedUrl: String,
        timeoutMs: Long = 20_000
    ): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            var webViewRef: WebView? = null
            val resolved = AtomicBoolean(false)

            Handler(Looper.getMainLooper()).post {
                if (cont.isCompleted) return@post

                val webView = createInvisibleWebView()
                webViewRef = webView

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        if (isRealVideoUrl(url, embedUrl)) {
                            Log.i("WebViewResolver", "🎬 Red: $url")
                            if (resolved.compareAndSet(false, true)) {
                                Log.i("WebViewResolver", "✅ Por red: $url")
                                if (cont.isActive) cont.resume(url)
                                Handler(Looper.getMainLooper()).post { destroyQuietly(webView) }
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.i("WebViewResolver", "📄 Página cargada, inyectando JS...")
                        // NUEVO: inyectar JS para leer <video> tags después de que cargue
                        view?.evaluateJavascript(JS_VIDEO_EXTRACTOR, null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true &&
                            !cont.isCompleted &&
                            resolved.compareAndSet(false, true)) {
                            Log.w("WebViewResolver", "❌ Error: ${error?.description}")
                            cont.resume(null)
                        }
                    }
                }

                configureWebView(webView)
                Log.i("WebViewResolver", "Cargando embed: $embedUrl")
                webView.loadUrl(embedUrl)
            }

            // NUEVO: después de 8s, intentar leer <video> tags directamente
            // (algunos servicios como streamtape cargan el video con JS ofuscado
            // y la URL no aparece en las peticiones de red interceptables)
            Thread {
                Thread.sleep(8000)
                if (!resolved.get() && !cont.isCompleted) {
                    Log.i("WebViewResolver", "⏰ 8s sin resultado, intentando JS extraction...")
                    Handler(Looper.getMainLooper()).post {
                        webViewRef?.let { wv ->
                            wv.evaluateJavascript(JS_VIDEO_EXTRACTOR) { result ->
                                val url = extractUrlFromJsResult(result)
                                if (url != null && isRealVideoUrl(url, embedUrl) &&
                                    resolved.compareAndSet(false, true)) {
                                    Log.i("WebViewResolver", "✅ Por JS: $url")
                                    if (cont.isActive) cont.resume(url)
                                    Handler(Looper.getMainLooper()).post { destroyQuietly(wv) }
                                }
                            }
                        }
                    }
                }
            }.start()

            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    webViewRef?.let { destroyQuietly(it) }
                }
            }
        }
    }

    /**
     * JS que se inyecta en la página para extraer URLs de video.
     * Busca en:
     * 1. <video src="..."> directo
     * 2. <video><source src="...">
     * 3. jwplayer().getPlaylistItem().file
     * 4. videojs players
     */
    private val JS_VIDEO_EXTRACTOR = """
        (function() {
            try {
                // 1. Buscar <video> tags
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    if (v.src && v.src.startsWith('http')) return v.src;
                    var sources = v.querySelectorAll('source');
                    for (var j = 0; j < sources.length; j++) {
                        if (sources[j].src && sources[j].src.startsWith('http')) return sources[j].src;
                    }
                }

                // 2. Buscar en jwplayer
                if (typeof jwplayer !== 'undefined') {
                    try {
                        var p = jwplayer();
                        if (p && p.getPlaylistItem) {
                            var item = p.getPlaylistItem();
                            if (item && item.file) return item.file;
                        }
                    } catch(e) {}
                }

                // 3. Buscar en variables globales comunes
                var globals = ['videoUrl', 'video_url', 'file', 'source', 'sources', 'mp4', 'm3u8', 'streamUrl'];
                for (var k = 0; k < globals.length; k++) {
                    try {
                        var val = window[globals[k]];
                        if (typeof val === 'string' && val.match(/^https?:\/\/.+\.(mp4|m3u8)/)) return val;
                        if (val && typeof val === 'object') {
                            var str = JSON.stringify(val);
                            var match = str.match(/https?:\/\/[^"'\\]+\.m3u8[^"'\\]*/);
                            if (match) return match[0];
                            match = str.match(/https?:\/\/[^"'\\]+\.mp4[^"'\\]*/);
                            if (match) return match[0];
                        }
                    } catch(e) {}
                }

                // 4. Buscar en todo el HTML del documento
                var html = document.documentElement.outerHTML;
                var m = html.match(/https?:\/\/[^"'<>\s\\]+\.m3u8[^"'<>\s\\]*/);
                if (m) return m[0];
                m = html.match(/https?:\/\/[^"'<>\s\\]+\.mp4[^"'<>\s\\]*/);
                if (m) return m[0];

                return null;
            } catch(e) {
                return null;
            }
        })();
    """.trimIndent()

    /**
     * Extrae la URL del resultado de evaluateJavascript.
     * El resultado viene entre comillas: "https://..." o null
     */
    private fun extractUrlFromJsResult(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        // Quitar comillas exteriores
        var s = result.trim()
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        // Unescape
        s = s.replace("\\/", "/").replace("\\u002F", "/")
        return if (s.startsWith("http")) s else null
    }

    private fun isRealVideoUrl(url: String, embedUrl: String): Boolean {
        val lower = url.lowercase()
        if (url == embedUrl) return false

        val nonVideoExtensions = listOf(
            ".js", ".css", ".jpg", ".jpeg", ".png", ".webp", ".gif",
            ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot",
            ".html", ".htm", ".xml", ".json", ".txt"
        )
        val urlWithoutQuery = lower.substringBefore("?")
        if (nonVideoExtensions.any { urlWithoutQuery.endsWith(it) }) return false

        if (lower.contains("get_slides") || lower.contains("thumb") ||
            lower.contains("thumbnail") || lower.contains("preview") ||
            lower.contains("adexchangerapid") || lower.contains("/ads/")) return false

        if ((lower.contains("/e/") || lower.contains("/embed/")) &&
            !lower.contains(".m3u8") && !lower.contains(".mp4")) return false

        val videoExtensions = listOf(".m3u8", ".mp4", ".mkv", ".webm")
        return videoExtensions.any { urlWithoutQuery.endsWith(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = HttpClient.UA_MOBILE
            blockNetworkImage = true
            blockNetworkLoads = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
    }

    private fun createInvisibleWebView(): WebView {
        return WebView(AnimeApp.appContext)
    }

    private fun destroyQuietly(webView: WebView) {
        try {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {}
    }
}
