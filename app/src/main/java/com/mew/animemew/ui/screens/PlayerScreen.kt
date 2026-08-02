@file:OptIn(ExperimentalMaterial3Api::class)

package com.mew.animemew.ui.screens

import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mew.animemew.ui.components.tvFocusable
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.viewmodels.PlayerState
import com.mew.animemew.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.delay
import com.mew.animemew.ui.components.KeepScreenOn

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    slug: String,
    episode: Int,
    title: String = "",
    coverUrl: String = "",
    totalEpisodes: Int = 0,
    anilistId: Int = 0,
    isAiring: Boolean = false,
    nextEpisodeTimestamp: Long = 0L,
    onNavigateBack: () -> Unit,
    onNextEpisode: ((Int) -> Unit)? = null,
    viewModel: PlayerViewModel = viewModel(
        viewModelStoreOwner = LocalViewModelStoreOwner.current
            ?: throw IllegalStateException("No ViewModelStoreOwner")
    )
) {
    KeepScreenOn()
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val nextEpisodeInfo by viewModel.nextEpisodeInfo.collectAsState()
    val currentData by viewModel.currentData.collectAsState()
    val currentLabel by viewModel.currentLabel.collectAsState()

    // NUEVO: observar estado de adblocker
    val showAdblockDialog by viewModel.showAdblockDialog.collectAsState()
    val forceCloseApp by viewModel.forceCloseApp.collectAsState()

    // NUEVO: forzar cierre de app después de 4 intentos
    LaunchedEffect(forceCloseApp) {
        if (forceCloseApp) {
            Log.w("PlayerScreen", "🚫 Forzando cierre de app por adblocker")
            (context as? android.app.Activity)?.let { activity ->
                activity.finishAffinity()
            }
            kotlinx.coroutines.delay(200)
            System.exit(0)
        }
    }

    // NUEVO: Pasar la Activity al ViewModel para que pueda mostrar interstitials
    DisposableEffect(Unit) {
        viewModel.setActivity(context as? android.app.Activity)
        onDispose {
            viewModel.setActivity(null)
        }
    }

    // FIX CRÍTICO: Setear orientación UNA sola vez al entrar al player.
    //
    // El problema de la rotación loca en tablet era causado porque el
    // AndroidManifest NO tenía configChanges (ya lo agregaste).
    // Ahora con configChanges, Android NO recrea la Activity al rotar,
    // así que este seteo se hace una sola vez y se mantiene.
    //
    // Usamos un flag simple para asegurar que solo se setea UNA vez.
    val orientationSet = remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!orientationSet.value) {
            (context as? ComponentActivity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            orientationSet.value = true
        }
    }

    // Al salir del player (onDispose del composable), restaurar orientación
    DisposableEffect(Unit) {
        onDispose {
            (context as? ComponentActivity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(slug, episode) {
        viewModel.loadEpisode(slug, episode, title, coverUrl, totalEpisodes, anilistId, isAiring, nextEpisodeTimestamp)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                (context as? ComponentActivity)?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val exoPlayer = remember {
        // LoadControl personalizado para evitar pausas a los 4-6s
        // - minBufferMs: 15s de buffer mínimo (default 50s a veces es excesivo para streams lentos)
        // - maxBufferMs: 50s de buffer máximo
        // - bufferForPlaybackMs: 2.5s necesarios para arrancar playback (default)
        // - bufferForPlaybackAfterRebufferMs: 5s necesarios tras rebuffer (más alto = más estable)
        // - backBufferMs: 10s de buffer hacia atrás (para seeking)
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ 15_000,
                /* maxBufferMs= */ 50_000,
                /* bufferForPlaybackMs= */ 2_500,
                /* bufferForPlaybackAfterRebufferMs= */ 5_000
            )
            .setBackBuffer(
                /* backBufferMs= */ 10_000,
                /* retainBackBufferFromKeyframe= */ true
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    val currentStreamUrl = (state as? PlayerState.Playing)?.streamUrl
    LaunchedEffect(currentStreamUrl) {
        val playingState = state as? PlayerState.Playing ?: return@LaunchedEffect
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; AnimeMew) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15_000)   // 15s para conectar
            .setReadTimeoutMs(30_000)      // 30s para leer datos
            .setAllowCrossProtocolRedirects(true)  // permitir redirects http→https
            .apply {
                playingState.referer?.let { ref ->
                    setDefaultRequestProperties(mapOf("Referer" to ref))
                }
            }
        val mediaItem = MediaItem.fromUri(Uri.parse(playingState.streamUrl))
        val mediaSource = if (playingState.isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(mediaSource)
        if (playingState.startPositionMs > 0 && exoPlayer.currentPosition == 0L) {
            exoPlayer.seekTo(playingState.startPositionMs)
        }
        exoPlayer.prepare()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (state) {
            is PlayerState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = NeonPurple)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Resolviendo el mejor servidor...", color = Color.White)
                }
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(16.dp).statusBarsPadding()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
            }
            is PlayerState.Error -> {
                val errorState = state as PlayerState.Error
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(64.dp))
                    Text(
                        text = errorState.message,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) {
                        Text("Regresar")
                    }
                }
            }
            is PlayerState.Playing -> {
                val cd = currentData
                ExoPlayerView(
                    exoPlayer = exoPlayer,
                    onNavigateBack = onNavigateBack,
                    slug = cd?.slug ?: slug,
                    episode = cd?.episode ?: episode,
                    title = cd?.title ?: title,
                    coverUrl = cd?.coverUrl ?: coverUrl,
                    totalEpisodes = cd?.totalEpisodes ?: totalEpisodes,
                    state = state,
                    currentEpisodeLabel = currentLabel,
                    onSelectServer = { viewModel.selectServer(it, exoPlayer.currentPosition) },
                    nextEpisodeLabel = nextEpisodeInfo?.label,
                    onPlayNext = { viewModel.playNext() },
                    onSaveProgress = { progress, total ->
                        viewModel.saveProgress(progress, total)
                    },
                    onSaveCastProgress = { progress, total ->
                        viewModel.saveCastProgress(progress, total)
                    }
                )
            }
        }

        // NUEVO: Diálogo de Adblocker detectado
        if (showAdblockDialog) {
            AdblockDialog(
                onAlreadyDisabled = {
                    viewModel.onUserDisabledAdblocker()
                },
                onDismiss = {
                    viewModel.onAdblockDialogDismiss()
                    onNavigateBack()
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  COLORES PREMIUM
// ═══════════════════════════════════════════════════════════════════════
private val PremiumPurple = Color(0xFF8B5CF6)
private val PremiumPurpleLight = Color(0xFFA78BFA)
private val PremiumPurpleDark = Color(0xFF6D28D9)
private val PremiumCyan = Color(0xFF06B6D4)
private val GlassDark = Color(0xFF0A0A0F)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8CC)
private val SliderTrackBg = Color(0xFF3A3A4A)

private fun formatTime(ms: Long): String {
    if (ms <= 0 || ms > 24 * 3600 * 1000L) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
           else String.format("%02d:%02d", m, s)
}

private fun formatSlugTitle(slug: String): String {
    return slug.split("-").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    exoPlayer: ExoPlayer,
    onNavigateBack: () -> Unit,
    slug: String,
    episode: Int,
    title: String,
    coverUrl: String,
    totalEpisodes: Int,
    state: PlayerState,
    currentEpisodeLabel: String,
    onSelectServer: (com.mew.animemew.scraper.ServerInfo) -> Unit,
    nextEpisodeLabel: String?,
    onPlayNext: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit,
    onSaveCastProgress: (Long, Long) -> Unit  // NUEVO v10
) {
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPercentage by remember { mutableIntStateOf(0) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekBarValue by remember { mutableFloatStateOf(0f) }
    var showServerSheet by remember { mutableStateOf(false) }
    // NUEVO: modo zoom (Crunchyroll-style) — persiste entre episodios
    val context = LocalContext.current
    val zoomPrefs = remember { context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE) }
    var isZoomMode by remember { mutableStateOf(zoomPrefs.getBoolean("isZoomMode", false)) }
    val playerViewRef = remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }

    // NUEVO v10: Cast
    var showCastDialog by remember { mutableStateOf(false) }
    var castServerUrl by remember { mutableStateOf("animemew.local:8080") }
    var castIpAddress by remember { mutableStateOf("0.0.0.0:8080") }
    var castClientConnected by remember { mutableStateOf(false) }
    var castPosition by remember { mutableStateOf(0.0) }
    var castDuration by remember { mutableStateOf(0.0) }
    var castIsPlaying by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) { onPlayNext() }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    LaunchedEffect(showControls) {
        window?.let { win ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, view)
            if (showControls) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            window?.let { win ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isUserSeeking) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                bufferedPercentage = exoPlayer.bufferedPercentage
                if (totalDuration > 0) {
                    seekBarValue = currentPosition.toFloat() / totalDuration.toFloat()
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            delay(5000)
            if (totalDuration > 0) { onSaveProgress(currentPosition, totalDuration) }
        }
    }

    LaunchedEffect(showControls, isUserSeeking, isPlaying) {
        if (showControls && !isUserSeeking && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // NUEVO: FocusRequester para forzar el foco en el Box principal
    // cuando los controles están ocultos. Esto garantiza que el D-Pad
    // siempre tenga un destino que reciba los eventos.
    val boxFocusRequester = remember { FocusRequester() }

    // NUEVO v9.1: FocusRequester para el botón Play/Pause
    // Cuando los controles se muestran vía D-Pad, el foco va aquí
    // (no al botón Back que saca al usuario del player)
    val playPauseFocusRequester = remember { FocusRequester() }

    // Cuando se ocultan los controles, pedir foco para el Box
    // Cuando se muestran los controles, pedir foco para Play/Pause
    LaunchedEffect(showControls) {
        if (!showControls) {
            try {
                boxFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            // Esperar un frame para que los controles se rendericen
            kotlinx.coroutines.delay(100)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // NUEVO: Box principal con onPreviewKeyEvent para revivir controles con D-Pad
    // y pointerInput para revivirlos con tap/click en cualquier parte
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (!showControls) {
                        showControls = true
                        return@onPreviewKeyEvent true  // consumir para que no haga otra cosa
                    }
                }
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (isZoomMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = false
                    isFocusableInTouchMode = false
                    playerViewRef.value = this
                }
            },
            update = { view ->
                view.resizeMode = if (isZoomMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = PremiumPurpleLight, strokeWidth = 3.dp, modifier = Modifier.size(56.dp)
                )
            }
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.7f),
                                0.15f to Color.Black.copy(alpha = 0.0f),
                                0.75f to Color.Black.copy(alpha = 0.0f),
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Atrás
                    Box(
                        modifier = Modifier
                            .tvFocusable(shape = CircleShape, onClick = onNavigateBack)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatSlugTitle(slug), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val label = if (currentEpisodeLabel.isNotBlank()) currentEpisodeLabel else "Episodio $episode"
                        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // Botón Servidores
                    if (state is PlayerState.Playing) {
                        val playingState = state as PlayerState.Playing
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // NUEVO v10: Botón Cast
                            Box(
                                modifier = Modifier
                                    .tvFocusable(shape = CircleShape, onClick = {
                                        // Iniciar/detener cast
                                        if (com.mew.animemew.cast.CastServer.instance == null) {
                                            val server = com.mew.animemew.cast.CastServer(context)
                                            com.mew.animemew.cast.CastServer.instance = server

                                            // Obtener IP local
                                            val ip = try {
                                                val wifiMan = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                                                val ipInt = wifiMan.connectionInfo.ipAddress
                                                "${(ipInt and 0xFF)}.${(ipInt shr 8 and 0xFF)}.${(ipInt shr 16 and 0xFF)}.${(ipInt shr 24 and 0xFF)}"
                                            } catch (e: Exception) { "0.0.0.0" }

                                            castServerUrl = "animemew.local:8080"
                                            castIpAddress = "$ip:8080"

                                            // Callbacks
                                            server.onClientConnected = { castClientConnected = true }
                                            server.onClientDisconnected = { castClientConnected = false }
                                            server.onPositionUpdate = { currentTime, duration ->
                                                castPosition = currentTime
                                                castDuration = duration
                                                // Usar el estado real del navegador (play/pause) reportado por el server
                                                castIsPlaying = server.browserIsPlaying
                                                onSaveCastProgress((currentTime * 1000).toLong(), (duration * 1000).toLong())
                                            }

                                            server.startServer()

                                            // Enviar stream actual CON posición inicial
                                            server.sendPlayCommand(
                                                playingState.streamUrl,
                                                playingState.referer,
                                                playingState.isHls,
                                                exoPlayer.currentPosition / 1000.0  // posición inicial
                                            )

                                            // Pausar ExoPlayer local
                                            exoPlayer.pause()
                                        }
                                        showCastDialog = true
                                    })
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Cast,
                                    "Cast a PC",
                                    tint = if (castClientConnected) PremiumCyan else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .tvFocusable(shape = RoundedCornerShape(24.dp), onClick = { showServerSheet = true })
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Brush.horizontalGradient(listOf(PremiumPurpleDark.copy(alpha = 0.6f), PremiumPurple.copy(alpha = 0.6f))))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.VideoSettings, "Servidores", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(playingState.currentServerName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // CENTER: Rewind / Play / Forward
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // -10s
                    Box(
                        modifier = Modifier
                            .tvFocusable(shape = CircleShape, onClick = { exoPlayer.seekTo(maxOf(0L, exoPlayer.currentPosition - 10_000)) })
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Replay10, "Retroceder 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    // Play/Pause
                    Box(
                        modifier = Modifier
                            .tvFocusable(
                                shape = CircleShape,
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                focusRequester = playPauseFocusRequester  // NUEVO v9.1
                            )
                            .size(72.dp)
                            .shadow(elevation = 16.dp, shape = CircleShape, ambientColor = PremiumPurple, spotColor = PremiumPurple)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(PremiumPurpleLight, PremiumPurpleDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }

                    // +10s
                    Box(
                        modifier = Modifier
                            .tvFocusable(shape = CircleShape, onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10_000) })
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Forward10, "Avanzar 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // SKIP OP
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 100.dp)
                        .tvFocusable(shape = RoundedCornerShape(28.dp), onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 85_000) })
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(PremiumCyan.copy(alpha = 0.85f), PremiumPurple.copy(alpha = 0.85f))))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Saltar OP", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                            Text("+1:25", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // NEXT EPISODE
                if (nextEpisodeLabel != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 100.dp)
                            .tvFocusable(shape = RoundedCornerShape(28.dp), onClick = onPlayNext)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.horizontalGradient(listOf(PremiumPurple.copy(alpha = 0.85f), PremiumPurpleDark.copy(alpha = 0.85f))))
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Siguiente", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                                Text(nextEpisodeLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // BOTTOM BAR
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPosition), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(formatTime(totalDuration), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // NUEVO: Slider + botón zoom en la misma fila
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PlayerSlider(
                                value = seekBarValue,
                                bufferedValue = bufferedPercentage / 100f,
                                onValueChange = { newValue -> isUserSeeking = true; seekBarValue = newValue },
                                onValueChangeFinished = {
                                    val newPos = (seekBarValue * totalDuration).toLong()
                                    exoPlayer.seekTo(newPos)
                                    isUserSeeking = false
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // NUEVO: Botón Zoom (Crunchyroll-style) al lado de la barra
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        isZoomMode = !isZoomMode
                                        zoomPrefs.edit().putBoolean("isZoomMode", isZoomMode).apply()
                                    }
                                )
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isZoomMode) NeonPurple.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AspectRatio,
                                contentDescription = "Modo zoom",
                                tint = if (isZoomMode) NeonPurple else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showServerSheet && state is PlayerState.Playing) {
            val playingState = state as PlayerState.Playing
            ServerSelectorSheet(
                servers = playingState.availableServers,
                currentServerName = playingState.currentServerName,
                onSelectServer = onSelectServer,
                onDismiss = { showServerSheet = false }
            )
        }

        // NUEVO v10: CastScreen (pantalla completa de Cast)
        if (showCastDialog) {
            com.mew.animemew.cast.CastScreen(
                title = title,
                episodeLabel = currentEpisodeLabel,
                serverUrl = castServerUrl,
                ipAddress = castIpAddress,
                isClientConnected = castClientConnected,
                currentPosition = castPosition,
                duration = castDuration,
                isPlaying = castIsPlaying,
                // API v3: el callback recibe el estado DESEADO (true=resume, false=pause)
                // Esto permite que el icono cambie inmediatamente (estado optimista en CastScreen)
                onPlayPause = { shouldBePlaying ->
                    com.mew.animemew.cast.CastServer.instance?.let { server ->
                        if (shouldBePlaying) {
                            server.sendResumeCommand()
                            castIsPlaying = true
                        } else {
                            server.sendPauseCommand()
                            castIsPlaying = false
                        }
                    }
                },
                onSeek = { position ->
                    com.mew.animemew.cast.CastServer.instance?.sendSeekCommand(position)
                    castPosition = position
                },
                onSkipOp = {
                    val newPos = (castPosition + 85).coerceAtMost(castDuration)
                    com.mew.animemew.cast.CastServer.instance?.sendSeekCommand(newPos)
                    castPosition = newPos
                },
                onStop = {
                    // ⚠️ IMPORTANTE: NO usar showCastDialog = false aquí.
                    // Eso devolvería al reproductor, y como el player sigue "activo"
                    // por debajo, podría guardar el progreso del episodio por
                    // SEGUNDA vez (una del Cast y otra del player al retomar) — bug de
                    // guardado duplicado.
                    //
                    // En su lugar, enviamos stop al navegador, detenemos el server,
                    // y navegamos ATRÁS (sale del player completamente, vuelve a Details).
                    com.mew.animemew.cast.CastServer.instance?.let { server ->
                        server.sendStopCommand()
                        Thread.sleep(300)
                        server.stopServer()
                    }
                    com.mew.animemew.cast.CastServer.instance = null
                    castClientConnected = false
                    castPosition = 0.0
                    castDuration = 0.0
                    castIsPlaying = false
                    showCastDialog = false
                    // Navegar atrás — sale del PlayerScreen del todo
                    onNavigateBack()
                }
            )
        }
    }
}

// =========================================================
//  AdblockDialog — Diálogo cuando se detecta adblocker
//  NUEVO v9.1: Diseño personalizado con gradiente neón
// =========================================================
@Composable
private fun AdblockDialog(
    onAlreadyDisabled: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0B2E),
                            Color(0xFF0D0518)
                        )
                    )
                )
                .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icono con glow
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NeonPurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Bloqueador de anuncios",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Detectamos que tienes un bloqueador de anuncios activo.\n\n" +
                           "Los anuncios mantienen AnimeMew gratis para todos.\n\n" +
                           "Desactívalo para reproducir este episodio.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botón "Ya lo desactivé" (gradiente neón)
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            shape = RoundedCornerShape(16.dp),
                            onClick = onAlreadyDisabled
                        )
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ya lo desactivé",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón "Cerrar" (gris sutil)
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            shape = RoundedCornerShape(16.dp),
                            onClick = onDismiss
                        )
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cerrar",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSelectorSheet(
    servers: List<com.mew.animemew.scraper.ServerInfo>,
    currentServerName: String,
    onSelectServer: (com.mew.animemew.scraper.ServerInfo) -> Unit,
    onDismiss: () -> Unit
) {
    // NUEVO v9.0: separar servidores por idioma
    val latServers = servers.filter { it.language == "lat" }
    val subServers = servers.filter { it.language != "lat" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(modifier = Modifier.padding(vertical = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TextSecondary.copy(alpha = 0.4f)))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Seleccionar servidor", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${servers.size} servidores disponibles", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // NUEVO: Sección LATINO
                if (latServers.isNotEmpty()) {
                    item {
                        Text(
                            "🎬 LATINO (${latServers.size})",
                            color = NeonPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(latServers) { server ->
                        ServerRow(server, currentServerName, onSelectServer, onDismiss)
                    }
                }

                // NUEVO: Sección SUBTITULADO
                if (subServers.isNotEmpty()) {
                    item {
                        Text(
                            "📝 SUBTITULADO (${subServers.size})",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (latServers.isNotEmpty()) 16.dp else 8.dp, bottom = 4.dp)
                        )
                    }
                    items(subServers) { server ->
                        ServerRow(server, currentServerName, onSelectServer, onDismiss)
                    }
                }
            }
        }
    }
}

// NUEVO v9.0: Row reutilizable para servidores
@Composable
private fun ServerRow(
    server: com.mew.animemew.scraper.ServerInfo,
    currentServerName: String,
    onSelectServer: (com.mew.animemew.scraper.ServerInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val isSelected = server.name == currentServerName
    // NUEVO v9.1: rayito ⚡ para servers rápidos (solo subtitulados)
    val isFastServer = server.language != "lat" &&
        com.mew.animemew.data.local.ServerCache.FAST_SERVERS.contains(server.name.lowercase())

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(
            if (isSelected) Brush.horizontalGradient(listOf(PremiumPurpleDark, PremiumPurple))
            else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.02f)))
        ).clickable { onSelectServer(server); onDismiss() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.name, color = if (isSelected) Color.White else TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        // NUEVO v9.1: rayito para servers rápidos
        if (isFastServer) {
            Text(
                text = "⚡",
                color = PremiumCyan,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        if (isSelected) { Icon(Icons.Filled.Check, "Seleccionado", tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun PlayerSlider(
    value: Float, bufferedValue: Float,
    onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit
) {
    val animatedValue by animateFloatAsState(targetValue = value, animationSpec = tween(300), label = "slider_anim")
    val safeBuffered = bufferedValue.coerceIn(0f, 1f)

    // Estado local para saber si el slider tiene el foco (D-Pad)
    var isSliderFocused by remember { mutableStateOf(false) }
    // Estado local temporal mientras el usuario hace seek con D-Pad
    var localSeekValue by remember { mutableStateOf<Float?>(null) }
    // Contador de actividad para auto-confirmar
    var seekActivityTick by remember { mutableStateOf(0) }
    val displayValue = localSeekValue ?: animatedValue

    // Auto-confirmar seek después de 800ms sin actividad en el D-Pad
    LaunchedEffect(seekActivityTick) {
        if (localSeekValue != null) {
            kotlinx.coroutines.delay(800)
            onValueChangeFinished()
            localSeekValue = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            // Hacer el Box focusable para que reciba el D-Pad
            .focusable()
            .onFocusChanged { isSliderFocused = it.isFocused }
            // Manejo de D-Pad izquierda/derecha para seek
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val current = localSeekValue ?: value
                    when (event.key) {
                        Key.DirectionLeft -> {
                            // Retroceder 5% del total
                            val newValue = (current - 0.05f).coerceIn(0f, 1f)
                            localSeekValue = newValue
                            onValueChange(newValue)
                            seekActivityTick++
                            true  // consumir
                        }
                        Key.DirectionRight -> {
                            // Avanzar 5%
                            val newValue = (current + 0.05f).coerceIn(0f, 1f)
                            localSeekValue = newValue
                            onValueChange(newValue)
                            seekActivityTick++
                            true  // consumir
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            // Confirmar seek inmediatamente
                            if (localSeekValue != null) {
                                onValueChangeFinished()
                                localSeekValue = null
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Track de fondo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSliderFocused) 6.dp else 4.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
                .background(SliderTrackBg)
        )
        // Buffered
        Box(
            modifier = Modifier
                .fillMaxWidth(safeBuffered)
                .height(if (isSliderFocused) 6.dp else 4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        )
        // Progreso actual
        Box(
            modifier = Modifier
                .fillMaxWidth(displayValue.coerceIn(0f, 1f))
                .height(if (isSliderFocused) 6.dp else 4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isSliderFocused) Brush.horizontalGradient(listOf(PremiumCyan, PremiumPurpleLight))
                    else Brush.horizontalGradient(listOf(PremiumPurpleLight, PremiumCyan))
                )
        )
        // Thumb (círculo blanco) - más grande cuando está enfocado
        Box(
            modifier = Modifier
                .fillMaxWidth(displayValue.coerceIn(0f, 1f))
                .height(28.dp)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSliderFocused) 18.dp else 14.dp)
                    .shadow(
                        elevation = if (isSliderFocused) 12.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = PremiumPurple,
                        spotColor = PremiumPurple
                    )
                    .clip(CircleShape)
                    .background(if (isSliderFocused) PremiumCyan else Color.White)
            )
        }
        // Slider invisible para capturar touch en móvil/tablet
        Slider(
            value = displayValue.coerceIn(0f, 1f),
            onValueChange = { newValue ->
                localSeekValue = newValue
                onValueChange(newValue)
                seekActivityTick++
            },
            onValueChangeFinished = {
                onValueChangeFinished()
                localSeekValue = null
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}
