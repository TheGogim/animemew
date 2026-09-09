package com.mew.animemew.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// =========================================================
//  PlayerGestures — gestos del reproductor (Fase 4 v2).
//
//  FIXES v2:
//   1. Tap simple revive controles (no requiere doble tap)
//   2. Reset contadores de ±10s cuando hay pausa > 1s entre taps
//   3. Indicadores ±10s más a los lados
//   4. (El brillo/volumen invertido lo dejás como está, estilos únicos)
// =========================================================

private val GestureOverlayColor = Color(0xFF8B5CF6)
private val IndicatorBg = Color.Black.copy(alpha = 0.6f)

/**
 * @param isMobile true si es móvil/tablet (no TV). En TV no se renderiza.
 * @param onSeek10Backward callback para retroceder 10s
 * @param onSeek10Forward callback para avanzar 10s
 * @param onTap callback para revivir/ocultar controles (tap simple en cualquier parte)
 */
@Composable
fun PlayerGestures(
    isMobile: Boolean,
    onSeek10Backward: () -> Unit,
    onSeek10Forward: () -> Unit,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isMobile) return  // No-op en TV

    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    // Estados visuales para el feedback
    var brightnessIndicator by remember { mutableFloatStateOf(getCurrentBrightness(context, window)) }
    var volumeIndicator by remember { mutableIntStateOf(getCurrentVolume(context)) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }
    var rewindCount by remember { mutableIntStateOf(0) }
    var forwardCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Auto-ocultar indicadores después de 1.2s
    LaunchedEffect(showBrightnessIndicator, brightnessIndicator) {
        if (showBrightnessIndicator) {
            delay(1200)
            showBrightnessIndicator = false
        }
    }
    LaunchedEffect(showVolumeIndicator, volumeIndicator) {
        if (showVolumeIndicator) {
            delay(1200)
            showVolumeIndicator = false
        }
    }
    LaunchedEffect(showRewindIndicator, rewindCount) {
        if (showRewindIndicator) {
            delay(800)
            showRewindIndicator = false
        }
    }
    LaunchedEffect(showForwardIndicator, forwardCount) {
        if (showForwardIndicator) {
            delay(800)
            showForwardIndicator = false
        }
    }

    // Reset contadores si pasan más de 1s sin taps (para que cada "toque aislado"
    // cuente como 1 y no acumule eternamente)
    LaunchedEffect(rewindCount, forwardCount) {
        if (rewindCount > 0 || forwardCount > 0) {
            delay(1000)
            // Si pasaron 1s sin nuevos taps, resetear contadores
            rewindCount = 0
            forwardCount = 0
        }
    }

    // Variables para tracking del drag (no son state porque no queremos recomposición en cada pixel)
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragStartBrightness by remember { mutableFloatStateOf(0f) }
    var dragStartVolume by remember { mutableIntStateOf(0) }
    var isLeftHalfDrag by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Tap simple → revivir/ocultar controles
                        onTap()
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val now = System.currentTimeMillis()

                        // Reset contadores si pasaron más de 1s desde el último tap
                        if (now - lastTapTime > 1000) {
                            rewindCount = 0
                            forwardCount = 0
                        }
                        lastTapTime = now

                        if (offset.x < width / 2) {
                            // Doble tap izquierda → -10s (acumula si el usuario sigue tocando rápido)
                            rewindCount++
                            showRewindIndicator = true
                            onSeek10Backward()
                        } else {
                            // Doble tap derecha → +10s
                            forwardCount++
                            showForwardIndicator = true
                            onSeek10Forward()
                        }
                    }
                    // onLongPress y onLongPress no se manejan acá: los deja pasar el Box padre
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        isLeftHalfDrag = offset.x < width / 2
                        dragStartY = offset.y
                        dragStartBrightness = getCurrentBrightness(context, window)
                        dragStartVolume = getCurrentVolume(context)
                    },
                    onDragEnd = {
                        isLeftHalfDrag = false
                    },
                    onDrag = { change, _ ->
                        val dy = dragStartY - change.position.y  // positivo = swipe hacia arriba
                        val height = size.height
                        if (height == 0) return@detectDragGestures

                        // Normalizar el movimiento: el 100% de la pantalla = cambio total
                        val normalizedDelta = dy / height

                        if (isLeftHalfDrag) {
                            // Brillo: 0.0 a 1.0
                            val newBrightness = (dragStartBrightness + normalizedDelta).coerceIn(0.0f, 1.0f)
                            setBrightness(context, window, newBrightness)
                            brightnessIndicator = newBrightness
                            showBrightnessIndicator = true
                        } else {
                            // Volumen: 0 a maxVolume
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                            val volumeDelta = (normalizedDelta * maxVolume * 1.5f).toInt()  // multiplicador para que sea sensible
                            val newVolume = (dragStartVolume + volumeDelta).coerceIn(0, maxVolume)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            volumeIndicator = newVolume
                            showVolumeIndicator = true
                        }
                    }
                )
            }
    ) {
        // Indicador de brillo (abajo a la izquierda)
        if (showBrightnessIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndicatorBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.BrightnessHigh,
                        contentDescription = "Brillo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Barra vertical de brillo
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(brightnessIndicator)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(GestureOverlayColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(brightnessIndicator * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Indicador de volumen (abajo a la derecha)
        if (showVolumeIndicator) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val volPercent = if (maxVolume > 0) volumeIndicator.toFloat() / maxVolume else 0f

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndicatorBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "Volumen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(volPercent)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(GestureOverlayColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${volumeIndicator}/${maxVolume}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Indicador "Retroceder 10s" — MÁS a la izquierda
        if (showRewindIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 64.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(IndicatorBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "-${rewindCount * 10}s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Indicador "Avanzar 10s" — MÁS a la derecha
        if (showForwardIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 64.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(IndicatorBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "+${forwardCount * 10}s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =========================================================
//  Helpers de brillo y volumen
// =========================================================

private fun getCurrentBrightness(context: Context, window: android.view.Window?): Float {
    return try {
        val attrs = window?.attributes
        if (attrs?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE < 0f) {
            Settings.System.getFloat(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0.5f)
        } else {
            attrs?.screenBrightness ?: 0.5f
        }
    } catch (e: Exception) {
        0.5f
    }
}

private fun setBrightness(context: Context, window: android.view.Window?, brightness: Float) {
    try {
        val attrs = window?.attributes
        attrs?.screenBrightness = brightness
        window?.attributes = attrs
    } catch (e: Exception) {
        Log.e("PlayerGestures", "Error seteando brillo: ${e.message}")
    }
}

private fun getCurrentVolume(context: Context): Int {
    return try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    } catch (e: Exception) {
        0
    }
}
