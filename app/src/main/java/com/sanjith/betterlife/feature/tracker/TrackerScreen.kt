package com.sanjith.betterlife.feature.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.tracker.TrackerSaveResult
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.feature.habits.HabitMonthGrid
import com.sanjith.betterlife.feature.habits.HeatmapCard
import com.sanjith.betterlife.feature.habits.SectionCard
import com.sanjith.betterlife.feature.habits.StatTile
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Spacing

/** Which amount dialog is open: the latest day's, the one before, or the target. */
private enum class TrackerDialog { LATEST, EARLIER, TARGET }

/**
 * A tracker's dashboard (protein, sleep; owner's features, 2026-11-21), built from the habit pieces:
 * the latest day's progress, the fix for the day before, this week, this month, streaks, the year,
 * and the goal and reminder.
 */
@Composable
public fun TrackerScreen(kind: TrackerKind, onBack: () -> Unit) {
    val viewModel: TrackerViewModel = viewModel(key = kind.name, factory = TrackerViewModel.factory(kind))
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val refused by viewModel.refused.collectAsStateWithLifecycle()
    val texts = textsFor(kind)
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<TrackerDialog?>(null) }
    val refusedText = refused?.let { stringResource(if (it == TrackerSaveResult.NO_GOAL) R.string.tracker_refused_goal else texts.refusedOld) }
    LaunchedEffect(refused) {
        refusedText ?: return@LaunchedEffect
        snackbar.showSnackbar(refusedText)
        viewModel.refusalShown()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(stringResource(texts.title), onBack)
            val state = ui ?: return@Column
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!state.hasGoal) {
                    GoalSetupCard(kind, state.settings.reminderTime, onStart = viewModel::start)
                    return@Column
                }
                LatestCard(state, onLog = { dialog = TrackerDialog.LATEST }, onEarlier = { dialog = TrackerDialog.EARLIER })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(state.streaks.current.toString(), stringResource(R.string.tracker_stat_streak), Modifier.weight(1f))
                    StatTile(state.streaks.best.toString(), stringResource(R.string.tracker_stat_best), Modifier.weight(1f))
                    StatTile(state.streaks.totalSuccessDays.toString(), stringResource(texts.total), Modifier.weight(1f))
                }
                SectionLabel(stringResource(R.string.tracker_this_week))
                PeriodCard(kind, state.week) { WeekBars(kind, state.week.days, state.latest) }
                SectionLabel(stringResource(R.string.tracker_this_month))
                PeriodCard(kind, state.month) {
                    state.boxes?.let { boxes ->
                        // The latest day's box opens its amount; older days are a record, like habits.
                        HabitMonthGrid(boxes, MaterialTheme.colorScheme.primary, onTap = { box -> if (box.isToday) dialog = TrackerDialog.LATEST })
                    }
                }
                state.heatmap?.let { heatmap ->
                    SectionLabel(stringResource(R.string.tracker_last_12_months))
                    HeatmapCard(heatmap, MaterialTheme.colorScheme.primary)
                }
                SectionLabel(stringResource(texts.goalAndReminder))
                SectionCard {
                    GoalAndReminder(kind, state.settings, onEditTarget = { dialog = TrackerDialog.TARGET }, onReminder = viewModel::setReminder)
                }
            }
            ShowDialog(dialog, state, onClose = { dialog = null }, viewModel)
        }
    }
}

@Composable
private fun ShowDialog(dialog: TrackerDialog?, state: TrackerUi, onClose: () -> Unit, viewModel: TrackerViewModel) {
    val texts = textsFor(state.kind)
    when (dialog) {
        null -> Unit
        TrackerDialog.LATEST -> AmountDialog(
            kind = state.kind,
            title = stringResource(texts.dialogLatest),
            initial = state.latestAmount,
            parse = ::parseAmount,
            onSave = { viewModel.save(state.latest, it); onClose() },
            onDismiss = onClose,
        )
        TrackerDialog.EARLIER -> AmountDialog(
            kind = state.kind,
            title = stringResource(texts.dialogEarlier),
            initial = state.earlierAmount,
            parse = ::parseAmount,
            onSave = { viewModel.save(state.latest.minusDays(1), it); onClose() },
            onDismiss = onClose,
        )
        TrackerDialog.TARGET -> AmountDialog(
            kind = state.kind,
            title = stringResource(texts.targetLabel),
            initial = state.settings.target,
            parse = ::parseTarget,
            onSave = { viewModel.setTarget(it); onClose() },
            onDismiss = onClose,
        )
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}
