package com.mew.animemew.data.sync

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mew.animemew.data.airing.AiringController
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.notifications.AnimeNotificationsManager

// =========================================================
//  SyncWorker — se ejecuta cada 15 min en background.
//  Solo hace algo si hay sesión activa.
//
//  NUEVO Fase 5: después de checkAllWaiting(), revisa qué animes
//  pasaron de hasNewEpisode=false a hasNewEpisode=true y dispara
//  una notificación local por cada uno. Solo notifica UNA vez por
//  anime (no spamea cada 15 min si ya avisó).
//
//  MODO DEBUG (línea de abajo): cuando DEBUG_AIRING_NOTIFS = true,
//  envía notificaciones informativas por CADA anime en "En espera"
//  cada vez que corre el SyncWorker (sin importar si cambió el
//  estado). Sirve para probar que el AiringController funciona.
//  ** Cambiar a false cuando termines de probar. **
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
            // 1. Crear canal de notificaciones (idempotente)
            AnimeNotificationsManager.createNotificationChannel(applicationContext)

            // 2. Sync normal (pull + push)
            syncManager.pull()
            syncManager.push()

            // 3. Capturar estado ANTES de verificar
            val dao = AnimeDatabase.getDatabase(applicationContext).animeDao()
            val stateBefore = dao.getAiringWatchHistory().associateBy { it.anilistId }
            val alreadyNotifiedAnimes = stateBefore.values
                .filter { it.hasNewEpisode }
                .map { it.anilistId }
                .toSet()

            // 4. Verificar animes en "En espera" vía AiringController
            try {
                val airingController = AiringController.getInstance(applicationContext)
                val updated = airingController.checkAllWaiting()
                if (updated > 0) {
                    Log.i("SyncWorker", "✅ $updated animes en espera actualizados")
                    syncManager.push()
                }

                // 5. NUEVO Fase 5: detectar animes con nuevos episodios
                val stateAfter = dao.getAiringWatchHistory()

                if (DEBUG_AIRING_NOTIFS) {
                    // =========================================================
                    //  MODO DEBUG: enviar notificaciones informativas por todos
                    //  los animes en espera, sin importar si cambiaron.
                    //  Útil para probar que el AiringController funciona.
                    // =========================================================
                    sendDebugNotifications(stateAfter)
                } else {
                    // =========================================================
                    //  MODO NORMAL: solo notificar animes que pasaron de
                    //  hasNewEpisode=false a true (no spam).
                    // =========================================================
                    val newlyAvailableAnimes = stateAfter.filter { history ->
                        history.hasNewEpisode &&
                        history.anilistId !in alreadyNotifiedAnimes &&
                        history.anilistId > 0
                    }

                    if (newlyAvailableAnimes.isNotEmpty()) {
                        Log.i("SyncWorker", "🔔 ${newlyAvailableAnimes.size} animes con nuevos episodios para notificar")

                        if (AnimeNotificationsManager.hasPermission(applicationContext)) {
                            newlyAvailableAnimes.forEach { history ->
                                try {
                                    AnimeNotificationsManager.showNewEpisodeNotification(
                                        applicationContext,
                                        history
                                    )
                                } catch (e: Exception) {
                                    Log.e("SyncWorker", "Error enviando notificación para ${history.title}: ${e.message}")
                                }
                            }
                        } else {
                            Log.w("SyncWorker", "Sin permiso de notificaciones, no se enviaron")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Error en AiringController: ${e.message}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error general en SyncWorker: ${e.message}")
            Result.retry()
        }
    }

    // =========================================================
    //  MODO DEBUG: notificaciones informativas para probar
    //  que el AiringController funciona correctamente.
    // =========================================================

    private suspend fun sendDebugNotifications(allAiringAnimes: List<com.mew.animemew.data.local.WatchHistoryEntity>) {
        if (!AnimeNotificationsManager.hasPermission(applicationContext)) {
            Log.w("SyncWorker", "[DEBUG] Sin permiso de notificaciones, no se envían debug notifs")
            return
        }

        if (allAiringAnimes.isEmpty()) {
            Log.i("SyncWorker", "[DEBUG] No hay animes en 'En espera' — no hay nada que notificar")
            sendDebugSummaryNotification(0, 0, 0, 0, 0)
            return
        }

        Log.i("SyncWorker", "[DEBUG] === Enviando notificaciones informativas para ${allAiringAnimes.size} animes ===")

        var totalWaitingNewEp = 0  // esperando nuevo ep
        var totalWithUnwatched = 0  // tienen caps por ver
        var totalFinishedWaiting = 0  // finalizados pero en espera
        var totalEpsUnwatched = 0

        // Notificación individual por cada anime en espera
        // El ID único por anime es (anilistId + 1000000) para que no choque
        // con el ID de notificaciones reales (que usan anilistId directo)
        allAiringAnimes.forEach { history ->
            try {
                val status = classifyAnimeStatus(history)
                // FIX Fase 5: sin emojis (compatibilidad Android 9 tablet)
                val title = "[DEBUG] ${history.title}"
                val body = when (status) {
                    AnimeStatus.DEBUG_WAITING_NEW_EP -> {
                        totalWaitingNewEp++
                        "En espera · Proximo ep: ${history.episodeNumber + 1} (aun no disponible)"
                    }
                    AnimeStatus.DEBUG_WITH_UNWATCHED -> {
                        totalWithUnwatched++
                        val unwatched = history.nextAvailableEpisode - history.episodeNumber
                        totalEpsUnwatched += unwatched
                        "En emision · $unwatched cap${if (unwatched > 1) "s" else ""} por ver (E${history.episodeNumber + 1}-E${history.nextAvailableEpisode})"
                    }
                    AnimeStatus.DEBUG_FINISHED_WAITING -> {
                        totalFinishedWaiting++
                        "Finalizado pero en espera · Viste E${history.episodeNumber}/${history.totalEpisodes}"
                    }
                }

                sendDebugIndividualNotification(history, title, body)
                Log.i("SyncWorker", "[DEBUG] Notificacion enviada: ${history.title} -> $body")
            } catch (e: Exception) {
                Log.e("SyncWorker", "[DEBUG] Error enviando notificacion para ${history.title}: ${e.message}")
            }
        }

        // Notificación general de resumen al final
        sendDebugSummaryNotification(
            total = allAiringAnimes.size,
            waitingNewEp = totalWaitingNewEp,
            withUnwatched = totalWithUnwatched,
            finishedWaiting = totalFinishedWaiting,
            totalEpsUnwatched = totalEpsUnwatched
        )
    }

    private enum class AnimeStatus {
        DEBUG_WAITING_NEW_EP,    // al día con lo disponible, esperando el próximo
        DEBUG_WITH_UNWATCHED,    // tiene caps nuevos por ver
        DEBUG_FINISHED_WAITING   // anime finalizado pero en espera (edge case)
    }

    private fun classifyAnimeStatus(history: com.mew.animemew.data.local.WatchHistoryEntity): AnimeStatus {
        return when {
            // Si hay nuevos episodios disponibles para ver
            history.hasNewEpisode && history.nextAvailableEpisode > history.episodeNumber -> {
                AnimeStatus.DEBUG_WITH_UNWATCHED
            }
            // Si está al día (viste el último disponible) → esperando próximo
            else -> {
                // Heurística: si episodeNumber == totalEpisodes Y hasNewEpisode=false
                // es probable que esté esperando el próximo episodio.
                // Si nextEpisodeTimestamp > 0, sabemos que hay fecha.
                if (history.episodeNumber >= history.totalEpisodes && history.totalEpisodes > 0) {
                    AnimeStatus.DEBUG_WAITING_NEW_EP
                } else {
                    AnimeStatus.DEBUG_FINISHED_WAITING
                }
            }
        }
    }

    /**
     * Notificación individual por anime, con cover.
     * ID = anilistId + 1_000_000 (para no chocar con IDs de notifs reales)
     */
    private fun sendDebugIndividualNotification(
        history: com.mew.animemew.data.local.WatchHistoryEntity,
        title: String,
        body: String
    ) {
        val notificationId = history.anilistId + 1_000_000  // offset para no chocar con IDs reales

        val notification = NotificationCompat.Builder(applicationContext, "debug_airing")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)  // baja prioridad para no molestar tanto
            .setAutoCancel(true)
            .apply {
                // Cover del anime
                if (history.coverUrl.isNotBlank()) {
                    try {
                        val futureTarget = kotlinx.coroutines.runBlocking {
                            coil.ImageLoader(applicationContext).execute(
                                coil.request.ImageRequest.Builder(applicationContext)
                                    .data(history.coverUrl)
                                    .size(144, 144)
                                    .build()
                            )
                        }
                        val bitmap = futureTarget.drawable?.let { drawable ->
                            android.graphics.Bitmap.createBitmap(
                                144, 144, android.graphics.Bitmap.Config.ARGB_8888
                            ).also { bmp ->
                                val canvas = android.graphics.Canvas(bmp)
                                drawable.setBounds(0, 0, 144, 144)
                                drawable.draw(canvas)
                            }
                        }
                        if (bitmap != null) {
                            setStyle(NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon(bitmap)
                                .setBigContentTitle(title)
                                .setSummaryText(body))
                        }
                    } catch (e: Exception) {
                        Log.w("SyncWorker", "[DEBUG] No se pudo cargar cover para ${history.title}: ${e.message}")
                    }
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e("SyncWorker", "[DEBUG] Error mostrando notificación individual: ${e.message}")
        }
    }

    /**
     * Notificación de resumen general.
     * ID fijo = 999_999 (siempre la misma notificación, se actualiza)
     */
    private fun sendDebugSummaryNotification(
        total: Int,
        waitingNewEp: Int,
        withUnwatched: Int,
        finishedWaiting: Int,
        totalEpsUnwatched: Int
    ) {
        val title = "[DEBUG] Resumen de animes en emision"
        val body = if (total == 0) {
            "No tienes animes en 'Continuar viendo' que esten en emision"
        } else {
            buildString {
                appendLine("Total en emision: $total")
                appendLine("· Esperando proximo ep: $waitingNewEp")
                appendLine("· Con caps por ver: $withUnwatched ($totalEpsUnwatched cap${if (totalEpsUnwatched != 1) "s" else ""} en total)")
                append("· Finalizados en espera: $finishedWaiting")
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, "debug_airing")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(999_999, notification)
            Log.i("SyncWorker", "[DEBUG] Resumen enviado: $body")
        } catch (e: Exception) {
            Log.e("SyncWorker", "[DEBUG] Error mostrando resumen: ${e.message}")
        }
    }

    companion object {
        // ⚠️ MODO DEBUG — cambiar a false cuando termines de probar
        // Si está en true, cada 15 min el SyncWorker va a enviar:
        //  - 1 notificación por cada anime en "En espera"
        //  - 1 notificación de resumen general
        // Las notificaciones tienen prioridad baja y un canal distinto
        // ("debug_airing") para que puedas distinguirlas de las reales.
        const val DEBUG_AIRING_NOTIFS = false

        /**
         * NUEVO Fase 5: Fuerza la ejecución inmediata del SyncWorker.
         *
         * Llamar desde Settings cuando el usuario toca "Sincronizar ahora".
         * Esto encola un OneTimeWorkRequest que ejecuta doWork() de inmediato
         * (o casi — Android decide cuándo, pero suele ser < 5 segundos).
         *
         * Esto hace que el "Sincronizar ahora" no solo haga pull/push del
         * sync cloud, sino que también dispare el AiringController y las
         * notificaciones de debug.
         */
        fun triggerNow(context: Context) {
            Log.i("SyncWorker", "triggerNow() llamado — encolando ejecución inmediata")
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }
}
