package dev.fuzz.feature.player

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlayerRoute

/** Настройки звука отдельным экраном: из профиля и настроек, без открытого плеера. */
@Serializable
data object SoundRoute

fun NavGraphBuilder.soundScreen(onBack: () -> Unit) {
    composable<SoundRoute> { SoundScreen(onBack = onBack) }
}

fun NavGraphBuilder.playerScreen(onCollapse: () -> Unit) {
    composable<PlayerRoute>(
        enterTransition = { slideInVertically { it } },
        exitTransition = { slideOutVertically { it } },
        popExitTransition = { slideOutVertically { it } },
    ) {
        PlayerScreen(onCollapse = onCollapse)
    }
}
