package com.mew.animemew.data.auth

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import com.mew.animemew.data.sync.CryptoHelper

// =========================================================
//  Estado de sesión observable por la UI.
// =========================================================
data class SessionInfo(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val userId: Int? = null,
    val email: String? = null,
    val salt: String? = null,
    val adsEnabled: Boolean = true  // NUEVO: si el usuario ve anuncios
)

// =========================================================
//  SessionManager — SINGLETON.
//
//  Es CRÍTICO que sea singleton para que el StateFlow de
//  sesión sea compartido entre la UI y el SyncManager.
//  Si hay 2 instancias, el login actualiza una pero la
//  otra sigue creyendo que no hay sesión, y el sync
//  nunca dispara peticiones.
// =========================================================

class SessionManager private constructor(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "animemew_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("animemew_session_fallback", Context.MODE_PRIVATE)
    }

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<SessionInfo> = _session.asStateFlow()

    private val _lastSyncMs = MutableStateFlow<Long?>(prefs.getLong(KEY_LAST_SYNC, -1L).takeIf { it > 0 })
    val lastSyncMs: StateFlow<Long?> = _lastSyncMs.asStateFlow()

    private fun loadSession(): SessionInfo {
        return SessionInfo(
            isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false),
            token = prefs.getString(KEY_TOKEN, null),
            userId = prefs.getInt(KEY_USER_ID, -1).takeIf { it > 0 },
            email = prefs.getString(KEY_EMAIL, null),
            salt = prefs.getString(KEY_SALT, null),
            adsEnabled = prefs.getBoolean(KEY_ADS_ENABLED, true)  // NUEVO
        )
    }

    fun saveSession(authResponse: AuthResponse, password: String) {
        val aesKey = CryptoHelper.deriveKey(password, authResponse.salt)
        val keyB64 = Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_TOKEN, authResponse.token)
            .putInt(KEY_USER_ID, authResponse.userId)
            .putString(KEY_EMAIL, authResponse.email)
            .putString(KEY_SALT, authResponse.salt)
            .putString(KEY_AES_KEY, keyB64)
            .putBoolean(KEY_ADS_ENABLED, true)  // NUEVO: default true, se actualiza al llamar /auth/me
            .apply()

        _session.value = SessionInfo(
            isLoggedIn = true,
            token = authResponse.token,
            userId = authResponse.userId,
            email = authResponse.email,
            salt = authResponse.salt,
            adsEnabled = true  // default, se actualiza con fetchUserInfo()
        )
    }

    /**
     * NUEVO: Actualiza el flag adsEnabled desde el servidor.
     * Llamado después de login o al abrir la app.
     */
    fun updateAdsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADS_ENABLED, enabled).apply()
        _session.value = _session.value.copy(adsEnabled = enabled)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _session.value = SessionInfo()
        _lastSyncMs.value = null
    }

    fun authHeader(): String? {
        val t = _session.value.token ?: return null
        return "Bearer $t"
    }

    fun getAesKey(): SecretKey? {
        val keyB64 = prefs.getString(KEY_AES_KEY, null) ?: return null
        return try {
            SecretKeySpec(Base64.decode(keyB64, Base64.NO_WRAP), "AES")
        } catch (e: Exception) {
            null
        }
    }

    fun markSynced(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestampMs).apply()
        _lastSyncMs.value = timestampMs
    }

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                SessionManager(appContext).also { INSTANCE = it }
            }
        }

        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_SALT = "salt"
        private const val KEY_AES_KEY = "aes_key"
        private const val KEY_LAST_SYNC = "last_sync_ms"
        private const val KEY_ADS_ENABLED = "ads_enabled"  // NUEVO
    }
}
