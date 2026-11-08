package dev.fuzz.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

fun NavGraphBuilder.onboardingScreen() {
    composable<OnboardingRoute> { OnboardingScreen() }
}
