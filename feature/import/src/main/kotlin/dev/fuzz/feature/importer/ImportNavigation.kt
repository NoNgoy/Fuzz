package dev.fuzz.feature.importer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ImportRoute

fun NavGraphBuilder.importScreen() {
    composable<ImportRoute> { ImportScreen() }
}
