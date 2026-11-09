package dev.fuzz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.stats.YearRecap
import dev.fuzz.core.domain.stats.YearRecapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YearRecapViewModel
    @Inject
    constructor(
        repository: YearRecapRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow<YearRecap?>(null)
        val recap: StateFlow<YearRecap?> = state.asStateFlow()

        init {
            viewModelScope.launch { state.value = repository.currentYear() }
        }
    }
