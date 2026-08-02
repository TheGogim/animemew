package com.mew.animemew.data.version

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// =========================================================
//  VersionChecker — Verifica si la app necesita actualizarse
//
//  Flujo:
//  1. Al iniciar la app, llama a /api/version del servidor
//  2. Compara version_name de la app con min_version del server
//  3. Si app_version < min_version → UpdateState.Required
//  4. Si app_version >= min_version → UpdateState.OK
//  5. Si no hay internet o error → UpdateState.OK (no bloquear)
//
//  Comparación de versiones:
//  "7.0" < "8.0" → required
//  "7.0" < "7.1" → required
//  "7.0" == "7.0" → ok
//  "8.0" > "7.0" → ok (admin/debugger)
// =========================================================

data class VersionInfo(
    @SerializedName("min_version")
    val minVersion: String = "0.0",
    @SerializedName("latest_version")
    val latestVersion: String = "0.0",
    @SerializedName("download_url")
    val downloadUrl: String = "",
    @SerializedName("description")
    val description: String = ""
)

sealed class UpdateState {
    object Loading : UpdateState()
    object OK : UpdateState()
    data class Required(val info: VersionInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class VersionChecker(private val context: Context) {

    private val gson = Gson()
    private val TAG = "VersionChecker"

    // ⚠️ CAMBIA ESTO por tu dominio real
    private val VERSION_URL = "https://animemew-api.duckdns.org/api/version"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Loading)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Obtiene el version_name de la app desde PackageManager.
     * Ej: "7.0"
     */
    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error obteniendo versión de la app: ${e.message}")
            "0.0"
        }
    }

    /**
     * Compara dos versiones semánticas.
     * Devuelve:
     *   -1 si v1 < v2
     *    0 si v1 == v2
     *    1 si v1 > v2
     *
     * Ej: compareVersions("7.0", "8.0") = -1
     *     compareVersions("7.1", "7.0") = 1
     *     compareVersions("7.0.1", "7.0") = 1
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }
        return 0
    }

    /**
     * Verifica la versión contra el servidor.
     * Llamar al iniciar la app.
     */
    suspend fun checkVersion() {
        _state.value = UpdateState.Loading
        Log.i(TAG, "=== Verificando versión ===")

        val appVersion = getAppVersionName()
        Log.i(TAG, "Versión de la app: $appVersion")

        try {
            val info = withContext(Dispatchers.IO) { fetchVersionInfo() }
            if (info == null) {
                Log.w(TAG, "No se pudo obtener info del servidor — permitiendo acceso")
                _state.value = UpdateState.OK
                return
            }

            Log.i(TAG, "Server min_version: ${info.minVersion}, latest: ${info.latestVersion}")
            Log.i(TAG, "Download URL: ${info.downloadUrl}")

            val comparison = compareVersions(appVersion, info.minVersion)
            when {
                comparison < 0 -> {
                    Log.w(TAG, "⚠️ App ($appVersion) < Server (${info.minVersion}) → ACTUALIZACIÓN REQUERIDA")
                    _state.value = UpdateState.Required(info)
                }
                comparison == 0 -> {
                    Log.i(TAG, "✅ App ($appVersion) == Server (${info.minVersion}) → OK")
                    _state.value = UpdateState.OK
                }
                else -> {
                    Log.i(TAG, "✅ App ($appVersion) > Server (${info.minVersion}) → OK (admin/debugger)")
                    _state.value = UpdateState.OK
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando versión: ${e.message}")
            // En caso de error, NO bloquear al usuario
            _state.value = UpdateState.OK
        }
    }

    private fun fetchVersionInfo(): VersionInfo? {
        val request = Request.Builder()
            .url(VERSION_URL)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Server respondió ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            return gson.fromJson(body, VersionInfo::class.java)
        }
    }

    /**
     * Comprueba si la actualización está requerida.
     * Conveniente para usar en composables.
     */
    fun isUpdateRequired(): Boolean {
        return _state.value is UpdateState.Required
    }
}
