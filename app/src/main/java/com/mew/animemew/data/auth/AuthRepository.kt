package com.mew.animemew.data.auth

import com.mew.animemew.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// =========================================================
//  AuthRepository — wrapper alrededor de AuthService.
//
//  CAMBIO Fase 3: saveSession ahora recibe el password para
//  derivar la clave AES. El password NO se guarda, solo se
//  usa en memoria para derivar la clave y se descarta.
// =========================================================

class AuthRepository(private val sessionManager: SessionManager) {

    val session = sessionManager.session
    val lastSyncMs = sessionManager.lastSyncMs

    suspend fun register(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.authService.register(
                    RegisterRequest(email = email.trim(), password = password)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                        ?: return@withContext Result.failure(Exception("Respuesta vacía del servidor"))
                    sessionManager.saveSession(body, password)  // ← pasamos password
                    Result.success(body)
                } else {
                    Result.failure(Exception(parseError(response.code(), response.errorBody()?.string())))
                }
            } catch (e: Exception) {
                Result.failure(Exception(parseNetworkError(e)))
            }
        }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.authService.login(
                    LoginRequest(email = email.trim(), password = password)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                        ?: return@withContext Result.failure(Exception("Respuesta vacía del servidor"))
                    sessionManager.saveSession(body, password)  // ← pasamos password
                    Result.success(body)
                } else {
                    Result.failure(Exception(parseError(response.code(), response.errorBody()?.string())))
                }
            } catch (e: Exception) {
                Result.failure(Exception(parseNetworkError(e)))
            }
        }

    fun logout() {
        sessionManager.clearSession()
    }

    /**
     * NUEVO: Obtiene info del usuario desde /auth/me y actualiza adsEnabled.
     * Llamar después de login o al abrir la app.
     */
    suspend fun fetchUserInfo(): Result<MeResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = sessionManager.authHeader() ?: return@withContext Result.failure(Exception("No hay sesión"))
            val response = ApiClient.authService.me(authHeader)
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(Exception("Respuesta vacía"))
                // NUEVO: actualizar adsEnabled en la sesión
                sessionManager.updateAdsEnabled(body.adsEnabled)
                Result.success(body)
            } else {
                Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================
    //  PARSING DE ERRORES
    // =========================================================

    private fun parseError(code: Int, errorBody: String?): String {
        val detail = try {
            JSONObject(errorBody ?: "{}").optString("detail", null)
        } catch (e: Exception) {
            null
        }
        return when (code) {
            401 -> "Email o contraseña incorrectos"
            409 -> "Ese email ya está registrado"
            422 -> detail ?: "Datos inválidos (revisa el email)"
            500, 502, 503 -> "El servidor tuvo un problema. Intenta más tarde."
            else -> detail ?: "Error del servidor ($code)"
        }
    }

    private fun parseNetworkError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Unable to resolve host") -> "Sin conexión al servidor. Revisa tu internet."
            msg.contains("timeout") || msg.contains("timed out") -> "El servidor tardó demasiado. Intenta de nuevo."
            msg.contains("Failed to connect") -> "No se pudo conectar al servidor."
            else -> msg.ifBlank { "Error desconocido" }
        }
    }
}
