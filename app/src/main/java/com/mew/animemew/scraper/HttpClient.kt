package com.mew.animemew.scraper

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object HttpClient {

    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** GET simple. Devuelve body como String. */
    fun get(url: String, referer: String? = null): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_MOBILE)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .apply { referer?.let { header("Referer", it) } }
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                throw ScraperException("HTTP ${res.code} en $url")
            }
            return res.body?.string()
                ?: throw ScraperException("Body vacío en $url")
        }
    }
}

class ScraperException(message: String) : Exception(message)
