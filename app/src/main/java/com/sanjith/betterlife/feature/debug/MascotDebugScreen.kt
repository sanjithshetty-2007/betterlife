package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.feature.tasks.SectionLabel
import com.sanjith.betterlife.data.habit.CelebrationEvent
import com.sanjith.betterlife.domain.habit.MilestoneDetector
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.domain.mascot.MoodReason
import com.sanjith.betterlife.domain.mascot.MoodResult
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.mascot.MascotStatic
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val CYCLE_MILLIS = 3_000L

/** Debug: Blip in every mood and both stages drawn so far, cycling, and the mood real data gives now (Prompt 16). */
@Composable
public fun MascotDebugScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val now by produceState<MoodResult?>(null) { value = container.mascotMood.mood() }
    val stageNow by produceState<MascotStage?>(null) { value = container.mascotMood.stage() }
    var mood by remember { mutableStateOf(MascotMood.CHEERING) }
    var stage by remember { mutableStateOf(MascotStage.BABY) }
    var cycling by remember { mutableStateOf(true) }
    LaunchedEffect(cycling) {
        while (cycling) {
            delay(CYCLE_MILLIS)
            mood = MascotMood.entries[(mood.ordinal + 1) % MascotMood.entries.size]
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(stringResource(R.string.mascot_debug_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            now?.let { result ->
                Text(
                    text = stringResource(R.string.mascot_debug_now, stringResource(moodLabel(result.mood)), stringResource(reasonLabel(result.reason))),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            stageNow?.let { Text(stringResource(R.string.mascot_debug_stage, stringResource(stageLabel(it))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Mascot(mood, stage, stringResource(moodLabel(mood)), Modifier.size(Dimens.mascotLarge).align(Alignment.CenterHorizontally))
            Text(
                stringResource(moodLabel(mood)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MascotMood.entries.forEach { m ->
                    FilterChip(selected = m == mood, onClick = { cycling = false; mood = m }, label = { Text(stringResource(moodLabel(m))) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(MascotStage.POD, MascotStage.BABY).forEach { s ->
                    FilterChip(selected = s == stage, onClick = { stage = s }, label = { Text(stringResource(stageLabel(s))) })
                }
            }
            Button(onClick = { cycling = !cycling }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(if (cycling) R.string.mascot_debug_stop else R.string.mascot_debug_cycle))
            }
            val testHabit = stringResource(R.string.mascot_debug_test_habit)
            Button(
                onClick = { container.celebrations.publish(CelebrationEvent("debug", testHabit, MilestoneDetector.MILESTONES.random(), quiet = false)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.mascot_debug_test_celebration)) }
            SectionLabel(stringResource(R.string.mascot_debug_static))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MascotMood.entries.forEach { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MascotStatic(m, MascotStage.BABY, stringResource(moodLabel(m)), Modifier.size(Dimens.mascotMedium))
                        Text(stringResource(moodLabel(m)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun moodLabel(mood: MascotMood): Int = when (mood) {
    MascotMood.CHEERING -> R.string.mascot_mood_cheering
    MascotMood.PROUD -> R.string.mascot_mood_proud
    MascotMood.CURIOUS -> R.string.mascot_mood_curious
    MascotMood.SLEEPY -> R.string.mascot_mood_sleepy
    MascotMood.DISAPPOINTED -> R.string.mascot_mood_down
}

private fun stageLabel(stage: MascotStage): Int = when (stage) {
    MascotStage.POD -> R.string.mascot_stage_pod
    else -> R.string.mascot_stage_baby
}

private fun reasonLabel(reason: MoodReason): Int = when (reason) {
    MoodReason.PAUSED_TODAY -> R.string.mascot_reason_paused
    MoodReason.LATE_NIGHT -> R.string.mascot_reason_late
    MoodReason.ALL_DONE_TODAY -> R.string.mascot_reason_all_done
    MoodReason.STRONG_DAYS -> R.string.mascot_reason_strong
    MoodReason.MANY_TASKS -> R.string.mascot_reason_tasks
    MoodReason.QUIET_DAYS -> R.string.mascot_reason_quiet
    MoodReason.NOTHING_SPECIAL -> R.string.mascot_reason_idle
}
