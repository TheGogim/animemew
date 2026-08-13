package com.mew.animemew.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusGroup
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.mew.animemew.data.AnimeRelation
import com.mew.animemew.ui.components.tvFocusable
import com.mew.animemew.ui.theme.AppBackgroundBrush
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonMagenta
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.theme.SectionAccentBrush
import com.mew.animemew.ui.viewmodels.DetailViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailScreen(
    animeId: String,
    onNavigateBack: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onPlayEpisode: (String, Int, String, String, Int, Int, Boolean, Long) -> Unit = { _, _, _, _, _, _, _, _ -> },
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val animeState by viewModel.animeDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isAniListDown by viewModel.isAniListDown.collectAsState()
    val context = LocalContext.current

    val isFavorite by viewModel.isFavorite.collectAsState()
    val isWatched by viewModel.isWatched.collectAsState()

    LaunchedEffect(animeId) {
        viewModel.loadAnimeDetails(animeId.toIntOrNull() ?: 1)
    }

    val scrollState = rememberScrollState()

    var showAddToListSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val anime = animeState
    if (anime == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackgroundBrush)
        ) {
            if (isAniListDown) {
                // NUEVO: mostrar error view cuando AniList está caído
                com.mew.animemew.ui.components.AniListErrorView(
                    onRetry = { viewModel.retry() }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackgroundBrush)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {

            // =====================================================
            //  1. HERO BANNER (260dp)
            // =====================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                AsyncImage(
                    model = anime.bannerUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color(0xFF1A0B2E)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = NeonPurple.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFFFD700).copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Score",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${anime.score}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD700),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NeonPurple.copy(alpha = 0.18f))
                                    .border(
                                        width = 1.dp,
                                        color = NeonPurple.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Ranking #${anime.ranking}",
                                    color = NeonPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // =====================================================
            //  2. ACTION BAR
            // =====================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón Visto / No Visto (circular)
                val watchedBg = if (isWatched) {
                    Modifier.background(NeonPurple.copy(alpha = 0.2f), CircleShape)
                } else {
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    )
                }
                Box(
                    modifier = Modifier
                        .tvFocusable(shape = CircleShape, onClick = { viewModel.toggleWatched() })
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(watchedBg)
                        .border(
                            width = 1.dp,
                            color = if (isWatched) NeonPurple else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Visto",
                        tint = if (isWatched) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Botón Añadir a Lista (pill ancho con gradiente neón)
                Box(
                    modifier = Modifier
                        .tvFocusable(shape = RoundedCornerShape(50), onClick = { showAddToListSheet = true })
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(NeonGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Añadir a lista",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Añadir a Lista",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // =====================================================
            //  3. META INFO (Formato, Estado, Año, Temporadas)
            // =====================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoItem(label = "Formato", value = anime.type, modifier = Modifier.weight(1f))
                val statusText = when(anime.status) {
                    "RELEASING" -> "En emisión"
                    "FINISHED" -> "Finalizado"
                    "NOT_YET_RELEASED" -> "Próximamente"
                    "CANCELLED" -> "Cancelado"
                    else -> anime.status
                }
                InfoItem(label = "Estado", value = statusText, modifier = Modifier.weight(1f))
                InfoItem(label = "Año", value = anime.releaseYear.toString(), modifier = Modifier.weight(1f))
                InfoItem(label = "Temporadas", value = anime.seasons.toString(), modifier = Modifier.weight(1f))
            }

            // =====================================================
            //  NUEVO: Mostrar fecha de próximo episodio si está en emisión
            // =====================================================
            if (anime.status == "RELEASING" && anime.nextEpisodeTimestamp != null) {
                val sdf = java.text.SimpleDateFormat("d 'de' MMMM", java.util.Locale("es"))
                val dateStr = sdf.format(java.util.Date(anime.nextEpisodeTimestamp * 1000))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurple.copy(alpha = 0.15f))
                        .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Próximo Ep ${anime.nextEpisodeNumber} · $dateStr",
                        color = NeonPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // =====================================================
            //  4. GÉNEROS
            // =====================================================
            LazyRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(anime.genres) { genre ->
                    // NUEVO: tvFocusable en géneros
                    Box(
                        modifier = Modifier
                            .tvFocusable(shape = RoundedCornerShape(50), scale = 1.0f, onClick = {})
                            .clip(RoundedCornerShape(50))
                            .background(NeonPurple.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = NeonPurple.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = genre,
                            fontSize = 12.sp,
                            color = NeonPurple,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // =====================================================
            //  5. SINOPSIS
            // =====================================================
            DetailSectionHeader("Sinopsis")
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = anime.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Text(
                    text = if (isDescriptionExpanded) "Ver menos" else "Leer más",
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .tvFocusable(shape = RoundedCornerShape(4.dp), onClick = { isDescriptionExpanded = !isDescriptionExpanded })
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================
            //  6. TRÁILER
            // =====================================================
            if (anime.trailerYoutubeId != null) {
                DetailSectionHeader("Tráiler Oficial")
                // NUEVO: tvFocusable en el tráiler
                Box(
                    modifier = Modifier
                        .tvFocusable(shape = RoundedCornerShape(16.dp), onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${anime.trailerYoutubeId}"))
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${anime.trailerYoutubeId}"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(webIntent)
                            }
                        })
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${anime.trailerYoutubeId}/hqdefault.jpg",
                        contentDescription = "Tráiler Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(2.dp, NeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir Tráiler",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================
            //  7. RELACIONADOS
            // =====================================================
            if (anime.relations.isNotEmpty()) {
                DetailSectionHeader("Relacionados")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(anime.relations) { relation ->
                        RelationCard(
                            relation = relation,
                            onClick = { onAnimeClick(relation.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =====================================================
            //  8. EPISODIOS / DEPURADOR
            // =====================================================
            val jkanimePage by viewModel.jkanimePage.collectAsState()
            val isScraping by viewModel.isScraping.collectAsState()

            if (isScraping) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else if (jkanimePage != null && jkanimePage!!.totalEpisodes > 0) {
                EpisodesSection(
                    totalEpisodes = jkanimePage!!.totalEpisodes,
                    slug = jkanimePage!!.slug,
                    onEpisodeClick = { ep ->
                        onPlayEpisode(
                            jkanimePage!!.slug,
                            ep,
                            anime.title,
                            anime.coverUrl ?: "",
                            jkanimePage!!.totalEpisodes,
                            anime.id.toIntOrNull() ?: 0,
                            anime.status == "RELEASING",
                            anime.nextEpisodeTimestamp ?: 0L
                        )
                    }
                )
            } else if (!isScraping) {
                // Mostrar el log de scraping o un mensaje específico según el status
                val scrapingLog by viewModel.scrapingLog.collectAsState()
                val isNotReleased = anime.status == "NOT_YET_RELEASED"
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isNotReleased) "Este anime aún no se ha estrenado"
                               else "Episodios no disponibles",
                        color = if (isNotReleased) NeonPurple
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isNotReleased) "No hay episodios disponibles todavía. Vuelve más tarde."
                               else scrapingLog,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // =====================================================
        //  TOP OVERLAY: Back (izq) + Favorite (der)
        // =====================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NUEVO: tvFocusable en botón Back
            Box(
                modifier = Modifier
                    .tvFocusable(shape = CircleShape, onClick = onNavigateBack)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // NUEVO: tvFocusable en botón Favorite
            val favBg = if (isFavorite) {
                Modifier.background(Color.Red.copy(alpha = 0.25f), CircleShape)
            } else {
                Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            }
            Box(
                modifier = Modifier
                    .tvFocusable(shape = CircleShape, onClick = { viewModel.toggleFavorite() })
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(favBg)
                    .border(
                        width = 1.dp,
                        color = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // =========================================================
    //  BOTTOM SHEET: Guardar en lista
    // =========================================================
    if (showAddToListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddToListSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
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
                        "Guardar en Lista",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val listasDisponibles by viewModel.allLists.collectAsState(initial = emptyList())

                listasDisponibles.forEach { lista ->
                    // NUEVO: tvFocusable en items del sheet
                    Row(
                        modifier = Modifier
                            .tvFocusable(shape = RoundedCornerShape(12.dp), onClick = {
                                viewModel.addToList(lista.id)
                                showAddToListSheet = false
                            })
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonPurple.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeonPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            lista.name,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// =========================================================
//  INFO ITEM — card pequeña para la meta info
// =========================================================
@Composable
fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = NeonPurple.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// =========================================================
//  SECTION HEADER — barra de acento + título.
// =========================================================
@Composable
private fun DetailSectionHeader(title: String) {
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

// =========================================================
//  RELATION CARD — card de anime relacionado
// =========================================================
@Composable
fun RelationCard(relation: AnimeRelation, onClick: () -> Unit) {
    // NUEVO: tvFocusable en relation card
    Card(
        modifier = Modifier
            .tvFocusable(shape = RoundedCornerShape(12.dp), scale = 1.0f, onClick = onClick)
            .width(130.dp)
            .height(220.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = relation.coverUrl,
                    contentDescription = relation.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonPurple, NeonMagenta)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = relation.relationType,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = relation.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

// =========================================================
//  EPISODES SECTION
// =========================================================
@Composable
fun EpisodesSection(
    totalEpisodes: Int,
    slug: String,
    onEpisodeClick: (Int) -> Unit
) {
    val chunkSize = 50
    val totalChunks = kotlin.math.ceil(totalEpisodes.toDouble() / chunkSize).toInt()

    var selectedChunkIndex by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
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
                text = "Episodios",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NeonPurple.copy(alpha = 0.18f))
                    .border(
                        width = 1.dp,
                        color = NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$totalEpisodes",
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (totalChunks > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .focusGroup()
            ) {
                items(totalChunks) { index ->
                    val start = index * chunkSize + 1
                    val end = minOf((index + 1) * chunkSize, totalEpisodes)
                    val label = "$start-$end"
                    val isSelected = selectedChunkIndex == index

                    // NUEVO: tvFocusable en tabs de paginación
                    Box(
                        modifier = Modifier
                            .tvFocusable(shape = RoundedCornerShape(8.dp), onClick = { selectedChunkIndex = index })
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) NeonGradient
                                else MaterialTheme.colorScheme.surfaceVariant.toBrush()
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        val startEp = selectedChunkIndex * chunkSize + 1
        val endEp = minOf((selectedChunkIndex + 1) * chunkSize, totalEpisodes)

        val episodesInChunk = (startEp..endEp).toList()

        val columns = 5
        val rows = episodesInChunk.chunked(columns)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusGroup()
        ) {
            for (rowEps in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (ep in rowEps) {
                        // NUEVO: tvFocusable en episodios
                        Box(
                            modifier = Modifier
                                .tvFocusable(shape = RoundedCornerShape(10.dp), onClick = { onEpisodeClick(ep) })
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = NeonPurple.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ep.toString(),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    val emptySpaces = columns - rowEps.size
                    for (i in 0 until emptySpaces) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Helper para convertir Color a Brush
// Usamos linearGradient con un solo color como alternativa portable a solidColor
private fun Color.toBrush(): Brush = Brush.linearGradient(listOf(this, this))
