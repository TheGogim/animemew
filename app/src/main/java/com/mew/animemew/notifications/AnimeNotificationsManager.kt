package com.mew.animemew.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mew.animemew.MainActivity
import com.mew.animemew.data.local.WatchHistoryEntity

// =========================================================
//  AnimeNotificationsManager — gestiona notificaciones locales
//  de nuevos episodios disponibles.
//
//  Funciona sin FCM ni backend push: las notificaciones se
//  disparan desde el SyncWorker (cada 15 min) cuando detecta
//  que un anime en "Continuar viendo" pasó de hasNewEpisode=false
//  a hasNewEpisode=true.
//
//  Canal: "new_episodes" (IMPORTANCE_DEFAULT, sonido + vibración)
//
//  Acción al tap: abre MainActivity que automáticamente navega
//  al PlayerScreen en el episodio correspondiente.
//
//  IDs de notificación: usamos el anilistId del anime (máximo 32 bits)
//  para que cada anime tenga su propia notificación y no se solapen.
// =========================================================

object AnimeNotificationsManager {

    private const val TAG = "AnimeNotifs"
    private const val CHANNEL_ID_NEW_EPISODES = "new_episodes"
    private const val CHANNEL_NAME = "Nuevos episodios"
    private const val CHANNEL_DESC = "Te avisamos cuando hay un episodio nuevo de un anime que estás siguiendo"

    // NUEVO DEBUG: canal separado para notificaciones de debug
    // (prioridad baja, no molesta). Lo podés desactivar desde Settings
    // del sistema sin afectar las notificaciones reales.
    private const val CHANNEL_ID_DEBUG = "debug_airing"
    private const val CHANNEL_NAME_DEBUG = "[DEBUG] Pruebas Airing"
    private const val CHANNEL_DESC_DEBUG = "Notificaciones de prueba para depurar el AiringController. Desactivá este canal cuando termines de probar."

    /**
     * Crea los canales de notificaciones. Necesario en Android 8.0+ (API 26).
     * Llamar UNA sola vez al inicio de la app (es idempotente).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal principal (notifs reales)
            val mainChannel = NotificationChannel(
                CHANNEL_ID_NEW_EPISODES,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            // Canal de debug (prioridad baja, sin sonido)
            val debugChannel = NotificationChannel(
                CHANNEL_ID_DEBUG,
                CHANNEL_NAME_DEBUG,
                NotificationManager.IMPORTANCE_LOW  // baja para no molestar
            ).apply {
                description = CHANNEL_DESC_DEBUG
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(mainChannel)
            manager?.createNotificationChannel(debugChannel)
            Log.i(TAG, "Canales de notificaciones creados (main + debug)")
        }
    }

    /**
     * Verifica si el permiso POST_NOTIFICATIONS está concedido (Android 13+).
     * En Android <13 no se requiere, devuelve true.
     */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Muestra una notificación para un anime con nuevos episodios disponibles.
     *
     * @param history La entrada de watch_history con hasNewEpisode=true
     * @return true si la notificación se mostró, false si no (sin permiso, etc.)
     */
    fun showNewEpisodeNotification(context: Context, history: WatchHistoryEntity): Boolean {
        if (!hasPermission(context)) {
            Log.w(TAG, "Sin permiso de notificaciones, no se muestra para ${history.title}")
            return false
        }

        // Calcular cuántos episodios nuevos hay
        val newEpsCount = if (history.nextAvailableEpisode > history.episodeNumber) {
            history.nextAvailableEpisode - history.episodeNumber
        } else 1

        val title = "📺 ${history.title}"
        val body = if (newEpsCount == 1) {
            "Episodio ${history.episodeNumber + 1} ya disponible"
        } else {
            "$newEpsCount episodios nuevos disponibles (E${history.episodeNumber + 1} a E${history.nextAvailableEpisode})"
        }

        // Construir Intent para abrir directamente el player en el episodio actual
        // del anime (no en el último disponible — el usuario sigue donde se quedó).
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("animemew://player/${Uri.encode(history.animeSlug)}/${history.episodeNumber}")
            putExtra("anime_title", history.title)
            putExtra("anime_cover", history.coverUrl)
            putExtra("anime_total", history.totalEpisodes)
            putExtra("anime_anilist_id", history.anilistId)
            putExtra("anime_is_airing", history.isAiring)
            putExtra("anime_next_ts", history.nextEpisodeTimestamp ?: 0L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            history.anilistId,  // request code único por anime
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEW_EPISODES)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)  // se cancela al tocar
            .setContentIntent(pendingIntent)
            .apply {
                // Cover del anime como notificación grande (si tenemos URL)
                if (history.coverUrl.isNotBlank()) {
                    try {
                        // Usar Coil (que ya está en las deps) para cargar la cover
                        // como bitmap y mostrarla en la notificación como big picture.
                        // Coil.execute() es suspend → usamos runBlocking porque estamos
                        // en contexto síncrono (NotificationCompat.Builder.apply).
                        val futureTarget = kotlinx.coroutines.runBlocking {
                            coil.ImageLoader(context).execute(
                                coil.request.ImageRequest.Builder(context)
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
                        Log.w(TAG, "No se pudo cargar cover para ${history.title}: ${e.message}")
                    }
                }
            }
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(history.anilistId, notification)
            Log.i(TAG, "✅ Notificación enviada para ${history.title} (+$newEpsCount eps)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación: ${e.message}")
            false
        }
    }

    /**
     * Cancela la notificación de un anime específico (cuando el usuario abre el anime).
     */
    fun cancelNotification(context: Context, anilistId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(anilistId)
            Log.i(TAG, "Notificación cancelada para anilistId=$anilistId")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cancelar notificación: ${e.message}")
        }
    }

    /**
     * Cancela todas las notificaciones pendientes (útil al desloguearse o cerrar la app).
     */
    fun cancelAllNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
            Log.i(TAG, "Todas las notificaciones canceladas")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron cancelar todas: ${e.message}")
        }
    }
}
