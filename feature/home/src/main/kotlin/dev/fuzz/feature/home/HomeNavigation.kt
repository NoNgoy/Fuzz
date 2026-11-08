package dev.fuzz.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onOpenWaveSettings: () -> Unit,
    onAddMusic: () -> Unit,
) {
    composable<HomeRoute> { HomeEntry(onOpenWaveSettings = onOpenWaveSettings, onAddMusic = onAddMusic) }
}
