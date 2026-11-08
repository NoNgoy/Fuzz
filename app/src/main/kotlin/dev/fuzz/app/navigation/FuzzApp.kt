package dev.fuzz.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.feature.connections.ConnectionsRoute
import dev.fuzz.feature.connections.connectionsScreen
import dev.fuzz.feature.home.HomeRoute
import dev.fuzz.feature.home.homeScreen
import dev.fuzz.feature.importer.ImportRoute
import dev.fuzz.feature.importer.importScreen
import dev.fuzz.feature.library.TrackListRoute
import dev.fuzz.feature.library.libraryScreen
import dev.fuzz.feature.library.trackListScreen
import dev.fuzz.feature.onboarding.OnboardingRoute
import dev.fuzz.feature.onboarding.onboardingScreen
import dev.fuzz.feature.player.MiniPlayer
import dev.fuzz.feature.player.PlayerRoute
import dev.fuzz.feature.player.playerScreen
import dev.fuzz.feature.search.searchScreen
import dev.fuzz.feature.settings.SettingsRoute
import dev.fuzz.feature.settings.profileScreen
import dev.fuzz.feature.settings.settingsScreen
import dev.fuzz.feature.wave.WaveRoute
import dev.fuzz.feature.wave.waveScreen

@Composable
fun FuzzApp(
    showOnboarding: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showChrome =
        destination != null &&
            !destination.hasRoute(PlayerRoute::class) &&
            !destination.hasRoute(OnboardingRoute::class)

    Scaffold(
        bottomBar = {
            if (showChrome) {
                Column {
                    MiniPlayer(onExpand = { navController.navigate(PlayerRoute) })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TopLevelDestination.entries.forEach { tab ->
                            val selected = destination.hierarchy.any { it.hasRoute(tab.routeClass) }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(tab.label)) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = FuzzTheme.colors.accentSoft,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (showOnboarding) OnboardingRoute else HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            homeScreen(
                onOpenWaveSettings = { navController.navigate(WaveRoute) },
                onAddMusic = { navController.navigate(OnboardingRoute) },
            )
            searchScreen()
            libraryScreen(
                onOpenLiked = { navController.navigate(TrackListRoute()) },
                onOpenPlaylist = { navController.navigate(TrackListRoute(playlistId = it)) },
            )
            trackListScreen(onBack = navController::popBackStack)
            profileScreen(
                onOpenImport = { navController.navigate(ImportRoute) },
                onOpenConnections = { navController.navigate(ConnectionsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
            waveScreen(onBack = navController::popBackStack)
            importScreen(
                onBack = navController::popBackStack,
                onSaved = { id ->
                    navController.navigate(TrackListRoute(playlistId = id)) {
                        popUpTo<ImportRoute> { inclusive = true }
                    }
                },
            )
            settingsScreen()
            connectionsScreen()
            onboardingScreen(
                onFinished = {
                    navController.navigate(HomeRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
            playerScreen(onCollapse = navController::popBackStack)
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
