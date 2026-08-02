package com.mew.animemew.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mew.animemew.data.sync.SyncManager
import com.mew.animemew.data.sync.SyncState
import com.mew.animemew.ui.theme.NeonPurple
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

// =========================================================
//  SyncStatusBadge — indicador flotante discreto en la
//  esquina inferior derecha, encima del bottom nav.
//
//  FIX: usa navigationBarsPadding() dinámico para que se
//  vea en TODOS los dispositivos (gesture nav, 3-button nav,
//  tablets con diferentes alturas de system bar).
//
//  Estados:
//   - LoggedOut → no se muestra
//   - Never     → "☁️ —"
//   - Syncing   → spinner + "sync..."
//   - Success   → "☁️ hace 2 min"
//   - Error     → "⚠️ hace 5 min" (amarillo)
//
//  Tap → diálogo con:
//   - Fecha/hora exacta de la última sync
//   - Botón "Descargar de la nube" (pull)
//   - Botón "Subir a la nube" (push)
// =========================================================

@Composable
fun SyncStatusBadge(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncManager = remember { SyncManager.getInstance(context) }
    val state by syncManager.state.collectAsState()
    var showDetailDialog by remember { mutableStateOf(false) }

    if (state is SyncState.LoggedOut) return

    // Tick cada 30s para refrescar el "hace X min"
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    // FIX: padding dinámico que respeta la altura del bottom nav + system bars.
    // El bottom nav son 60dp + 8dp de margen + navigationBarsPadding()
    // para gesture nav / 3-button nav / tablets.
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(end = 12.dp, bottom = 72.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showDetailDialog = true }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (state) {
                    is SyncState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = NeonPurple
                        )
                        Text(
                            text = "sync…",
                            color = NeonPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is SyncState.Success -> {
                        val ts = (state as SyncState.Success).timestampMs
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatRelativeTime(ts, tick),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is SyncState.Error -> {
                        val err = state as SyncState.Error
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = err.lastSuccessMs?.let { formatRelativeTime(it, tick) } ?: "error",
                            color = Color(0xFFFFB300),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is SyncState.Never -> {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "—",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    // =========================================================
    //  DIÁLOGO DE DETALLE — con 2 botones claros
    // =========================================================
    if (showDetailDialog) {
        val lastMs = when (state) {
            is SyncState.Success -> (state as SyncState.Success).timestampMs
            is SyncState.Error -> (state as SyncState.Error).lastSuccessMs
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = {
                Text("Sincronización en la nube", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    val statusText = when (state) {
                        is SyncState.Syncing -> "Estado: Sincronizando…"
                        is SyncState.Success -> "Estado: Conectado ✓\nÚltima sync: ${formatFullDateTime(lastMs!!)}"
                        is SyncState.Error -> "Estado: Error\n${(state as SyncState.Error).message}\n\nÚltima sync exitosa: ${lastMs?.let { formatFullDateTime(it) } ?: "Nunca"}"
                        is SyncState.Never -> "Estado: Aún no se ha sincronizado"
                        else -> ""
                    }
                    Text(statusText, fontSize = 13.sp, lineHeight = 18.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¿Qué quieres hacer?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = {
                        syncManager.pullAsync()
                        showDetailDialog = false
                    }) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Descargar", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        syncManager.pushAsync()
                        showDetailDialog = false
                    }) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Subir", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// =========================================================
//  HELPERS de formato de tiempo
// =========================================================

private fun formatRelativeTime(timestampMs: Long, tick: Int): String {
    val diffMs = System.currentTimeMillis() - timestampMs
    val diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    return when {
        diffMin < 1 -> "ahora"
        diffMin < 60 -> "hace ${diffMin}min"
        diffMin < 1440 -> "hace ${diffMin / 60}h"
        else -> "hace ${diffMin / 1440}d"
    }
}

private fun formatFullDateTime(timestampMs: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestampMs))
}
