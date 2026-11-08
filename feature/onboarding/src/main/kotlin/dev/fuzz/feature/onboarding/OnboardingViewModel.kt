package dev.fuzz.feature.onboarding

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { Permissions, Folders, Background }

data class FolderOption(
    val folder: MusicFolder,
    val selected: Boolean,
)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Permissions,
    /** Шаг про автозапуск нужен только на прошивках Xiaomi: остальные не выгружают медиасервис. */
    val showBackgroundStep: Boolean = false,
    val folders: ImmutableList<FolderOption> = persistentListOf(),
) : UiState {
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.Background || showBackgroundStep }

    val canContinue: Boolean
        get() = step != OnboardingStep.Folders || folders.any { it.selected }
}

sealed interface OnboardingIntent : UiIntent {
    data class Start(
        val aggressiveBackgroundKill: Boolean,
    ) : OnboardingIntent

    data object Next : OnboardingIntent

    data object Back : OnboardingIntent

    data class ToggleFolder(
        val id: String,
    ) : OnboardingIntent

    data class AddFolder(
        val folder: MusicFolder,
    ) : OnboardingIntent
}

sealed interface OnboardingEffect : UiEffect {
    data object Finished : OnboardingEffect
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val repository: OnboardingRepository,
    ) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {
        private var started = false

        override fun onIntent(intent: OnboardingIntent) {
            when (intent) {
                is OnboardingIntent.Start -> {
                    start(intent.aggressiveBackgroundKill)
                }

                OnboardingIntent.Next -> {
                    next()
                }

                OnboardingIntent.Back -> {
                    back()
                }

                is OnboardingIntent.ToggleFolder -> {
                    setState {
                        copy(
                            folders =
                                folders
                                    .map { if (it.folder.id == intent.id) it.copy(selected = !it.selected) else it }
                                    .toImmutableList(),
                        )
                    }
                }

                is OnboardingIntent.AddFolder -> {
                    setState {
                        if (folders.any { it.folder.id == intent.folder.id }) {
                            this
                        } else {
                            copy(folders = (folders + FolderOption(intent.folder, selected = true)).toImmutableList())
                        }
                    }
                }
            }
        }

        private fun start(aggressiveBackgroundKill: Boolean) {
            if (started) return
            started = true
            setState { copy(showBackgroundStep = aggressiveBackgroundKill) }
            viewModelScope.launch {
                val saved = repository.state.first().folders
                setState { copy(folders = saved.map { FolderOption(it, selected = true) }.toImmutableList()) }
            }
        }

        private fun next() {
            val state = currentState
            if (!state.canContinue) return
            if (state.step == OnboardingStep.Folders) {
                viewModelScope.launch { repository.setFolders(state.folders.filter { it.selected }.map { it.folder }) }
            }
            val steps = state.steps
            val index = steps.indexOf(state.step)
            if (index == steps.lastIndex) {
                viewModelScope.launch {
                    repository.complete()
                    sendEffect(OnboardingEffect.Finished)
                }
            } else {
                setState { copy(step = steps[index + 1]) }
            }
        }

        private fun back() {
            val steps = currentState.steps
            val index = steps.indexOf(currentState.step)
            if (index > 0) setState { copy(step = steps[index - 1]) }
        }
    }
