package dev.fuzz.feature.connections

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ConnectionsRoute

fun NavGraphBuilder.connectionsScreen(onBack: () -> Unit) {
    composable<ConnectionsRoute> { ConnectionsEntry(onBack = onBack) }
}
