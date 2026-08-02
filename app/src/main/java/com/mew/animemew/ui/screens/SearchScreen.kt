package com.mew.animemew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusGroup
import com.mew.animemew.ui.components.AnimeCard
import com.mew.animemew.ui.components.tvFocusVisual
import com.mew.animemew.ui.theme.NeonPurple
import com.mew.animemew.ui.theme.AppBackgroundBrush
import com.mew.animemew.ui.theme.TopBarGlowBrush
import com.mew.animemew.ui.viewmodels.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onAnimeClick: (String) -> Unit,
    viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val allFilters by viewModel.genres.collectAsState()
    var selectedFilters by remember { mutableStateOf(setOf<String>()) }

    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val gridState = rememberLazyGridState()

    val isAtEnd by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsInfo = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsInfo - 4 && totalItemsInfo > 0
        }
    }

    LaunchedEffect(isAtEnd) {
        if (isAtEnd) {
            viewModel.loadMore()
        }
    }

    fun performSearch() {
        focusManager.clearFocus()
        viewModel.search(searchQuery, selectedFilters.toList())
    }

    LaunchedEffect(searchQuery, selectedFilters) {
        kotlinx.coroutines.delay(500) // Debounce 500ms
        viewModel.search(searchQuery, selectedFilters.toList())
    }

    // =========================================================
    //  MISMO FONDO DEL HOME: gradiente AppBackgroundBrush.
    //  El Scaffold queda transparente para que el gradiente se
    //  vea de borde a borde, igual que en la pantalla principal.
    // =========================================================
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TopBarGlowBrush)   // ← mismo glow sutil del Home
                            .statusBarsPadding()
                    ) {
                    // Barra de Búsqueda
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Buscar anime...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = NeonPurple) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    performSearch()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    // Filtros (Chips) múltiples
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón "Todos" para limpiar
                        item {
                            FilterChip(
                                selected = selectedFilters.isEmpty(),
                                onClick = {
                                    selectedFilters = emptySet()
                                    performSearch()
                                },
                                label = { Text("Todos", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonPurple,
                                    disabledContainerColor = Color.Transparent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedFilters.isEmpty(),
                                    borderColor = if (selectedFilters.isEmpty()) NeonPurple else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.tvFocusVisual(
                                    shape = RoundedCornerShape(16.dp),
                                    scale = 1.0f,
                                    borderWidth = 2.dp
                                )
                            )
                        }

                        items(allFilters) { filter ->
                            val isSelected = selectedFilters.contains(filter)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedFilters = if (isSelected) {
                                        selectedFilters - filter // Lo quita si ya estaba
                                    } else {
                                        selectedFilters + filter // Lo añade
                                    }
                                    performSearch()
                                },
                                label = { Text(filter, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonPurple,
                                    disabledContainerColor = Color.Transparent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NeonPurple else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.tvFocusVisual(
                                    shape = RoundedCornerShape(16.dp),
                                    scale = 1.0f,
                                    borderWidth = 2.dp
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Cuadrícula infinita de resultados
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 130.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(searchResults) { anime ->
                    AnimeCard(
                        anime = anime,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAnimeClick(anime.id) }
                    )
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonPurple)
                        }
                    }
                }

                // Espacio en blanco al final para el BottomNav
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
            // NUEVO: Banner de anuncios
            com.mew.animemew.data.ads.BannerAdView()
        }
    }
}
