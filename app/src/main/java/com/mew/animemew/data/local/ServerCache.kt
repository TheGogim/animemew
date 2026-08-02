package com.mew.animemew.data.local

import android.content.Context
import android.util.Log

// =========================================================
//  ServerCache — Cachea el servidor preferido por anime.
//
//  Guarda el servidor que el usuario seleccionó manualmente
//  para un anime específico (por anilistId).
//
//  Solo aplica a servidores SUBTITULADOS.
//
//  Cuando el usuario entra a un episodio:
//  1. Si hay servidor cacheado para este anime → intentarlo PRIMERO
//  2. Si no hay cache o el cacheado falla → flujo normal
//
//  El cache persiste entre episodios de la misma temporada
//  y entre temporadas de la misma cadena.
// =========================================================

class ServerCache(context: Context) {

    private val prefs = context.getSharedPreferences("server_cache", Context.MODE_PRIVATE)
    private val TAG = "ServerCache"

    /**
     * Guarda el servidor preferido para un anime.
     *
     * @param anilistId ID de AniList del anime
     * @param serverName Nombre del servidor (ej: "Desu", "Voe", "YourUpload")
     */
    fun savePreferredServer(anilistId: Int, serverName: String) {
        if (anilistId <= 0) return
        prefs.edit().putString("server_$anilistId", serverName).apply()
        Log.i(TAG, "✅ Servidor cacheado para anilistId=$anilistId: $serverName")
    }

    /**
     * Obtiene el servidor preferido para un anime.
     * Devuelve null si no hay cache.
     */
    fun getPreferredServer(anilistId: Int): String? {
        if (anilistId <= 0) return null
        return prefs.getString("server_$anilistId", null)
    }

    /**
     * Verifica si un servidor es "rápido" (merece el rayito ⚡).
     * Estos son los servidores que cargan más fluido.
     */
    fun isFastServer(serverName: String): Boolean {
        return serverName.lowercase() in FAST_SERVERS
    }

    companion object {
        // Servidores que cargan más fluido (merecen rayito ⚡)
        // Solo subtitulados
        val FAST_SERVERS = setOf(
            "desu",      // HTTP directo, muy rápido
            "voe",       // WebView pero HLS fluido
            "yourupload" // HTTP directo de TioAnime
        )
    }
}
