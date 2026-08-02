package com.mew.animemew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mew.animemew.data.ads.AdBlockDetector
import com.mew.animemew.data.auth.SessionManager
import com.mew.animemew.data.local.ThemePreferences
import com.mew.animemew.data.version.UpdateState
import com.mew.animemew.data.version.VersionChecker
import com.mew.animemew.ui.screens.MainAppScreen
import com.mew.animemew.ui.screens.UpdateRequiredScreen
import com.mew.animemew.ui.theme.AnimemewTheme
import com.mew.animemew.ui.theme.NeonPurple

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: se muestra antes de que Compose cargue.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            }
        }
    }
}

