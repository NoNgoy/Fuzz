package dev.fuzz.feature.player

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlayerRoute

fun NavGraphBuilder.playerScreen(onCollapse: () -> Unit) {
    composable<PlayerRoute>(
        enterTransition = { slideInVertically { it } },
        exitTransition = { slideOutVertically { it } },
        popExitTransition = { slideOutVertically { it } },
    ) {
        PlayerScreen(onCollapse = onCollapse)
    }
}
