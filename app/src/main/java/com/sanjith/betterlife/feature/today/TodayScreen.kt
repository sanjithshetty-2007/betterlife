package com.sanjith.betterlife.feature.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.rememberReducedMotion
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.today.TodayItem
import com.sanjith.betterlife.feature.account.SyncIndicatorLine
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.format.DateTimeFormatter

private const val RING_MILLIS = 1_100
private const val MORNING_FROM = 5
private const val AFTERNOON_FROM = 12
private const val EVENING_FROM = 17

/** Where Today sends the owner. */
public data class TodayActions(
    val openItem: (TodayItem) -> Unit,
    val newTask: () -> Unit,
    val minimumDay: () -> Unit,
    val comeback: () -> Unit,
    val seeAllTasks: () -> Unit,
    val openAccount: () -> Unit,
    val askName: () -> Unit,
)

/** Today, the home screen (Prompt 17): "what should I do right now?". */
@Composable
public fun TodayScreen(
    actions: TodayActions,
    /** Banners from elsewhere: Reminder Health problems and an "I'm busy" pause. */
    banners: @Composable () -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val comeback by viewModel.comeback.collectAsStateWithLifecycle()
    LaunchedEffect(comeback) {
        if (comeback != null) {
            viewModel.comebackShown()
            actions.comeback()
        }
    }
    LaunchedEffect(Unit) { if (viewModel.needsName()) actions.askName() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        val state = ui ?: return@Scaffold
        val snapshot = state.snapshot
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = tabContentPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { Header(state, actions.openAccount) }
            item { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { banners() } }
            snapshot.activePause?.let { range -> item { PauseRangeBanner(range, onEnd = { viewModel.endPause(range.id) }) } }
            item { Hero(snapshot) }
            item { CaptureRow(onTask = actions.newTask, onBadDay = actions.minimumDay) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.today_right_now),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = actions.seeAllTasks, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(R.string.today_see_all)) }
                }
            }
            if (snapshot.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_nothing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    )
                }
            }
            items(snapshot.rows, key = { row -> rowKey(row) }) { row ->
                Box(Modifier.animateItem()) { TodayRowCard(row, actions.openItem, viewModel::tick) }
            }
        }
    }
}

private fun rowKey(row: com.sanjith.betterlife.domain.today.TodayRow): String = when (row) {
    is com.sanjith.betterlife.domain.today.TodayRow.Single -> "${row.item.kind}:${row.item.id}"
    is com.sanjith.betterlife.domain.today.TodayRow.Group -> "group:${row.minute}"
}

/** Date, greeting with the owner's name, the sync line, and Blip in today's mood. */
@Composable
private fun Header(ui: TodayUi, onOpenAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                ui.snapshot.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val greeting = when (ui.hour) {
                in MORNING_FROM until AFTERNOON_FROM -> R.string.today_greeting_morning
                in AFTERNOON_FROM until EVENING_FROM -> R.string.today_greeting_afternoon
                else -> R.string.today_greeting_evening
            }
            Text(
                stringResource(greeting, ui.ownerName),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            SyncIndicatorLine(onOpen = onOpenAccount)
        }
        Mascot(ui.mood, ui.stage, description = mascotDescription(ui.mood), modifier = Modifier.size(Dimens.mascotMedium))
    }
}

/** Progress ring for today, consistency first with the streak beside it, and the counts. */
@Composable
private fun Hero(snapshot: TodaySnapshot) {
    val reduced = rememberReducedMotion()
    val progress by animateFloatAsState(snapshot.progressPercent / PERCENT, animationSpec = tween(if (reduced) 0 else RING_MILLIS), label = "ring")
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        val glow = remember { Brush.horizontalGradient(listOf(Color.Transparent, BetterLifeColors.HeroGlow)) }
        Row(Modifier.fillMaxWidth().background(glow).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val ringDescription = stringResource(R.string.today_progress_description, snapshot.progressPercent)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Dimens.healthRing).semantics(mergeDescendants = true) { contentDescription = ringDescription }) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(Dimens.healthRing),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeWidth = Dimens.healthRingStroke,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.habit_percent, snapshot.progressPercent), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.today_progress_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        when (val c = snapshot.consistency) {
                            Consistency.New -> stringResource(R.string.habit_consistency_new)
                            is Consistency.Percent -> stringResource(R.string.habit_percent, c.percent)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(stringResource(R.string.today_consistency), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(painterResource(R.drawable.ic_local_fire), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.smallIconSize))
                    Text(
                        if (snapshot.bestStreak > 0) stringResource(R.string.today_streak, snapshot.bestStreak) else stringResource(R.string.today_streak_none),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(R.string.today_counts, snapshot.tasksDone, snapshot.tasksTotal, snapshot.habitsDone, snapshot.habitsTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Task, Note, Voice (Note and Voice arrive in Phase 4) and "Bad day?". */
@Composable
private fun CaptureRow(onTask: () -> Unit, onBadDay: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CaptureTile(R.drawable.ic_add, stringResource(R.string.today_capture_task), enabled = true, highlighted = false, onClick = onTask, modifier = Modifier.weight(1f))
        val note = stringResource(R.string.today_capture_note)
        CaptureTile(R.drawable.ic_note, note, enabled = false, highlighted = false, onClick = {}, modifier = Modifier.weight(1f), description = stringResource(R.string.today_capture_later, note))
        val voice = stringResource(R.string.today_capture_voice)
        CaptureTile(R.drawable.ic_mic, voice, enabled = false, highlighted = false, onClick = {}, modifier = Modifier.weight(1f), description = stringResource(R.string.today_capture_later, voice))
        CaptureTile(R.drawable.ic_spa, stringResource(R.string.today_bad_day), enabled = true, highlighted = true, onClick = onBadDay, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CaptureTile(icon: Int, label: String, enabled: Boolean, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier, description: String? = null) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget).semantics { if (description != null) contentDescription = description },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(painterResource(icon), null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private const val PERCENT = 100f
