package com.mew.animemew.data.ads

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// =========================================================
//  AdBlockDetector — Detecta bloqueadores de anuncios.
//
//  ESTRATEGIA:
//  1. Control: verificar que hay internet conectando a
//     connectivitycheck.gstatic.com (Google connectivity check)
//  2. Ads: intentar conectar a dominios de servidores de anuncios
//     - googleads.g.doubleclick.net
//     - pagead2.googlesyndication.com
//     - adservice.google.com
//
//  LÓGICA:
//  - Si control FALLA → sin internet, NO es adblocker (no bloqueamos)
//  - Si control OK + TODOS los ads fallan → adblocker detectado
//  - Si control OK + AL MENOS UN ad responde → sin adblocker
//
//  NOTA: No miramos el código HTTP (404, 301, etc).
//  Solo si la conexión TCP/SSL se establece.
//  Los adblockers RECHAZAN la conexión (connection refused, timeout).
// =========================================================

class AdBlockDetector private constructor(private val context: Context) {

    private val TAG = "AdBlockDetector"

    companion object {
        @Volatile
        private var INSTANCE: AdBlockDetector? = null

        fun getInstance(context: Context): AdBlockDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdBlockDetector(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Dominio de control (Google connectivity check, siempre responde 204)
        private const val CONTROL_URL = "https://connectivitycheck.gstatic.com/generate_204"

        // Dominios de servidores de anuncios (siempre bloqueados por adblockers)
        private val AD_URLS = listOf(
            "https://googleads.g.doubleclick.net",
            "https://pagead2.googlesyndication.com/pagead/id",
            "https://adservice.google.com"
        )

        // Timeout en milisegundos
        private const val TIMEOUT_MS = 4000
    }

    // Caché del resultado al abrir la app (5 minutos)
    @Volatile
    private var cachedResult: Boolean? = null

    @Volatile
    private var cachedTimestamp: Long = 0L

    private val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 minutos

    /**
     * Verifica si hay un adblocker activo.
     *
     * @param useCache Si true, usa el caché (para chequeo al abrir app).
     *                 Si false, siempre hace chequeo fresco (para reproducir).
     * @return true si se detecta adblocker, false si no hay o si no hay internet
     */
    suspend fun isAdBlocked(useCache: Boolean = false): Boolean {
        // Si se permite caché y hay uno válido, usarlo
        if (useCache) {
            val cached = cachedResult
            val timestamp = cachedTimestamp
            if (cached != null && System.currentTimeMillis() - timestamp < CACHE_TTL_MS) {
                Log.i(TAG, "Usando caché: adblocked=$cached")
                return cached
            }
        }

        val result = performCheck()

        // Guardar en caché
        cachedResult = result
        cachedTimestamp = System.currentTimeMillis()

        return result
    }

    /**
     * Fuerza un re-chequeo (cuando usuario dice "ya lo desactivé").
     * Ignora el caché siempre.
     */
    suspend fun recheck(): Boolean {
        Log.i(TAG, "Re-check forzado por usuario")
        return performCheck()
    }

    /**
     * Ejecuta el chequeo real.
     */
    private suspend fun performCheck(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== Iniciando chequeo de adblocker ===")

        // 1. Control: verificar internet
        val controlOk = canConnect(CONTROL_URL)
        Log.i(TAG, "Control ($CONTROL_URL): ${if (controlOk) "OK" else "FAIL"}")

        if (!controlOk) {
            // Sin internet → no es adblocker, no bloqueamos
            Log.i(TAG, "Sin internet, no se detecta adblocker")
            return@withContext false
        }

        // 2. Probar dominios de ads
        var anyAdOk = false
        for (adUrl in AD_URLS) {
            val ok = canConnect(adUrl)
            Log.i(TAG, "Ad ($adUrl): ${if (ok) "OK" else "BLOCKED"}")
            if (ok) {
                anyAdOk = true
                break  // con uno que responda, basta
            }
        }

        // 3. Veredicto
        val adblocked = !anyAdOk
        Log.i(TAG, "=== Resultado: adblocked=$adblocked ===")
        return@withContext adblocked
    }

    /**
     * Intenta conectar a una URL.
     * Devuelve true si la conexión se establece (cualquier código HTTP).
     * Devuelve false si hay timeout, connection refused, unknown host, etc.
     */
    private fun canConnect(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = false  // no seguir redirects, solo verificar conexión
                setRequestProperty("User-Agent", "AnimeMew/1.0 (Android; AdblockCheck)")
            }
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            // Cualquier código HTTP (incluso 404, 301, 400) = conexión OK
            // Solo nos importa que el servidor respondió
            code > 0
        } catch (e: Exception) {
            // Timeout, ConnectionRefused, UnknownHost, SSLException, etc.
            Log.d(TAG, "  canConnect fail: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Limpia el caché (para testing).
     */
    fun clearCache() {
        cachedResult = null
        cachedTimestamp = 0L
    }
}
