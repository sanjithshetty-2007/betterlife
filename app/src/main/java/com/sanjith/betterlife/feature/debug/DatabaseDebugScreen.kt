package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.AppContainer
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.debug.SeedDataGenerator
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class DatabaseDebugUiState(
    val tasks: Int = 0,
    val events: Int = 0,
    val reminders: Int = 0,
    val logEntries: Int = 0,
    val busy: Boolean = false,
    val lastResult: String? = null,
)

public class DatabaseDebugViewModel(
    counts: Flow<List<Int>>,
    private val seeder: SeedDataGenerator,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val lastResult = MutableStateFlow<String?>(null)

    public val uiState: StateFlow<DatabaseDebugUiState> = combine(counts, busy, lastResult) { c, isBusy, result ->
        DatabaseDebugUiState(c[0], c[1], c[2], c[3], isBusy, result)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DatabaseDebugUiState())

    public fun seed() = runBusy {
        val r = seeder.seed()
        "Seeded ${r.tasks} tasks, ${r.events} events, ${r.reminders} reminders in ${r.millis} ms"
    }

    public fun wipe() = runBusy { "Removed ${seeder.wipe()} seeded rows" }

    // The database runs its own queries off the main thread; the UI only waits for the result.
    private fun runBusy(work: suspend () -> String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            lastResult.value = try {
                work()
            } finally {
                busy.value = false
            }
        }
    }

    public companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c: AppContainer = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                val counts = combine(
                    c.taskRepository.observeActiveCount(),
                    c.eventRepository.observeActiveCount(),
                    c.reminderRepository.observeActiveCount(),
                    c.reminderLogRepository.observeCount(),
                ) { t, e, r, l -> listOf(t, e, r, l) }
                DatabaseDebugViewModel(counts, c.seedDataGenerator)
            }
        }
    }
}

/** Debug-only screen for Prompt 5: row counts plus seed and wipe buttons. */
@Composable
public fun DatabaseDebugScreen(
    onBack: () -> Unit,
    viewModel: DatabaseDebugViewModel = viewModel(factory = DatabaseDebugViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.setup_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.db_debug_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.db_debug_counts, state.tasks, state.events, state.reminders, state.logEntries),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(
                onClick = viewModel::seed,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.db_debug_seed)) }
            OutlinedButton(
                onClick = viewModel::wipe,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.db_debug_wipe)) }
            val status = if (state.busy) stringResource(R.string.db_debug_working) else state.lastResult
            status?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            IntegritySection()
        }
    }
}
