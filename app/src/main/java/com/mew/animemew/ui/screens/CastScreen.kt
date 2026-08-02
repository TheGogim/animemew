package com.mew.animemew.cast

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mew.animemew.ui.components.tvFocusable
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple

// =========================================================
//  CastScreen v3 — Pantalla de Cast mejorada
//
//  Mejoras v3:
//  - Fuerza orientación PORTRAIT (ya no se queda en horizontal)
//  - Bloquea touch passthrough al reproductor debajo
//  - Seek bar deslizable y tappable (arrastrar para adelantar)
//  - Play/Pause con estado optimista (no depende del poll)
//  - API: onPlayPause(shouldBePlaying: Boolean) -> Unit
//  - Mejor diseño visual
// =========================================================

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun CastScreen(
    title: String,
    episodeLabel: String,
    serverUrl: String,
    ipAddress: String,
    isClientConnected: Boolean,
    currentPosition: Double,
    duration: Double,
    isPlaying: Boolean,
    // ⚠️ API CHANGE: ahora recibe el estado deseado (true=resume, false=pause)
    // El padre debe hacer:
    //   onPlayPause = { shouldBePlaying ->
    //       if (shouldBePlaying) castServer.sendResumeCommand()
    //       else castServer.sendPauseCommand()
    //   }
    onPlayPause: (shouldBePlaying: Boolean) -> Unit,
    onSeek: (Double) -> Unit,
    onSkipOp: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current

    // ── Forzar orientación PORTRAIT mientras Cast esté abierto ──
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ── Estado optimista para play/pause ──
    var localIsPlaying by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) { localIsPlaying = isPlaying }

    // ── Estado de drag para la seek bar ──
    var dragPosition by remember { mutableStateOf<Double?>(null) }
    val displayPosition = dragPosition ?: currentPosition

    // ── Animación de pulso para estado "esperando" ──
    val infiniteTransition = rememberInfiniteTransition(label = "cast_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0B2E),
                        Color(0xFF0D0518)
                    )
                )
            )
            // ── Bloquear touch passthrough al reproductor debajo ──
            // SOLO capturar downs que NINGÚN hijo consumió (requireUnconsumed = true).
            // Esto permite que la seek bar y los botones funcionen sin interferencia,
            // pero bloquea el player de abajo cuando se toca un área vacía.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Icono Cast con pulso ──
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(if (isClientConnected) 1f else pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (isClientConnected) Color(0xFF4CAF50).copy(alpha = 0.12f)
                        else NeonPurple.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = if (isClientConnected) Color(0xFF4CAF50) else NeonPurple,
                    modifier = Modifier.size(36.dp)
                )
            }

            // ── Estado de conexión ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isClientConnected) Color(0xFF4CAF50) else Color(0xFFFFC107))
                )
                Text(
                    text = if (isClientConnected) "PC conectado" else "Esperando conexión...",
                    color = if (isClientConnected) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Título + episodio ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (episodeLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episodeLabel,
                        color = NeonPurple,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── URL (solo cuando no hay conexión) ──
            if (!isClientConnected) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Abre en tu navegador:",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonPurple.copy(alpha = 0.1f))
                            .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 28.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = ipAddress,
                            color = NeonPurple,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Solo red WiFi local — nada sale de tu casa",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }

            // ── Seek bar + controles (solo cuando hay video) ──
            if (isClientConnected && duration > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── Seek bar deslizable (tap + drag) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .pointerInput(duration) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    val frac = (down.position.x / size.width)
                                        .toFloat().coerceIn(0f, 1f)
                                    dragPosition = (frac * duration).toDouble()
                                    down.consume()

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()
                                        val f = (change.position.x / size.width)
                                            .toFloat().coerceIn(0f, 1f)
                                        dragPosition = (f * duration).toDouble()
                                        change.consume()
                                        if (change.changedToUp()) break
                                    }

                                    dragPosition?.let { onSeek(it) }
                                    dragPosition = null
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Track (fondo)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        // Fill (progreso)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    (displayPosition / duration)
                                        .toFloat().coerceIn(0f, 1f)
                                )
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeonGradient)
                        )
                    }

                    // ── Tiempos ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(displayPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }

                    // ── Botones de control ──
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retroceder 10s
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    onClick = { onSeek(maxOf(0.0, displayPosition - 10)) }
                                )
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u23EA", color = Color.White, fontSize = 22.sp)
                        }

                        // Play / Pause (estado optimista)
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    onClick = {
                                        localIsPlaying = !localIsPlaying
                                        onPlayPause(localIsPlaying)
                                    }
                                )
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(NeonGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (localIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (localIsPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Saltar OP (+85s)
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    onClick = { onSeek(minOf(duration, displayPosition + 85)) }
                                )
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u23E9", color = Color.White, fontSize = 22.sp)
                        }
                    }
                }
            }

            // ── Botón Detener ──
            // ⚠️ El padre debe implementar onStop para navegar a Details/Home,
            // NO volver al reproductor (evita guardado duplicado).
            //   onStop = {
            //       castServer.sendStopCommand()
            //       castServer.stopServer()
            //       navController.popBackStack(Route.Detail, inclusive = false)
            //   }
            Box(
                modifier = Modifier
                    .tvFocusable(
                        shape = RoundedCornerShape(16.dp),
                        onClick = onStop
                    )
                    .width(140.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Detener",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Advertencia de batería ──
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFC107).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BatteryFull,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Optimización de batería",
                            color = Color(0xFFFFC107),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Desactiva las restricciones de batería para AnimeMew.",
                            color = Color(0xFFFFC107).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    TextButton(onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Text(
                            "Abrir",
                            color = Color(0xFFFFC107),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(s: Double): String {
    if (s <= 0 || s.isNaN()) return "00:00"
    val totalSec = s.toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (h > 0) "$h:${String.format("%02d", m)}:${String.format("%02d", sec)}"
    else "${String.format("%02d", m)}:${String.format("%02d", sec)}"
}
