package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ROW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
private const val ID_PREVIEW = 8

/** Debug-only screen for Prompt 7: create test reminders and see the alarm schedule and reminder log. */
@Composable
public fun ReminderEngineDebugScreen(
    onBack: () -> Unit,
    viewModel: ReminderEngineDebugViewModel = viewModel(factory = ReminderEngineDebugViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            item { EngineControls(viewModel, state.hideTitles, onBack) }
            item { SectionTitle(stringResource(R.string.engine_scheduled_title, state.scheduled.size)) }
            items(state.scheduled, key = { it.id }) { reminder ->
                MonoLine("${format(reminder.scheduledFireAt)}  ${reminder.importance}  ${reminder.id.take(ID_PREVIEW)}")
            }
            item { SectionTitle(stringResource(R.string.engine_log_title)) }
            items(state.log, key = { it.id }) { entry ->
                MonoLine(
                    "${format(entry.firedAt)}  ${entry.status} ${entry.level}" +
                        (entry.reasonCode?.let { " $it" } ?: "") + (entry.detail?.let { "  $it" } ?: ""),
                )
            }
        }
    }
}

@Composable
private fun EngineControls(viewModel: ReminderEngineDebugViewModel, hideTitles: Boolean, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.setup_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.engine_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.engine_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(1L, 2L).forEach { minutes ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Importance.entries.forEach { level ->
                    Button(
                        onClick = { viewModel.createTest(minutes, level) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.minTouchTarget),
                    ) { Text(stringResource(R.string.engine_create, minutes, level.label())) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = viewModel::rescheduleAll,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.engine_reschedule)) }
            OutlinedButton(
                onClick = viewModel::removeTests,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.engine_remove_tests)) }
        }
        OutlinedButton(
            onClick = viewModel::completeTests,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.engine_complete_tests)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .toggleable(value = hideTitles, role = Role.Switch, onValueChange = viewModel::setHideTitles),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.engine_hide_titles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = hideTitles, onCheckedChange = null)
        }
    }
}

@Composable
private fun Importance.label(): String = stringResource(
    when (this) {
        Importance.NORMAL -> R.string.engine_level_normal
        Importance.FULL_SCREEN -> R.string.engine_level_full
        Importance.ALARM -> R.string.engine_level_alarm
    },
)

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun MonoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun format(millis: Long?): String = millis?.let { ROW_TIME.format(Instant.ofEpochMilli(it)) } ?: "--"
