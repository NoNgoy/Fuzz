package dev.fuzz.core.ui.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/** Неизменяемое состояние экрана. */
interface UiState

/** Действие пользователя на экране. */
interface UiIntent

/** Одноразовое событие: навигация, тост, системный диалог. */
interface UiEffect

/**
 * Базовая ViewModel для MVI: экран отдаёт [UiIntent] в [onIntent], читает [state]
 * и один раз обрабатывает каждый [UiEffect] из [effects].
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: S
        get() = mutableState.value

    abstract fun onIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        mutableState.update(reducer)
    }

    protected fun sendEffect(effect: E) {
        effectChannel.trySend(effect)
    }
}
