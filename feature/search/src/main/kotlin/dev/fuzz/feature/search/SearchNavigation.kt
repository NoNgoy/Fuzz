package dev.fuzz.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavGraphBuilder.searchScreen() {
    composable<SearchRoute> { SearchScreen() }
}
