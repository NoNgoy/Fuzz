package dev.fuzz.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.fuzz.app.R
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.domain.download.DownloadEvent
import dev.fuzz.feature.connections.ConnectionsRoute
import dev.fuzz.feature.connections.connectionsScreen
import dev.fuzz.feature.home.HomeRoute
import dev.fuzz.feature.home.homeScreen
import dev.fuzz.feature.importer.ImportRoute
import dev.fuzz.feature.importer.importScreen
import dev.fuzz.feature.library.TrackListRoute
import dev.fuzz.feature.library.WishlistRoute
import dev.fuzz.feature.library.libraryScreen
import dev.fuzz.feature.library.trackListScreen
import dev.fuzz.feature.library.wishlistScreen
import dev.fuzz.feature.onboarding.OnboardingRoute
import dev.fuzz.feature.onboarding.onboardingScreen
import dev.fuzz.feature.player.MiniPlayer
import dev.fuzz.feature.player.PlayerRoute
import dev.fuzz.feature.player.SoundRoute
import dev.fuzz.feature.player.playerScreen
import dev.fuzz.feature.player.soundScreen
import dev.fuzz.feature.search.artistScreens
import dev.fuzz.feature.search.searchScreen
import dev.fuzz.feature.settings.SettingsActions
import dev.fuzz.feature.settings.SettingsRoute
import dev.fuzz.feature.settings.YearRecapRoute
import dev.fuzz.feature.settings.profileScreen
import dev.fuzz.feature.settings.settingsScreen
import dev.fuzz.feature.settings.yearRecapScreen
import dev.fuzz.feature.wave.WaveRoute
import dev.fuzz.feature.wave.waveScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun FuzzApp(
    showOnboarding: Boolean,
    playerErrors: Flow<String> = emptyFlow(),
    downloadEvents: Flow<DownloadEvent> = emptyFlow(),
    navController: NavHostController = rememberNavController(),
) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = stringResource(R.string.player_error)
    val downloadDone = stringResource(R.string.download_done)
    val downloadFailed = stringResource(R.string.download_failed)
    LaunchedEffect(downloadEvents) {
        downloadEvents.collect { event ->
            snackbar.showSnackbar((if (event.success) downloadDone else downloadFailed).format(event.title))
        }
    }
    LaunchedEffect(playerErrors) {
        playerErrors.collect { error ->
            snackbar.showSnackbar(errorText.format(error), withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showChrome =
        destination != null &&
            !destination.hasRoute(PlayerRoute::class) &&
            !destination.hasRoute(OnboardingRoute::class)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
            searchScreen(onOpenArtist = { navController.navigate(it) })
            artistScreens(
                onBack = navController::popBackStack,
                onOpenArtist = { navController.navigate(it) },
                onOpenAlbum = { navController.navigate(it) },
            )
            libraryScreen(
                onOpenLiked = { navController.navigate(TrackListRoute()) },
                onOpenPlaylist = { navController.navigate(TrackListRoute(playlistId = it)) },
                onOpenDownloaded = { navController.navigate(TrackListRoute(downloaded = true)) },
                onOpenFolders = { navController.navigate(OnboardingRoute) },
                onOpenTrackList = { navController.navigate(it) },
                onOpenWishlist = { navController.navigate(WishlistRoute) },
            )
            trackListScreen(onBack = navController::popBackStack)
            wishlistScreen(onBack = navController::popBackStack)
            profileScreen(
                onOpenImport = { navController.navigate(ImportRoute) },
                onOpenConnections = { navController.navigate(ConnectionsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenSound = { navController.navigate(SoundRoute) },
                onOpenWave = { navController.navigate(WaveRoute) },
                onOpenWishlist = { navController.navigate(WishlistRoute) },
                onOpenYear = { navController.navigate(YearRecapRoute) },
            )
            yearRecapScreen(onBack = navController::popBackStack)
            waveScreen(onBack = navController::popBackStack)
            importScreen(
                onBack = navController::popBackStack,
                onSaved = { id ->
                    navController.navigate(TrackListRoute(playlistId = id)) {
                        popUpTo<ImportRoute> { inclusive = true }
                    }
                },
            )
            settingsScreen(
                SettingsActions(
                    onBack = navController::popBackStack,
                    onOpenFolders = { navController.navigate(OnboardingRoute) },
                    onOpenSound = { navController.navigate(SoundRoute) },
                    onOpenWave = { navController.navigate(WaveRoute) },
                    onOpenConnections = { navController.navigate(ConnectionsRoute) },
                    onOpenImport = { navController.navigate(ImportRoute) },
                ),
            )
            soundScreen(onBack = navController::popBackStack)
            connectionsScreen(onBack = navController::popBackStack)
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
