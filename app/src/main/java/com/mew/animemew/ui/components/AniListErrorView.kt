package com.mew.animemew.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mew.animemew.ui.theme.NeonMagenta
import com.mew.animemew.ui.theme.NeonPurple

// =========================================================
//  AniListErrorView — Pantalla/banner cuando AniList está caído
//
//  Mensaje claro para el usuario:
//  - "El servidor de carátulas (AniList) está temporalmente caído"
//  - "No es un problema de tu app ni de tu conexión"
//  - Botón reintentar
// =========================================================

@Composable
fun AniListErrorView(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    compact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "anilist_error")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    if (compact) {
        // Banner compacto para mostrar dentro de una pantalla con contenido
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NeonMagenta.copy(alpha = 0.1f),
                            NeonPurple.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(1.dp, NeonMagenta.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = NeonMagenta,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AniList no disponible",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "El servidor de carátulas está caído. No es un problema de tu app.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NeonPurple
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reintentar", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Pantalla completa centrada
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Icono con pulso
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(NeonMagenta.copy(alpha = 0.12f))
                        .border(1.dp, NeonMagenta.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = NeonMagenta,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Servidor de carátulas no disponible",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "AniList (nuestro proveedor de metadatos) está temporalmente caído por problemas de estabilidad.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Esto no es un problema de tu app ni de tu conexión. Vuelve a intentarlo en unos minutos.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Botón reintentar
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reintentar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
