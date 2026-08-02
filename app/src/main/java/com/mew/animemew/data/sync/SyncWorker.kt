package com.mew.animemew.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mew.animemew.data.airing.AiringController

// =========================================================
//  SyncWorker — se ejecuta cada 15 min en background.
//  Solo hace algo si hay sesión activa.
//
//  NUEVO: también verifica animes en "En espera" vía
//  AiringController, para habilitar nuevos episodios
//  automáticamente cuando ya están disponibles.
// =========================================================

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncManager = SyncManager.getInstance(applicationContext)
        val session = syncManager.state.value

        // Si no hay sesión, no hacer nada
        if (session is SyncState.LoggedOut) return Result.success()

        return try {
            // 1. Sync normal (pull + push)
            syncManager.pull()
            syncManager.push()

            // 2. NUEVO: Verificar animes en "En espera"
            // Esto habilita nuevos episodios cuando ya están disponibles
            // en jkanime o tioanime.
            try {
                val airingController = AiringController.getInstance(applicationContext)
                val updated = airingController.checkAllWaiting()
                if (updated > 0) {
                    Log.i("SyncWorker", "✅ $updated animes en espera actualizados")
                    // Hacer push de los cambios
                    syncManager.push()
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Error en AiringController: ${e.message}")
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
