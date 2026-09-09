package com.mew.animemew

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mew.animemew.data.ads.AdBlockDetector
import com.mew.animemew.data.auth.SessionManager
import com.mew.animemew.data.local.ThemePreferences
import com.mew.animemew.data.version.UpdateState
import com.mew.animemew.data.version.VersionChecker
import com.mew.animemew.notifications.AnimeNotificationsManager
import com.mew.animemew.ui.components.CrashOverlay
import com.mew.animemew.ui.screens.MainAppScreen
import com.mew.animemew.ui.screens.UpdateRequiredScreen
import com.mew.animemew.ui.theme.AnimemewTheme
import com.mew.animemew.ui.theme.NeonPurple

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // NUEVO Fase 5: extras pasados desde la notificación al MainActivity
        const val EXTRA_DEEP_LINK_SLUG = "deep_link_slug"
        const val EXTRA_DEEP_LINK_EPISODE = "deep_link_episode"
        const val EXTRA_DEEP_LINK_TITLE = "deep_link_title"
        const val EXTRA_DEEP_LINK_COVER = "deep_link_cover"
        const val EXTRA_DEEP_LINK_TOTAL = "deep_link_total"
        const val EXTRA_DEEP_LINK_ANILIST_ID = "deep_link_anilist_id"
        const val EXTRA_DEEP_LINK_IS_AIRING = "deep_link_is_airing"
        const val EXTRA_DEEP_LINK_NEXT_TS = "deep_link_next_ts"

        // NUEVO Fase 5: deep link pendiente de navegar (lo lee MainAppScreen)
        @Volatile
        var pendingDeepLink: DeepLinkData? = null
            private set

        // Método para limpiar el deep link después de navegar
        fun clearPendingDeepLink() {
            pendingDeepLink = null
        }
    }

    // NUEVO Fase 5: launcher para pedir permiso de notificaciones en runtime
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(TAG, "Permiso POST_NOTIFICATIONS concedido: $granted")
        // Si no concede, las notificaciones simplemente no aparecen, la app sigue funcionando
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: se muestra antes de que Compose cargue.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // NUEVO Fase 5: crear canal de notificaciones al iniciar la app
        AnimeNotificationsManager.createNotificationChannel(this)

        // NUEVO Fase 5: pedir permiso de notificaciones en Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // NUEVO Fase 5: manejar deep link desde notificación (si la app se abrió por notificación)
        handleDeepLinkFromNotification(intent)

        // NUEVO: Start.io SDK se inicializa perezosamente en AdManager
        // cuando se crea el primer banner o se muestra el primer interstitial.
        // Esto evita que el splash verde/magenta/azul aparezca al abrir la app.

        // NUEVO: Chequeo de adblocker en background al abrir la app
        // (calienta el caché, no bloquea al usuario)
        // Usamos runBlocking dentro de un Thread porque isAdBlocked es suspend
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    AdBlockDetector.getInstance(this@MainActivity).isAdBlocked(useCache = false)
                }
            } catch (e: Exception) {
                // ignore
            }
        }.start()

        val themePreferences = ThemePreferences(this)
        val sessionManager = SessionManager.getInstance(this)
        // NUEVO: checker de versión
        val versionChecker = VersionChecker(this)

        setContent {
            val themeState by themePreferences.themeFlow.collectAsState(initial = "system")
            val darkTheme = when (themeState) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // NUEVO: Verificar versión al iniciar
            LaunchedEffect(Unit) {
                versionChecker.checkVersion()
            }

            val updateState by versionChecker.state.collectAsState()

            AnimemewTheme(darkTheme = darkTheme) {
                when (val state = updateState) {
                    is UpdateState.Required -> {
                        // BLOQUEAR todo — mostrar pantalla de actualización obligatoria
                        UpdateRequiredScreen(info = state.info)
                    }
                    is UpdateState.Loading -> {
                        // Pantalla de carga mientras verifica versión
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D0518)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonPurple)
                        }
                    }
                    else -> {
                        // OK o Error → mostrar la app normal
                        MainAppScreen(sessionManager = sessionManager)
                    }
                }

                // NUEVO Fase 1: overlay global de crashes.
                CrashOverlay()
            }
        }
    }

    // =========================================================
    // NUEVO Fase 5: Manejar deep link desde notificación
    // =========================================================
    //
    // Cuando el usuario toca una notificación de "nuevo episodio",
    // el PendingIntent abre MainActivity con un URI del tipo:
    //   animemew://player/{slug}/{episode}
    //
    // Acá lo interceptamos y extraemos los datos del anime para
    // que MainAppScreen navegue automáticamente al PlayerScreen.
    //
    private fun handleDeepLinkFromNotification(intent: Intent?) {
        intent ?: return
        val data: Uri = intent.data ?: return

        if (data.scheme != "animemew" || data.host != "player") {
            return
        }

        // Extraer slug y episode del path: animemew://player/{slug}/{episode}
        val pathSegments = data.pathSegments
        if (pathSegments.size < 2) {
            Log.w(TAG, "Deep link inválido: $data")
            return
        }

        val slug = pathSegments[0]
        val episode = pathSegments[1].toIntOrNull() ?: 1
        val title = intent.getStringExtra(EXTRA_DEEP_LINK_TITLE) ?: slug
        val coverUrl = intent.getStringExtra(EXTRA_DEEP_LINK_COVER) ?: ""
        val totalEpisodes = intent.getIntExtra(EXTRA_DEEP_LINK_TOTAL, 0)
        val anilistId = intent.getIntExtra(EXTRA_DEEP_LINK_ANILIST_ID, 0)
        val isAiring = intent.getBooleanExtra(EXTRA_DEEP_LINK_IS_AIRING, false)
        val nextTs = intent.getLongExtra(EXTRA_DEEP_LINK_NEXT_TS, 0L)

        Log.i(TAG, "📂 Deep link recibido: $title E$episode (anilistId=$anilistId)")

        // Guardar los datos en un lugar accesible para MainAppScreen
        // Usamos un companion object estático porque MainAppScreen se crea
        // sin acceso a estos extras directamente.
        pendingDeepLink = DeepLinkData(
            slug = slug,
            episode = episode,
            title = title,
            coverUrl = coverUrl,
            totalEpisodes = totalEpisodes,
            anilistId = anilistId,
            isAiring = isAiring,
            nextEpisodeTimestamp = nextTs
        )

        // Cancelar la notificación de este anime (ya lo abrió)
        if (anilistId > 0) {
            AnimeNotificationsManager.cancelNotification(this, anilistId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Si la app ya estaba abierta y llegó una nueva notificación
        handleDeepLinkFromNotification(intent)
    }

    // =========================================================
    // NUEVO Fase 4 A2: Picture-in-Picture
    // =========================================================

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().build()
                )
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo entrar en PiP: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.i(TAG, "PiP mode: $isInPictureInPictureMode")
    }

    // =========================================================
    // Deep link data (estático para que MainAppScreen lo lea)
    // =========================================================

    data class DeepLinkData(
        val slug: String,
        val episode: Int,
        val title: String,
        val coverUrl: String,
        val totalEpisodes: Int,
        val anilistId: Int,
        val isAiring: Boolean,
        val nextEpisodeTimestamp: Long
    )
}
