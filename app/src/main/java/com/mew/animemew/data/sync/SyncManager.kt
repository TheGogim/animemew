package com.mew.animemew.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mew.animemew.data.auth.SessionManager
import com.mew.animemew.data.auth.SnapshotUpload
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// =========================================================
//  SyncManager — orquesta la sincronización entre Room y
//  el server.
//
//  Métodos:
//   - pushAsync():  fire-and-forget. Lanza push() en IO.
//                   Llamar tras cualquier mutación de Room.
//   - pullAsync():  fire-and-forget. Lanza pull() en IO.
//                   Llamar al abrir la app.
//   - push():       suspend. Cifra Room → sube al server.
//   - pull():       suspend. Descarga → descifra → merge Room.
//
//  Estado observable via `state: StateFlow<SyncState>`
//  para que el SyncStatusBadge reaccione.
// =========================================================

class SyncManager private constructor(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val db: AnimeDatabase
) {
    private val snapshotBuilder = SnapshotBuilder(db)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow<SyncState>(SyncState.Never)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    // NUEVO: wrappers para registrar borrados antes de pushear
    fun registerHistoryDeletion(slug: String) {
        snapshotBuilder.registerHistoryDeletion(slug)
    }

    fun registerListDeletion(listId: Int) {
        snapshotBuilder.registerListDeletion(listId)
    }

    init {
        // Inicializar estado según sesión
        if (!sessionManager.session.value.isLoggedIn) {
            _state.value = SyncState.LoggedOut
        } else {
            val lastMs = sessionManager.lastSyncMs.value
            _state.value = if (lastMs != null) SyncState.Success(lastMs) else SyncState.Never
        }

        // Programar sync periódica (cada 15 min)
        schedulePeriodicSync()
    }

    // =====================================================
    //  PUSH: Room → Server
    // =====================================================

    /** Fire-and-forget. Llamar tras mutaciones de Room. */
    fun pushAsync() {
        val session = sessionManager.session.value
        if (!session.isLoggedIn) return
        scope.launch { push() }
    }

    suspend fun push() {
        val session = sessionManager.session.value
        if (!session.isLoggedIn) {
            _state.value = SyncState.LoggedOut
            return
        }
        val key = sessionManager.getAesKey() ?: run {
            _state.value = SyncState.Error("No hay clave de cifrado")
            return
        }
        val authHeader = sessionManager.authHeader() ?: return

        try {
            _state.value = SyncState.Syncing
            val json = snapshotBuilder.build()
            val encrypted = CryptoHelper.encrypt(json.toByteArray(Charsets.UTF_8), key)

            val response = ApiClient.authService.uploadSnapshot(
                authHeader,
                SnapshotUpload(encryptedBlob = encrypted, schemaVersion = 1)
            )

            if (response.isSuccessful) {
                val now = System.currentTimeMillis()
                sessionManager.markSynced(now)
                _state.value = SyncState.Success(now)
                Log.d(TAG, "Push OK: ${json.length} chars")
            } else {
                val lastMs = sessionManager.lastSyncMs.value
                _state.value = SyncState.Error("HTTP ${response.code()}", lastMs)
                Log.e(TAG, "Push failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            val lastMs = sessionManager.lastSyncMs.value
            _state.value = SyncState.Error(e.message ?: "Error de red", lastMs)
            Log.e(TAG, "Push exception", e)
        }
    }

    // =====================================================
    //  PULL: Server → Room
    // =====================================================

    /** Fire-and-forget. Llamar al abrir la app. */
    fun pullAsync() {
        val session = sessionManager.session.value
        if (!session.isLoggedIn) return
        scope.launch { pull() }
    }

    suspend fun pull() {
        val session = sessionManager.session.value
        if (!session.isLoggedIn) {
            _state.value = SyncState.LoggedOut
            return
        }
        val key = sessionManager.getAesKey() ?: run {
            _state.value = SyncState.Error("No hay clave de cifrado")
            return
        }
        val authHeader = sessionManager.authHeader() ?: return

        try {
            _state.value = SyncState.Syncing
            val response = ApiClient.authService.getSnapshot(authHeader)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.encryptedBlob != null) {
                    val decrypted = CryptoHelper.decrypt(body.encryptedBlob, key)
                    val json = String(decrypted, Charsets.UTF_8)
                    val snapshot = snapshotBuilder.parse(json)
                    if (snapshot != null) {
                        snapshotBuilder.apply(snapshot)
                        Log.d(TAG, "Pull OK: applied snapshot")
                    }
                }
                val now = System.currentTimeMillis()
                sessionManager.markSynced(now)
                _state.value = SyncState.Success(now)
            } else {
                val lastMs = sessionManager.lastSyncMs.value
                _state.value = SyncState.Error("HTTP ${response.code()}", lastMs)
                Log.e(TAG, "Pull failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            val lastMs = sessionManager.lastSyncMs.value
            _state.value = SyncState.Error(e.message ?: "Error de red", lastMs)
            Log.e(TAG, "Pull exception", e)
        }
    }

    /** Pull seguido de push — útil para el "Sincronizar ahora" manual. */
    fun syncNowAsync() {
        val session = sessionManager.session.value
        if (!session.isLoggedIn) return
        scope.launch {
            pull()
            push()
        }
    }

    // =====================================================
    //  WORKMANAGER — sync periódica cada 15 min
    // =====================================================

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "animemew_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                // CAMBIO: usar el MISMO singleton de SessionManager que el resto de la app.
                // Antes creaba su propia instancia, lo que hacía que el StateFlow de sesión
                // no se compartiera y el sync nunca se disparaba tras el login.
                val session = SessionManager.getInstance(appContext)
                val db = AnimeDatabase.getDatabase(appContext)
                SyncManager(appContext, session, db).also { INSTANCE = it }
            }
        }
    }
}
