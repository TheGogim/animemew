package com.mew.animemew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mew.animemew.data.auth.SessionManager
import com.mew.animemew.data.sync.SyncManager
import com.mew.animemew.data.sync.SyncState
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.theme.NeonMagenta
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.AppBackgroundBrush
import com.mew.animemew.ui.theme.TopBarGlowBrush
import com.mew.animemew.ui.theme.SectionAccentBrush
import com.mew.animemew.ui.theme.LogoGlowBrush
import com.mew.animemew.ui.viewmodels.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    sessionManager: SessionManager,
    onNavigateToAuth: () -> Unit
) {
    val selectedTheme by viewModel.currentTheme.collectAsState()
    val selectedLanguage by viewModel.currentLanguage.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLanguageWarning by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf("") }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var policyTitle by remember { mutableStateOf("") }
    var policyContent by remember { mutableStateOf("") }
    val themeOptions = listOf("Sistema", "Oscuro", "Claro")

    val session by sessionManager.session.collectAsState()
    val context = LocalContext.current
    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.state.collectAsState()
    val lastSyncMs by sessionManager.lastSyncMs.collectAsState()

    // Tick cada 30s para refrescar el "hace X min" en el subtitle
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick++
        }
    }

    val displayTheme = when (selectedTheme) {
        "light" -> "Claro"
        "dark" -> "Oscuro"
        else -> "Sistema"
    }

    val displayLanguage = if (selectedLanguage == "lat") "Latino / Castellano" else "Subtitulado"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f),
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TopBarGlowBrush)
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(LogoGlowBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Configuración",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Configuración",
                                style = TextStyle(
                                    brush = NeonGradient,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    letterSpacing = 0.3.sp
                                )
                            )
                            Text(
                                text = "Personaliza tu experiencia",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // =================================================
                    //  SECCIÓN 0: CUENTA (cloud sync)
                    // =================================================
                    item {
                        SettingsSectionHeader("Cuenta")
                        if (session.isLoggedIn) {
                            // --- Sesión activa ---
                            SettingsGroupCard {
                                // Email + estado conectado
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(NeonPurple.copy(alpha = 0.15f))
                                            .border(1.dp, NeonPurple.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDone,
                                            contentDescription = null,
                                            tint = NeonPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.email ?: "Conectado",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1
                                        )
                                        // NUEVO: subtitle dinámico con estado + última sync
                                        val subtitle = when (syncState) {
                                            is SyncState.Syncing -> "Sincronizando…"
                                            is SyncState.Error -> "Error de sync"
                                            else -> {
                                                lastSyncMs?.let {
                                                    val diff = System.currentTimeMillis() - it
                                                    val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                                                    when {
                                                        min < 1 -> "Sincronizado ahora"
                                                        min < 60 -> "Sincronizado hace ${min} min"
                                                        min < 1440 -> "Sincronizado hace ${min / 60} h"
                                                        else -> "Sincronizado hace ${min / 1440} d"
                                                    }
                                                } ?: "Aún no se ha sincronizado"
                                            }
                                        }
                                        val subtitleColor = when (syncState) {
                                            is SyncState.Syncing -> NeonPurple
                                            is SyncState.Error -> Color(0xFFFFB300)
                                            else -> NeonPurple
                                        }
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = subtitleColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                SettingsDivider()
                                // Botón: Sincronizar ahora (FUNCIONA DE VERDAD)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // NUEVO: dispara pull + push real
                                            syncManager.syncNowAsync()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Spinner si está sincronizando, icono si no
                                        if (syncState is SyncState.Syncing) {
                                            CircularProgressIndicator(
                                                color = NeonPurple,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Sync,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sincronizar ahora",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        // NUEVO: fecha exacta de la última sync
                                        lastSyncMs?.let {
                                            Text(
                                                text = "Última: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        } ?: run {
                                            Text(
                                                text = "Toca para descargar y subir tus datos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                SettingsDivider()
                                // Botón: Cerrar sesión (rojo sutil)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLogoutDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Logout,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Cerrar sesión",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            // --- Sin sesión: CTA para iniciar sesión ---
                            SettingsGroupCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAuth() }
                                        .padding(horizontal = 14.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(NeonPurple.copy(alpha = 0.15f))
                                            .border(1.dp, NeonPurple.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = null,
                                            tint = NeonPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Iniciar sesión",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Guarda tus listas y progreso en la nube",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // =================================================
                    //  SECCIÓN 1: APARIENCIA
                    // =================================================
                    item {
                        SettingsSectionHeader("Apariencia")
                        SettingsGroupCard {
                            SettingsRowItem(
                                icon = Icons.Filled.Palette,
                                title = "Tema de la aplicación",
                                subtitle = displayTheme,
                                onClick = { showThemeSheet = true }
                            )
                        }
                    }

                    // =================================================
                    //  SECCIÓN 1.5: IDIOMA DE AUDIO (NUEVO v9.0)
                    // =================================================
                    item {
                        SettingsSectionHeader("Idioma de Audio")
                        SettingsGroupCard {
                            SettingsRowItem(
                                icon = Icons.Filled.RecordVoiceOver,
                                title = "Idioma de audio",
                                subtitle = displayLanguage,
                                onClick = { showLanguageSheet = true }
                            )
                        }
                    }

                    // =================================================
                    //  SECCIÓN 2: POLÍTICAS E INFORMACIÓN
                    // =================================================
                    item {
                        SettingsSectionHeader("Políticas e Información")
                        SettingsGroupCard {
                            SettingsRowItem(
                                icon = Icons.Filled.Security,
                                title = "Privacidad y Cookies",
                                subtitle = "Uso de datos y cookies de YouTube",
                                onClick = {
                                    policyTitle = "Privacidad y Cookies"
                                    policyContent = "Esta aplicación utiliza YouTube iframe para reproducir tráilers. YouTube puede recopilar cookies y datos de uso de acuerdo con sus propias políticas de privacidad al reproducir estos videos."
                                    showPolicyDialog = true
                                }
                            )
                            SettingsDivider()
                            SettingsRowItem(
                                icon = Icons.Filled.Policy,
                                title = "Uso de Inteligencia Artificial",
                                subtitle = "Políticas de uso de IA",
                                onClick = {
                                    policyTitle = "Inteligencia Artificial"
                                    policyContent = "AnimeMew no utiliza algoritmos de IA intrusivos para generar contenido. La información es provista fielmente mediante la integración con AniList y no manipulamos recomendaciones con IA sin transparencia."
                                    showPolicyDialog = true
                                }
                            )
                            SettingsDivider()
                            SettingsRowItem(
                                icon = Icons.Filled.Info,
                                title = "Acerca de AnimeMew",
                                subtitle = "API y versión",
                                onClick = {
                                    policyTitle = "Acerca de"
                                    policyContent = "AnimeMew v8.1-alpha\n\nTodos los datos y metadatos del anime son proporcionados gracias a la excelente API pública de AniList. Todos los derechos pertenecen a sus respectivos dueños."
                                    showPolicyDialog = true
                                }
                            )
                        }
                    }

                    // =================================================
                    //  FOOTER
                    // =================================================
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "AnimeMew",
                                style = TextStyle(
                                    brush = NeonGradient,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "v8.1-alpha",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Datos proporcionados por AniList",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            // NUEVO: Banner de anuncios abajo del contenido
            com.mew.animemew.data.ads.BannerAdView()
        }
    }

    // =========================================================
    //  THEME SELECTOR — ModalBottomSheet
    // =========================================================
    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SectionAccentBrush)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Elegir Tema",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                themeOptions.forEach { theme ->
                    val themeId = when (theme) {
                        "Claro" -> "light"
                        "Oscuro" -> "dark"
                        else -> "system"
                    }
                    val isSelected = selectedTheme == themeId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) NeonPurple.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .clickable {
                                viewModel.setTheme(themeId)
                                showThemeSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val themeIcon = when (theme) {
                            "Claro" -> Icons.Filled.LightMode
                            "Oscuro" -> Icons.Filled.DarkMode
                            else -> Icons.Filled.BrightnessAuto
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) NeonPurple.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = null,
                                tint = if (isSelected) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = theme,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeonPurple else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Seleccionado",
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // =========================================================
    //  NUEVO v9.0: LANGUAGE SELECTOR — ModalBottomSheet
    // =========================================================
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SectionAccentBrush)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Idioma de Audio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val languageOptions = listOf("Subtitulado" to "sub", "Latino / Castellano" to "lat")
                languageOptions.forEach { (displayName, langId) ->
                    val isSelected = selectedLanguage == langId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) NeonPurple.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .clickable {
                                if (langId == "lat" && selectedLanguage != "lat") {
                                    // Mostrar advertencia antes de cambiar a lat
                                    pendingLanguage = langId
                                    showLanguageWarning = true
                                } else {
                                    viewModel.setLanguage(langId)
                                }
                                showLanguageSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val langIcon = if (langId == "lat") Icons.Filled.RecordVoiceOver else Icons.Filled.Subtitles
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) NeonPurple.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = langIcon,
                                contentDescription = null,
                                tint = if (isSelected) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = displayName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeonPurple else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Seleccionado",
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // =========================================================
    //  NUEVO v9.0: DIÁLOGO DE ADVERTENCIA — Lat/Cast
    // =========================================================
    if (showLanguageWarning) {
        AlertDialog(
            onDismissRequest = {
                showLanguageWarning = false
                pendingLanguage = ""
            },
            title = {
                Text("Atención", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Los episodios en latino o castellano pueden tardar más en cargar.\n\n" +
                     "Si un anime no está disponible en latino, se reproducirá en subtitulado automáticamente.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setLanguage(pendingLanguage)
                    showLanguageWarning = false
                    pendingLanguage = ""
                }) {
                    Text("Entendido", color = NeonPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLanguageWarning = false
                    pendingLanguage = ""
                }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // =========================================================
    //  DIÁLOGO: CONFIRMACIÓN DE LOGOUT
    // =========================================================
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("Cerrar sesión", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("¿Seguro que quieres cerrar sesión?\n\nTus datos locales se mantienen en el dispositivo, pero ya no se sincronizarán con la nube hasta que vuelvas a iniciar sesión.")
            },
            confirmButton = {
                TextButton(onClick = {
                    sessionManager.clearSession()
                    showLogoutDialog = false
                }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // =========================================================
    //  DIÁLOGO DE POLÍTICAS
    // =========================================================
    if (showPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = {
                Text(
                    policyTitle,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = NeonGradient)
                )
            },
            text = {
                Text(
                    policyContent,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPolicyDialog = false }) {
                    Text("Entendido", color = NeonPurple, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// =========================================================
//  SECTION HEADER — barra de acento + título en mayúsculas
// =========================================================
@Composable
private fun SettingsSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SectionAccentBrush)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = NeonPurple,
                letterSpacing = 1.2.sp
            )
        )
    }
}

// =========================================================
//  GROUP CARD — contenedor de una sección completa
// =========================================================
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = NeonPurple.copy(alpha = 0.12f)
        )
    ) {
        Column(content = content)
    }
}

// =========================================================
//  ROW ITEM — fila moderna con icono + chevron
// =========================================================
@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NeonPurple.copy(alpha = 0.15f))
                .border(1.dp, NeonPurple.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = NeonPurple,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// =========================================================
//  DIVIDER
// =========================================================
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    )
}
