package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow

public class PopupSpikeViewModel(private val log: DebugLog) : ViewModel() {
    public val entries: StateFlow<List<String>> = log.entries

    public fun clear() {
        log.clear()
    }

    public companion object {
        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp
                PopupSpikeViewModel(app.container.debugLog)
            }
        }
    }
}

/** Debug-only screen for the Prompt 3 gate: schedules a test pop-up and shows the decision log. */
@Composable
public fun PopupSpikeScreen(
    onBack: () -> Unit,
    viewModel: PopupSpikeViewModel = viewModel(factory = PopupSpikeViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = innerPadding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                SpikeHeader(
                    onBack = onBack,
                    onShow = { PopupSpikeService.start(context) },
                    onClear = viewModel::clear,
                )
            }
            // Newest first, so the latest outcome is visible without scrolling.
            itemsIndexed(entries.asReversed()) { _, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpikeHeader(onBack: () -> Unit, onShow: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.setup_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.spike_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.spike_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        Button(
            onClick = onShow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.spike_show_button)) }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.spike_clear_log)) }
        Text(
            text = stringResource(R.string.spike_log_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
