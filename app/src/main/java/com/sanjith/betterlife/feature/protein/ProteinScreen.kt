package com.sanjith.betterlife.feature.protein

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
import com.sanjith.betterlife.data.protein.ProteinSaveResult
import com.sanjith.betterlife.domain.protein.ProteinRules
import com.sanjith.betterlife.feature.habits.HabitMonthGrid
import com.sanjith.betterlife.feature.habits.HeatmapCard
import com.sanjith.betterlife.feature.habits.SectionCard
import com.sanjith.betterlife.feature.habits.StatTile
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.LocalDate

/** Which grams dialog is open: today's, yesterday's, or the target. */
private enum class ProteinDialog { TODAY, YESTERDAY, TARGET }

/**
 * The protein dashboard (owner's feature, 2026-11-21), built from the habit pieces: today's progress,
 * "Add yesterday's protein", this week, this month, streaks, the year, and the goal and reminder.
 */
@Composable
public fun ProteinScreen(onBack: () -> Unit, viewModel: ProteinViewModel = viewModel(factory = ProteinViewModel.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val refused by viewModel.refused.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<ProteinDialog?>(null) }
    val refusedText = refused?.let { stringResource(refusalText(it)) }
    LaunchedEffect(refused) {
        refusedText ?: return@LaunchedEffect
        snackbar.showSnackbar(refusedText)
        viewModel.refusalShown()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack)
            val state = ui ?: return@Column
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!state.hasGoal) {
                    GoalSetupCard(state.settings.reminderTime, onStart = viewModel::start)
                    return@Column
                }
                TodayCard(
                    state = state,
                    onLogToday = { dialog = ProteinDialog.TODAY },
                    onYesterday = { dialog = ProteinDialog.YESTERDAY },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(state.streaks.current.toString(), stringResource(R.string.protein_stat_streak), Modifier.weight(1f))
                    StatTile(state.streaks.best.toString(), stringResource(R.string.protein_stat_best), Modifier.weight(1f))
                    StatTile(state.streaks.totalSuccessDays.toString(), stringResource(R.string.protein_stat_total), Modifier.weight(1f))
                }
                SectionLabel(stringResource(R.string.protein_this_week))
                PeriodCard(state.week) { WeekBars(state.week.days, state.today) }
                SectionLabel(stringResource(R.string.protein_this_month))
                PeriodCard(state.month) {
                    state.boxes?.let { boxes ->
                        // Today's box opens today's amount; past days are a record, like habits.
                        HabitMonthGrid(boxes, MaterialTheme.colorScheme.primary, onTap = { box -> if (box.isToday) dialog = ProteinDialog.TODAY })
                    }
                }
                state.heatmap?.let { heatmap ->
                    SectionLabel(stringResource(R.string.protein_last_12_months))
                    HeatmapCard(heatmap, MaterialTheme.colorScheme.primary)
                }
                SectionLabel(stringResource(R.string.protein_goal_and_reminder))
                SectionCard {
                    GoalAndReminder(
                        settings = state.settings,
                        onEditTarget = { dialog = ProteinDialog.TARGET },
                        onReminder = viewModel::setReminder,
                    )
                }
            }
            ShowDialog(dialog, state.today, state, onClose = { dialog = null }, viewModel)
        }
    }
}

@Composable
private fun ShowDialog(dialog: ProteinDialog?, today: LocalDate, state: ProteinUi, onClose: () -> Unit, viewModel: ProteinViewModel) {
    when (dialog) {
        null -> Unit
        ProteinDialog.TODAY -> GramsDialog(
            title = stringResource(R.string.protein_dialog_today),
            label = stringResource(R.string.protein_amount_hint),
            initial = state.todayGrams,
            parse = ProteinRules::parseGrams,
            onSave = { viewModel.save(today, it); onClose() },
            onDismiss = onClose,
        )
        ProteinDialog.YESTERDAY -> GramsDialog(
            title = stringResource(R.string.protein_dialog_yesterday),
            label = stringResource(R.string.protein_amount_hint),
            initial = state.yesterdayGrams,
            parse = ProteinRules::parseGrams,
            onSave = { viewModel.save(today.minusDays(1), it); onClose() },
            onDismiss = onClose,
        )
        ProteinDialog.TARGET -> GramsDialog(
            title = stringResource(R.string.protein_daily_target),
            label = stringResource(R.string.protein_target_hint),
            initial = state.settings.targetGrams,
            parse = ProteinRules::parseTarget,
            onSave = { viewModel.setTarget(it); onClose() },
            onDismiss = onClose,
        )
    }
}

private fun refusalText(result: ProteinSaveResult): Int = when (result) {
    ProteinSaveResult.NO_GOAL -> R.string.protein_refused_goal
    ProteinSaveResult.NOT_EDITABLE, ProteinSaveResult.INVALID, ProteinSaveResult.SAVED -> R.string.protein_refused_old
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(R.string.protein_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}
