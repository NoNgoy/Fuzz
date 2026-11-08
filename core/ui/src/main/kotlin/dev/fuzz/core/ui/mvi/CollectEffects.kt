package dev.fuzz.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Собирает одноразовые эффекты, пока экран виден. */
@Composable
fun <E : UiEffect> CollectEffects(
    effects: Flow<E>,
    onEffect: suspend (E) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler.value(it) }
        }
    }
}
