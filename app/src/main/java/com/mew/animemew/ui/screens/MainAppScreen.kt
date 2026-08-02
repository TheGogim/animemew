package com.mew.animemew.ui.screens

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mew.animemew.data.auth.SessionManager
import com.mew.animemew.navigation.BottomNavItem
import com.mew.animemew.ui.components.SyncStatusBadge
import com.mew.animemew.ui.theme.NeonGradient
import com.mew.animemew.ui.theme.NeonPurple

@Composable
fun MainAppScreen(sessionManager: SessionManager) {
    val navController = rememberNavController()

    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Lists,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isPlayerScreen = currentRoute?.startsWith("player/") == true
    val isAuthScreen = currentRoute == "auth"

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useRail = isLandscape && !isPlayerScreen && !isAuthScreen

    Box(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(80.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    items.forEach { item ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    // FIX: quitar scale para evitar temblor en TV
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (isSelected) 1f else 0.6f
                                    }
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = if (isSelected) {
                                        TextStyle(
                                            brush = NeonGradient,
                                            shadow = Shadow(
                                                color = NeonPurple.copy(alpha = 0.8f),
                                                blurRadius = 15f
                                            )
                                        )
                                    } else {
                                        TextStyle.Default
                                    },
                                    fontSize = 10.sp
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = Color.Transparent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Home.route,
                    modifier = Modifier.weight(1f)
                ) {
                    ComposableRoutes(navController, sessionManager)
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (!isPlayerScreen && !isAuthScreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding()
                        ) {
                            NavigationBar(
                                modifier = Modifier.height(60.dp),
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                items.forEach { item ->
                                    val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.title,
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = if (isSelected) 1.2f else 1f
                                                    scaleY = if (isSelected) 1.2f else 1f
                                                    alpha = if (isSelected) 1f else 0.85f
                                                }
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                style = if (isSelected) {
                                                    TextStyle(
                                                        brush = NeonGradient,
                                                        shadow = Shadow(
                                                            color = NeonPurple.copy(alpha = 0.8f),
                                                            blurRadius = 15f
                                                        )
                                                    )
                                                } else {
                                                    TextStyle.Default
                                                }
                                            )
                                        },
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = Color.Transparent,
                                            indicatorColor = Color.Transparent,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Home.route,
                    modifier = if (isPlayerScreen || isAuthScreen) Modifier else Modifier.padding(innerPadding)
                ) {
                    ComposableRoutes(navController, sessionManager)
                }
            }
        }

        if (!isPlayerScreen && !isAuthScreen) {
            SyncStatusBadge()
        }
    }
}

private fun NavGraphBuilder.ComposableRoutes(
    navController: NavHostController,
    sessionManager: SessionManager
) {
    composable(BottomNavItem.Home.route) {
        HomeScreen(
            onAnimeClick = { animeId ->
                navController.navigate("details/$animeId")
            },
            onPlayEpisode = { slug, ep, title, coverUrl, total, anilistId, isAiring, nextEpTs ->
                navController.navigate("player/$slug/$ep?title=${Uri.encode(title)}&coverUrl=${Uri.encode(coverUrl)}&total=$total&anilistId=$anilistId&isAiring=$isAiring&nextEpTs=$nextEpTs")
            }
        )
    }
    composable(BottomNavItem.Search.route) {
        SearchScreen(onAnimeClick = { animeId ->
            navController.navigate("details/$animeId")
        })
    }
    composable(BottomNavItem.Lists.route) {
        ListsScreen(onAnimeClick = { animeId ->
            navController.navigate("details/$animeId")
        })
    }
    composable(BottomNavItem.Settings.route) {
        SettingsScreen(
            sessionManager = sessionManager,
            onNavigateToAuth = { navController.navigate("auth") }
        )
    }
    composable("auth") {
        AuthScreen(
            sessionManager = sessionManager,
            onBack = { navController.popBackStack() },
            onAuthSuccess = { navController.popBackStack() }
        )
    }
    composable("details/{animeId}") { backStackEntry ->
        val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
        AnimeDetailScreen(
            animeId = animeId,
            onNavigateBack = { navController.popBackStack() },
            onAnimeClick = { relatedId -> navController.navigate("details/$relatedId") },
            onPlayEpisode = { slug, ep, title, coverUrl, total, anilistId, isAiring, nextEpTs ->
                navController.navigate("player/$slug/$ep?title=${Uri.encode(title)}&coverUrl=${Uri.encode(coverUrl)}&total=$total&anilistId=$anilistId&isAiring=$isAiring&nextEpTs=$nextEpTs")
            }
        )
    }
    composable(
        "player/{slug}/{episode}?title={title}&coverUrl={coverUrl}&total={total}&anilistId={anilistId}&isAiring={isAiring}&nextEpTs={nextEpTs}",
        arguments = listOf(
            androidx.navigation.navArgument("title") { defaultValue = "" },
            androidx.navigation.navArgument("coverUrl") { defaultValue = "" },
            androidx.navigation.navArgument("total") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
            androidx.navigation.navArgument("anilistId") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
            androidx.navigation.navArgument("isAiring") { type = androidx.navigation.NavType.BoolType; defaultValue = false },
            androidx.navigation.navArgument("nextEpTs") { type = androidx.navigation.NavType.LongType; defaultValue = 0L }
        )
    ) { backStackEntry ->
        val slug = backStackEntry.arguments?.getString("slug") ?: ""
        val episode = backStackEntry.arguments?.getString("episode")?.toIntOrNull() ?: 1
        val title = backStackEntry.arguments?.getString("title") ?: ""
        val coverUrl = backStackEntry.arguments?.getString("coverUrl") ?: ""
        val total = backStackEntry.arguments?.getInt("total") ?: 0
        val anilistId = backStackEntry.arguments?.getInt("anilistId") ?: 0
        val isAiring = backStackEntry.arguments?.getBoolean("isAiring") ?: false
        val nextEpTs = backStackEntry.arguments?.getLong("nextEpTs") ?: 0L
        com.mew.animemew.ui.screens.PlayerScreen(
            slug = slug,
            episode = episode,
            title = title,
            coverUrl = coverUrl,
            totalEpisodes = total,
            anilistId = anilistId,
            isAiring = isAiring,
            nextEpisodeTimestamp = nextEpTs,
            onNavigateBack = { navController.popBackStack() },
            onNextEpisode = { nextEp ->
                navController.navigate("player/$slug/$nextEp?title=${Uri.encode(title)}&coverUrl=${Uri.encode(coverUrl)}&total=$total&anilistId=$anilistId&isAiring=$isAiring&nextEpTs=$nextEpTs") {
                    popUpTo("player/{slug}/{episode}?title={title}&coverUrl={coverUrl}&total={total}&anilistId={anilistId}&isAiring={isAiring}&nextEpTs={nextEpTs}") { inclusive = true }
                }
            }
        )
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}
