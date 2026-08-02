package com.mew.animemew.data

import android.content.Context
import com.google.gson.Gson
import com.mew.animemew.graphql.type.MediaSort
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// =========================================================
//  HomeRepository — gestiona la configuración remota del Home.
//
//  1. Intenta descargar desde el server.
//  2. Si falla, usa el último caché.
//  3. Si no hay caché, usa configuración por defecto.
// =========================================================

class HomeRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("home_config_cache", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // ⚠️ CAMBIA ESTO por tu dominio real
    private val CONFIG_URL = "https://animemew-api.duckdns.org/api/home/config"

    /**
     * Obtiene la configuración del Home.
     * Prioridad: Server → Caché → Default
     */
    suspend fun getHomeConfig(): HomeConfig {
        // 1. Intentar descargar del server
        try {
            val json = downloadConfig()
            if (json != null) {
                val config = gson.fromJson(json, HomeConfig::class.java)
                if (config != null && config.sections.isNotEmpty()) {
                    // Guardar en caché
                    prefs.edit().putString("config_json", json).apply()
                    return config
                }
            }
        } catch (e: Exception) {
            // Ignorar, pasar al fallback
        }

        // 2. Usar caché
        val cachedJson = prefs.getString("config_json", null)
        if (cachedJson != null) {
            try {
                val cachedConfig = gson.fromJson(cachedJson, HomeConfig::class.java)
                if (cachedConfig != null && cachedConfig.sections.isNotEmpty()) {
                    return cachedConfig
                }
            } catch (e: Exception) {}
        }

        // 3. Configuración por defecto
        return getDefaultConfig()
    }

    private fun downloadConfig(): String? {
        val request = Request.Builder()
            .url(CONFIG_URL)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.string()
            }
        }
        return null
    }

    private fun getDefaultConfig(): HomeConfig {
        return HomeConfig(
            sections = listOf(
                HomeSectionConfig("popular", "Más Populares", "popular"),
                HomeSectionConfig("trending", "Ranking Semanal (Tendencias)", "trending"),
                HomeSectionConfig("action", "Tendencias en Acción", "genre", "Action", "FAVOURITES_DESC"),
                HomeSectionConfig("fantasy", "Tendencias en Fantasía", "genre", "Fantasy", "SCORE_DESC")
            )
        )
    }
}
