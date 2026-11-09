package dev.fuzz.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.domain.player.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface LaunchState {
    data object Loading : LaunchState

    data class Ready(
        val onboardingCompleted: Boolean,
    ) : LaunchState
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        onboarding: OnboardingRepository,
        player: PlayerController,
    ) : ViewModel() {
        /** Ошибки плеера для подсказки внизу экрана: каждая новая показывается один раз. */
        val playerErrors: Flow<String> = player.state.map { it.error }.distinctUntilChanged().filterNotNull()

        val launchState: StateFlow<LaunchState> =
            onboarding.state
                .map { LaunchState.Ready(it.completed) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, LaunchState.Loading)
    }
