package dev.fuzz.core.ui.mvi

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MviViewModelTest {
    private data class CounterState(
        val value: Int = 0,
    ) : UiState

    private sealed interface CounterIntent : UiIntent {
        data object Increment : CounterIntent

        data object Reset : CounterIntent
    }

    private data class CounterEffect(
        val message: String,
    ) : UiEffect

    private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
        override fun onIntent(intent: CounterIntent) {
            when (intent) {
                CounterIntent.Increment -> {
                    setState { copy(value = value + 1) }
                }

                CounterIntent.Reset -> {
                    setState { CounterState() }
                    sendEffect(CounterEffect("reset"))
                }
            }
        }
    }

    @Test
    fun `intent reduces state`() {
        val vm = CounterViewModel()
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Increment)
        assertEquals(2, vm.state.value.value)
    }

    @Test
    fun `effect is delivered once`() =
        runTest {
            val vm = CounterViewModel()
            vm.onIntent(CounterIntent.Increment)
            vm.onIntent(CounterIntent.Reset)
            assertEquals(0, vm.state.value.value)
            vm.effects.test {
                assertEquals(CounterEffect("reset"), awaitItem())
                expectNoEvents()
            }
        }
}
