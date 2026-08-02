package com.mew.animemew.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mew.animemew.data.version.ApkDownloader
import com.mew.animemew.data.version.VersionInfo
import com.mew.animemew.ui.components.tvFocusable
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple

// =========================================================
//  UpdateRequiredScreen — Pantalla de actualización obligatoria
//
//  2 botones:
//  1. "Actualizar ahora" → descarga APK dentro de la app + instala
//  2. "Abrir en navegador" → fallback para TV o si la descarga falla
// =========================================================

@Composable
fun UpdateRequiredScreen(info: VersionInfo) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val downloader = remember { ApkDownloader(context) }
    val downloadState by downloader.state.collectAsState()
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    // Cuando volvemos de la pantalla de permisos, reintentar descarga
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && permissionRequested) {
                permissionRequested = false
                if (downloader.canInstallPackages()) {
                    // Permiso concedido, iniciar descarga
                    downloader.downloadApk(info.downloadUrl)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono de actualización
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonPurple.copy(alpha = 0.3f),
                                NeonPurple.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = "Actualización",
                    tint = NeonPurple,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Título
            Text(
                text = "Nueva versión ${info.latestVersion} disponible",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Actualización obligatoria",
                color = NeonPurple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Card con la descripción (markdown renderizado)
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    NeonPurple.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                ) {
                    MarkdownRenderer(markdown = info.description)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTÓN 1: Actualizar ahora (descarga + instala dentro de la app)
            UpdateButton(
                text = when (downloadState) {
                    is ApkDownloader.DownloadState.Downloading -> "Descargando..."
                    is ApkDownloader.DownloadState.Installing -> "Instalando..."
                    is ApkDownloader.DownloadState.RequestingPermission -> "Esperando permiso..."
                    is ApkDownloader.DownloadState.Error -> "Reintentar"
                    else -> "Actualizar ahora"
                },
                icon = Icons.Filled.Download,
                enabled = downloadState !is ApkDownloader.DownloadState.Downloading &&
                          downloadState !is ApkDownloader.DownloadState.Installing &&
                          downloadState !is ApkDownloader.DownloadState.RequestingPermission,
                onClick = {
                    if (downloader.canInstallPackages()) {
                        downloader.downloadApk(info.downloadUrl)
                    } else {
                        permissionRequested = true
                        downloader.requestInstallPermission()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // BOTÓN 2: Abrir en navegador (fallback)
            UpdateButton(
                text = "Abrir en navegador",
                icon = Icons.Filled.OpenInBrowser,
                isSecondary = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Estado de descarga
            when (val st = downloadState) {
                is ApkDownloader.DownloadState.Error -> {
                    Text(
                        text = "❌ ${st.message}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                is ApkDownloader.DownloadState.Downloading -> {
                    Text(
                        text = "Descargando APK... Esto puede tardar unos minutos.",
                        color = NeonPurple,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is ApkDownloader.DownloadState.Installing -> {
                    Text(
                        text = "Abriendo instalador... Sigue las instrucciones en pantalla.",
                        color = NeonPurple,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is ApkDownloader.DownloadState.RequestingPermission -> {
                    Text(
                        text = "Concede el permiso de instalar apps desconocidas y vuelve a AnimeMew.",
                        color = NeonPurple,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Texto de ayuda
            Text(
                text = "La app está bloqueada hasta que instales la nueva versión.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }) {
                Text(
                    text = "Si ya instalaste la nueva versión, abre la app nuevamente",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// =========================================================
//  UpdateButton — Botón reutilizable para la pantalla de update
// =========================================================
@Composable
private fun UpdateButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSecondary) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }
    val borderColor = if (isSecondary) {
        Color.White.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }
    val brush = if (isSecondary) null else NeonGradient

    Box(
        modifier = Modifier
            .tvFocusable(
                shape = RoundedCornerShape(28.dp),
                scale = 1.0f,
                onClick = onClick
            )
            .fillMaxWidth(0.7f)
            .height(52.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (brush != null) Modifier.background(brush)
                else Modifier.background(backgroundColor)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (enabled) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================
//  MarkdownRenderer — (igual que antes, sin cambios)
// =========================================================

@Composable
private fun MarkdownRenderer(markdown: String) {
    val lines = markdown.lines()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.isEmpty() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                line.startsWith("## ") -> {
                    val text = line.removePrefix("## ").trim()
                    MarkdownHeading(text = text, level = 2)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                line.startsWith("### ") -> {
                    val text = line.removePrefix("### ").trim()
                    MarkdownHeading(text = text, level = 3)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                line.startsWith("- ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("- ")) {
                        items.add(lines[i].trim().removePrefix("- ").trim())
                        i++
                    }
                    i--
                    MarkdownList(items = items)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    MarkdownParagraph(text = line)
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            i++
        }
    }
}

@Composable
private fun MarkdownHeading(text: String, level: Int) {
    val (fontSize, fontWeight, color) = when (level) {
        2 -> Triple(18.sp, FontWeight.Bold, Color.White)
        3 -> Triple(15.sp, FontWeight.SemiBold, NeonPurple.copy(alpha = 0.9f))
        else -> Triple(14.sp, FontWeight.Medium, Color.White)
    }
    val annotatedString = buildBoldAnnotatedString(text, color)
    Text(
        text = annotatedString,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color
    )
}

@Composable
private fun MarkdownParagraph(text: String) {
    val annotatedString = buildBoldAnnotatedString(text, Color.White.copy(alpha = 0.85f))
    Text(
        text = annotatedString,
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.85f),
        lineHeight = 18.sp
    )
}

@Composable
private fun MarkdownList(items: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (item in items) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "•",
                    color = NeonPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp)
                )
                val annotatedString = buildBoldAnnotatedString(item, Color.White.copy(alpha = 0.85f))
                Text(
                    text = annotatedString,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class TextPart(val text: String, val isBold: Boolean)

private fun parseBoldText(text: String): List<TextPart> {
    val parts = mutableListOf<TextPart>()
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) {
            parts.add(TextPart(text.substring(lastIndex, match.range.first), false))
        }
        parts.add(TextPart(match.groupValues[1], true))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        parts.add(TextPart(text.substring(lastIndex), false))
    }
    return if (parts.isEmpty()) listOf(TextPart(text, false)) else parts
}

private fun buildBoldAnnotatedString(text: String, baseColor: Color): AnnotatedString {
    val parts = parseBoldText(text)
    return buildAnnotatedString {
        for (part in parts) {
            if (part.isBold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                    append(part.text)
                }
            } else {
                withStyle(SpanStyle(color = baseColor)) {
                    append(part.text)
                }
            }
        }
    }
}
