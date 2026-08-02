package com.mew.animemew.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * KeepScreenOn — mantiene la pantalla encendida mientras está compuesto.
 * Se libera automáticamente al salir del composable.
 * No requiere permisos WAKE_LOCK.
 */
@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}