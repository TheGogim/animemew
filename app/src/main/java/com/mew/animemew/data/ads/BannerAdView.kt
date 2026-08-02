package com.mew.animemew.data.ads

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// =========================================================
//  BannerAdView — Composable que muestra un banner de Start.io.
//
//  Uso:
//  BannerAdView()  // sin parámetros, se ajusta solo
//
//  Comportamiento:
//  - Si adsEnabled == false → no renderiza nada (0dp)
//  - Si es TV → muestra banner (los banners SÍ funcionan en TV)
//  - Si SDK no inicializado → placeholder gris
//  - Carga el banner de Start.io dentro de un AndroidView
// =========================================================

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdManager.getInstance(context) }

    // Verificar si los ads están activos
    val adsEnabled = remember { adManager.isAdsEnabled() }

    if (!adsEnabled || activity == null) {
        // No mostrar nada — usuario premium o sin activity
        Box(modifier = modifier.height(0.dp))
        return
    }

    // Crear el banner de Start.io una sola vez
    val banner = remember { adManager.createBanner(activity) }

    if (banner == null) {
        // SDK no inicializado o error — placeholder gris
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Color(0xFF1A1A2E))
        )
        return
    }

    // AndroidView para integrar el banner de Start.io
    AndroidView(
        factory = { ctx ->
            // Contenedor LinearLayout para el banner
            LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                addView(banner)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    )

    // Lifecycle: limpiar el banner cuando sale de pantalla
    DisposableEffect(banner) {
        onDispose {
            try {
                // Banner no tiene onPause/onDestroy público en esta versión del SDK
                // Start.io maneja el lifecycle internamente
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
