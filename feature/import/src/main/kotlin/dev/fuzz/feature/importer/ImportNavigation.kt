package dev.fuzz.feature.importer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ImportRoute

fun NavGraphBuilder.importScreen(
    onBack: () -> Unit,
    onSaved: (playlistId: String) -> Unit,
) {
    composable<ImportRoute> { ImportEntry(onBack = onBack, onSaved = onSaved) }
}
