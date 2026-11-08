package dev.fuzz.feature.wave

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object WaveRoute

fun NavGraphBuilder.waveScreen() {
    composable<WaveRoute> { WaveScreen() }
}
