package com.mew.animemew.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mew.animemew.data.Anime
import com.mew.animemew.ui.components.AnimeCard
import com.mew.animemew.ui.components.tvFocusable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.focusGroup
import com.mew.animemew.ui.viewmodels.HomeViewModel
import com.mew.animemew.ui.viewmodels.HomeSectionState
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.theme.NeonMagenta
import com.mew.animemew.ui.theme.AppBackgroundBrush
import com.mew.animemew.ui.theme.TopBarGlowBrush
import com.mew.animemew.ui.theme.LogoGlowBrush
import com.mew.animemew.ui.theme.SectionAccentBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    onPlayEpisode: (String, Int, String, String, Int, Int, Boolean, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val sections by viewModel.sections.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()

    var showRemoveDialog by remember { mutableStateOf(false) }
    var removeSlug by remember { mutableStateOf("") }
    var removeTitle by remember { mutableStateOf("") }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Quitar de Seguir Viendo", fontWeight = FontWeight.Bold) },
            text = { Text("¿Seguro que quieres quitar '$removeTitle' de Seguir Viendo?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeHistoryEntry(removeSlug)
                    showRemoveDialog = false
                }) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

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
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LogoGlowBrush)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.mew.animemew.R.drawable.animemew_logo),
                            contentDescription = "AnimeMew Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "AnimeMew",
                        style = TextStyle(
                            brush = NeonGradient,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (watchHistory.isNotEmpty()) {
                    item {
                        WatchHistorySection(
                            title = "Seguir Viendo",
                            historyList = watchHistory,
                            onPlayEpisode = onPlayEpisode,
                            onRemove = { slug, title ->
                                removeSlug = slug
                                removeTitle = title
                                showRemoveDialog = true
                            }
                        )
                    }
                }

                items(sections) { section ->
                    if (section.animes.isEmpty() && section.isLoading) {
                        SkeletonCarouselSection()
                    } else if (section.animes.isNotEmpty()) {
                        DynamicCarouselSection(
                            section = section,
                            onAnimeClick = onAnimeClick,
                            onLoadMore = { viewModel.loadMore(section.config.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
            // NUEVO: Banner de anuncios abajo del contenido
            com.mew.animemew.data.ads.BannerAdView()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SectionAccentBrush)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
fun DynamicCarouselSection(
    section: HomeSectionState,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    val isAtEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsInfo = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsInfo - 3 && totalItemsInfo > 0
        }
    }

    LaunchedEffect(isAtEnd) {
        if (isAtEnd && section.hasMore && !section.isLoading) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeader(title = section.config.title)
        // focusGroup: trata toda la fila como una unidad de foco
        // Evita el temblor horizontal al navegar verticalmente entre filas
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
        ) {
            items(section.animes) { anime ->
                AnimeCard(anime = anime, onClick = { onAnimeClick(anime.id) })
            }
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerHighlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val brush = Brush.linearGradient(
        colors = listOf(shimmerColor, shimmerHighlight, shimmerColor),
        start = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim),
        end = androidx.compose.ui.geometry.Offset(translateAnim + 300f, translateAnim + 300f)
    )

    Box(modifier = modifier.background(brush))
}

@Composable
fun SkeletonCarouselSection() {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .width(120.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(5) {
                Column(modifier = Modifier.width(120.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(120.dp, 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun WatchHistorySection(
    title: String,
    historyList: List<com.mew.animemew.data.local.WatchHistoryEntity>,
    onPlayEpisode: (String, Int, String, String, Int, Int, Boolean, Long) -> Unit,
    onRemove: (String, String) -> Unit
) {
    var showWaitingDialog by remember { mutableStateOf(false) }
    var waitingTitle by remember { mutableStateOf("") }

    if (showWaitingDialog) {
        AlertDialog(
            onDismissRequest = { showWaitingDialog = false },
            title = { Text("En espera", fontWeight = FontWeight.Bold) },
            text = { Text("Estás al día con '$waitingTitle'.\nEn espera de próximos episodios.") },
            confirmButton = {
                TextButton(onClick = { showWaitingDialog = false }) {
                    Text("Entendido", color = NeonPurple, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeader(title = title)
        // focusGroup: trata toda la fila como una unidad de foco
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
        ) {
            items(historyList) { history ->
                // FIX: isWaiting ahora usa waitingSinceTimestamp como indicador.
                // - Cuando marcamos "En espera" → waitingSinceTimestamp = now
                // - Cuando AiringController habilita el ep → waitingSinceTimestamp = null
                // Así distinguimos "en espera real" de "episodio recién habilitado que
                // el usuario puede ver".
                val isWaiting = history.isAiring && 
                                history.episodeNumber >= history.totalEpisodes && 
                                history.totalEpisodes > 0 &&
                                history.progressMs == 0L &&
                                history.waitingSinceTimestamp != null

                WatchHistoryCard(
                    history = history,
                    isWaiting = isWaiting,
                    onClick = {
                        if (isWaiting) {
                            waitingTitle = history.title
                            showWaitingDialog = true
                        } else {
                            onPlayEpisode(
                                history.animeSlug,
                                history.episodeNumber,
                                history.title,
                                history.coverUrl,
                                history.totalEpisodes,
                                history.anilistId,
                                history.isAiring,
                                history.nextEpisodeTimestamp ?: 0L
                            )
                        }
                    },
                    onRemove = { onRemove(history.animeSlug, history.title) }
                )
            }
        }
    }
}

@Composable
fun WatchHistoryCard(
    history: com.mew.animemew.data.local.WatchHistoryEntity,
    isWaiting: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .tvFocusable(shape = RoundedCornerShape(8.dp), scale = 1.0f, onClick = onClick)
            .width(160.dp)
            .height(100.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            coil.compose.AsyncImage(
                model = history.coverUrl,
                contentDescription = history.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remover",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = history.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val episodeText = if (isWaiting) {
                    "En espera"
                } else if (history.seasonIndex > 0) {
                    "T${history.seasonIndex} E${history.episodeNumber}"
                } else {
                    "Episodio ${history.episodeNumber}"
                }

                val textColor = if (isWaiting) Color(0xFFFFD700) else NeonPurple

                Text(
                    text = episodeText,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )

                if (!isWaiting) {
                    val progress = if (history.durationMs > 0) history.progressMs.toFloat() / history.durationMs else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp).clip(RoundedCornerShape(2.dp)),
                        color = NeonPurple,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}
