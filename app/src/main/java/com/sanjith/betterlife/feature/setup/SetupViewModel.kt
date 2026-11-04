package com.sanjith.betterlife.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.setup.SetupRepository
import com.sanjith.betterlife.domain.setup.SetupCheck
import com.sanjith.betterlife.domain.setup.SetupStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public sealed interface SetupUiState {
    public data object Loading : SetupUiState
    public data class Loaded(val status: SetupStatus) : SetupUiState
}

public class SetupViewModel(private val repository: SetupRepository) : ViewModel() {

    public val uiState: StateFlow<SetupUiState> = repository.status
        .map { status -> status?.let { SetupUiState.Loaded(it) } ?: SetupUiState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupUiState.Loading)

    /** Called on every resume, so coming back from system settings shows the new state. */
    public fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    public fun setManualTick(check: SetupCheck, ticked: Boolean) {
        viewModelScope.launch { repository.setManualTick(check, ticked) }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                SetupViewModel(app.container.setupRepository)
            }
        }
    }
}
