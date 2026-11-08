package com.sanjith.betterlife.feature.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.ui.feedback.rememberDoneHaptics
import com.sanjith.betterlife.ui.nav.tabBarClearance
import com.sanjith.betterlife.ui.nav.tabContentPadding
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/** Undo stays offered for about 6 seconds, like Tasks (Prompt 9). */
private const val UNDO_WINDOW_MILLIS = 6_000L

/** Habits (Prompt 15): one card per habit with its numbers and the 31-box grid of the month shown. */
@Composable
public fun HabitsScreen(
    onOpenHabit: (String) -> Unit,
    onNewHabit: () -> Unit,
    onOpenCelebrations: () -> Unit = {},
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberDoneHaptics()
    UndoSnackbar(undo, snackbar, viewModel)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = tabBarClearance())) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onNewHabit, hapticsOn = ui?.haptics == true, onHaptics = viewModel::setHaptics, onOpenCelebrations = onOpenCelebrations)
            val state = ui ?: return@Column
            MonthRow(state, viewModel)
            QuietMilestoneBanner()
            val cards = state.cards ?: return@Column
            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(tabContentPadding()).padding(Spacing.sm), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.habits_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }
            LazyColumn(contentPadding = tabContentPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(cards, key = { it.id }, contentType = { "habit" }) { card ->
                    HabitCard(
                        card = card,
                        pastMonth = !state.isCurrentMonth,
                        onOpen = { onOpenHabit(card.id) },
                        onTap = { box ->
                            if (box.state == BoxState.OPEN) haptics.done() else haptics.undone()
                            viewModel.toggle(card, box)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UndoSnackbar(undo: HabitUndo?, snackbar: SnackbarHostState, viewModel: HabitsViewModel) {
    val message = undo?.let {
        val day = it.date.format(DateTimeFormatter.ofPattern("d MMM"))
        stringResource(if (it.ticked) R.string.habit_ticked_snack else R.string.habit_unticked_snack, it.name, day)
    }
    val action = stringResource(R.string.undo)
    LaunchedEffect(undo) {
        val offer = undo ?: return@LaunchedEffect
        val expiry = launch {
            delay(UNDO_WINDOW_MILLIS)
            snackbar.currentSnackbarData?.dismiss()
        }
        val result = snackbar.showSnackbar(message.orEmpty(), action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
        expiry.cancel()
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(offer) else viewModel.undoExpired(offer)
    }
}

@Composable
private fun Header(onNewHabit: () -> Unit, hapticsOn: Boolean, onHaptics: (Boolean) -> Unit, onOpenCelebrations: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.habits_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onNewHabit) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.habit_new), tint = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.habits_more), tint = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.habits_vibrate_on_tick)) },
                    trailingIcon = { Checkbox(checked = hapticsOn, onCheckedChange = null) },
                    onClick = { onHaptics(!hapticsOn) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.celebrations_menu)) },
                    onClick = { menu = false; onOpenCelebrations() },
                )
            }
        }
    }
}

/** Previous / next month with the month's name; "N of M done today" or "Back to this month" under it. */
@Composable
private fun MonthRow(ui: HabitsUi, viewModel: HabitsViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previousMonth) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.habits_previous_month))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = ui.month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (ui.isCurrentMonth) {
                Text(
                    text = stringResource(R.string.habits_done_today, ui.doneToday, ui.cards?.size ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(onClick = viewModel::backToThisMonth, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.habits_back_to_this_month))
                }
            }
        }
        IconButton(onClick = viewModel::nextMonth, enabled = !ui.isCurrentMonth) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.habits_next_month))
        }
    }
}

@Composable
private fun HabitCard(card: HabitCardUi, pastMonth: Boolean, onOpen: () -> Unit, onTap: (GridBox) -> Unit) {
    val color = habitColor(card.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).clip(MaterialTheme.shapes.large).clickable(onClick = onOpen).padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                HabitAvatar(card.avatar, color)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (card.essential) EssentialBadge()
                    }
                    if (pastMonth) {
                        Text(
                            text = stringResource(R.string.habit_month_count, card.doneInMonth, card.boxes.count { it.date != null }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StreakLine(card)
                    }
                }
                if (!pastMonth) ConsistencyNumber(card.consistency)
            }
            HabitMonthGrid(card.boxes, color, onTap)
        }
    }
}

/** The habit's emoji on a tile tinted with its colour. */
@Composable
internal fun HabitAvatar(avatar: String?, color: androidx.compose.ui.graphics.Color, large: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (large) Dimens.habitAvatarLarge else Dimens.habitAvatar)
            .background(color.copy(alpha = BetterLifeColors.HEATMAP_SOFT_ALPHA), MaterialTheme.shapes.medium),
    ) {
        Text(
            text = avatar ?: HabitAvatars.DEFAULT,
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun EssentialBadge() {
    Text(
        text = stringResource(R.string.habit_essential_badge),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(BetterLifeColors.Greyed, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/** "37-day streak", or "Best: 21. Restarting." when broken; never a bare 0. */
@Composable
internal fun StreakLine(card: HabitCardUi) {
    val (icon, text) = when {
        card.streak.current > 0 -> R.drawable.ic_local_fire to stringResource(R.string.habit_streak, card.streak.current)
        card.streak.restarting -> R.drawable.ic_refresh to stringResource(R.string.habit_restarting, card.streak.best)
        else -> R.drawable.ic_local_fire to stringResource(R.string.habit_streak_none)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.smallIconSize))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Consistency leads: the big number, or "New" before 7 days of data. */
@Composable
private fun ConsistencyNumber(consistency: Consistency) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = when (consistency) {
                Consistency.New -> stringResource(R.string.habit_consistency_new)
                is Consistency.Percent -> stringResource(R.string.habit_percent, consistency.percent)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.habit_consistency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
