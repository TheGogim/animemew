package com.mew.animemew.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // =========================================================
    //  ICONOS MODERNIZADOS:
    //  - Variantes `Rounded` (más suaves y modernas que `Filled`)
    //  - "Listas" ahora usa `VideoLibrary` para coincidir con el
    //    icono del header de ListsScreen (consistencia visual).
    //  - Las letras/gradientes/sombras del bottom bar NO se tocan;
    //    eso vive en MainAppScreen.kt y ya quedó moderno.
    //  - Como el bottom bar es centralizado (se define una sola
    //    vez en MainAppScreen.kt), este cambio se refleja
    //    automáticamente en TODAS las pestañas: Inicio, Buscar,
    //    Listas y Ajustes. Ninguna se queda con el look viejo.
    // =========================================================
    object Home : BottomNavItem("home", "Inicio", Icons.Rounded.Home, Icons.Outlined.Home)
    object Search : BottomNavItem("search", "Buscar", Icons.Rounded.Search, Icons.Outlined.Search)
    object Lists : BottomNavItem("lists", "Listas", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary)
    object Settings : BottomNavItem("settings", "Ajustes", Icons.Rounded.Settings, Icons.Outlined.Settings)
}
