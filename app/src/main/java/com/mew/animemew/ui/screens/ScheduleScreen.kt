package com.mew.animemew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.viewmodels.ScheduleItem
import com.mew.animemew.ui.viewmodels.ScheduleState
import com.mew.animemew.ui.viewmodels.ScheduleViewModel
import java.time.DayOfWeek

// =========================================================
//  ScheduleScreen — pestaña "Horarios".
//
//  Estructura:
//    - Header con título "Horarios"
//    - TabRow con 2 tabs: "Siguiendo" y "Esta temporada"
//    - LazyColumn con secciones por día de la semana (Lunes, Martes, ...)
//    - Cada sección: header del día + lista de ScheduleItems
//    - Cada item: cover chico (60dp) + título + "E12 · 9:30 AM"
//
//  Lógica de agrupación:
//    - Items ordenados por airingAt (timestamp)
//    - Se agrupan por dayOfWeek en hora Colombia
//    - Solo se muestran días que tienen al menos 1 anime
// =========================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAnimeClick: (Int) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // === HEADER ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
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
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Horarios",
                    style = TextStyle(
                        brush = NeonGradient,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = when (activeTab) {
                        0 -> "Tus animes en emisión"
                        else -> "Animes populares en emisión"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            // Botón refresh
            IconButton(onClick = { viewModel.loadSchedule() }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refrescar",
                    tint = NeonPurple
                )
            }
        }

        // === TABS ===
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = NeonPurple
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { viewModel.setTab(0) },
                text = { Text("Siguiendo", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { viewModel.setTab(1) },
                text = { Text("Esta temporada", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        // === CONTENIDO ===
        when (val s = state) {
            is ScheduleState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonPurple)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando horarios...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            is ScheduleState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Error al cargar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSchedule() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            is ScheduleState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (activeTab) {
                                0 -> "No estás siguiendo animes en emisión"
                                else -> "No hay animes en emisión con horario conocido"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (activeTab) {
                                0 -> "Empezá a ver un anime en emisión para verlo aquí"
                                else -> "Probá de nuevo más tarde o cambiá de pestaña"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            is ScheduleState.Loaded -> {
                ScheduleList(items = s.items, onAnimeClick = onAnimeClick)
            }
        }
    }
}

// =========================================================
//  Lista agrupada por día de la semana
// =========================================================

@Composable
private fun ScheduleList(
    items: List<ScheduleItem>,
    onAnimeClick: (Int) -> Unit
) {
    // Agrupar por día de la semana
    val grouped = items.groupBy { it.dayOfWeek }
    // Ordenar días: Lunes, Martes, ..., Domingo
    val dayOrder = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )
    val orderedDays = dayOrder.filter { it in grouped.keys }

    // Pre-calcular nombres de días en español (fuera del LazyColumn para no recalcular)
    val dayNames = remember {
        dayOrder.associateWith { day ->
            day.getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale("es", "CO")
            ).uppercase()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(orderedDays) { day ->
            val dayItems = grouped[day] ?: return@items
            DaySection(
                dayName = dayNames[day] ?: day.name,
                items = dayItems,
                onAnimeClick = onAnimeClick
            )
        }
    }
}

@Composable
private fun DaySection(
    dayName: String,
    items: List<ScheduleItem>,
    onAnimeClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header del día
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NeonPurple)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "· ${items.size} anime${if (items.size > 1) "s" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Items del día
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                ScheduleItemCard(item = item, onClick = { onAnimeClick(item.anilistId) })
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (item.isFollowing) NeonPurple.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ep ${item.nextEpisode}",
                        color = NeonPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(NeonPurple.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.dateString,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Hora (alineada a la derecha)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.timeString,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                if (item.isFollowing) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Siguiendo",
                        color = NeonPurple,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
