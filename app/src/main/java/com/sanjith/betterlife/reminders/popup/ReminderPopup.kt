package com.sanjith.betterlife.reminders.popup

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.delivery.isTapAllowed
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.SnoozeChoice
import com.sanjith.betterlife.domain.popup.SnoozeOption
import com.sanjith.betterlife.domain.popup.SnoozeRules
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime

private const val ENTER_MS = 280
private const val EXIT_MS = 180
private const val ENTER_START_SCALE = 0.85f
private const val EXIT_END_SCALE = 0.9f
private const val NOTES_PREVIEW_LINES = 2

/** Everything the pop-up needs from its host besides the cards. */
public data class PopupUi(
    val hideTitles: Boolean,
    val reduceMotion: Boolean,
    val snoozeOptions: (PopupCard) -> List<SnoozeOption>,
    val pickTime: (PopupCard, LocalTime) -> Instant?,
    val formatTime: (Instant) -> String,
    val formatDate: (Instant) -> String,
)

private enum class CardMode { BUTTONS, SNOOZE, PICK_TIME, WHY }

/**
 * The reminder pop-up: every showing reminder as a card in ONE window (context.md 5.1), shared by the
 * overlay and the lock-screen activity. Buttons ignore taps for 700 ms after a card appears.
 */
@Composable
public fun ReminderPopupStack(
    cards: List<PopupCard>,
    ui: PopupUi,
    onAction: (key: String, action: CardAction) -> Unit,
    onTapIgnored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BetterLifeColors.Scrim)
            .safeDrawingPadding()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.popupMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        ) {
            if (cards.size > 1) {
                item(key = "header") {
                    Text(
                        text = pluralStringResource(R.plurals.popup_stack_header, cards.size, cards.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            items(cards, key = { it.key }) { card ->
                AnimatedCard(card, ui, onAction, onTapIgnored, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun AnimatedCard(
    card: PopupCard,
    ui: PopupUi,
    onAction: (String, CardAction) -> Unit,
    onTapIgnored: () -> Unit,
    modifier: Modifier,
) {
    val visibility = remember(card.key) { MutableTransitionState(ui.reduceMotion).apply { targetState = true } }
    var pending by remember(card.key) { mutableStateOf<CardAction?>(null) }
    val shownAt = remember(card.key) { SystemClock.uptimeMillis() }
    val guarded: (CardAction) -> Unit = { action ->
        if (isTapAllowed(shownAt, SystemClock.uptimeMillis())) {
            pending = action
            visibility.targetState = false
        } else {
            onTapIgnored()
        }
    }
    // Let the exit animation play, then hand the answer over (at once when animations are off).
    LaunchedEffect(pending) {
        val action = pending ?: return@LaunchedEffect
        if (!ui.reduceMotion) delay(EXIT_MS.toLong())
        onAction(card.key, action)
    }
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
        enter = if (ui.reduceMotion) EnterTransition.None else fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), ENTER_START_SCALE),
        exit = if (ui.reduceMotion) ExitTransition.None else fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), EXIT_END_SCALE),
    ) {
        PopupCardView(card, ui, guarded)
    }
}

@Composable
private fun PopupCardView(card: PopupCard, ui: PopupUi, onAction: (CardAction) -> Unit) {
    var mode by rememberSaveable(card.key) { mutableStateOf(CardMode.BUTTONS) }
    val title = if (ui.hideTitles) stringResource(R.string.popup_hidden_title) else card.title
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardHeader(card, ui, onWhy = { mode = if (mode == CardMode.WHY) CardMode.BUTTONS else CardMode.WHY })
            Text(
                text = timeText(card, ui),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            card.notesPreview?.takeIf { it.isNotBlank() && !ui.hideTitles }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = NOTES_PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            when (mode) {
                CardMode.BUTTONS -> MainButtons(card, ui, onAction, onSnooze = { mode = CardMode.SNOOZE })
                CardMode.SNOOZE -> SnoozePanel(
                    options = ui.snoozeOptions(card),
                    formatTime = ui.formatTime,
                    onPick = { option -> option.at?.let { onAction(CardAction.Snooze(it)) } ?: run { mode = CardMode.PICK_TIME } },
                    onBack = { mode = CardMode.BUTTONS },
                )
                CardMode.PICK_TIME -> PickTimePanel(
                    onConfirm = { time -> ui.pickTime(card, time)?.let { onAction(CardAction.Snooze(it)) } != null },
                    onBack = { mode = CardMode.SNOOZE },
                )
                CardMode.WHY -> WhyPanel(card, ui, onEdit = { onAction(CardAction.Reschedule) }, onBack = { mode = CardMode.BUTTONS })
            }
        }
    }
}

@Composable
private fun CardHeader(card: PopupCard, ui: PopupUi, onWhy: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PopupMascot(reduceMotion = ui.reduceMotion, description = stringResource(R.string.popup_mascot_description))
        ImportanceBadge(card.importance, Modifier.padding(start = Spacing.sm).weight(1f))
        IconButton(onClick = onWhy) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.popup_why),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MainButtons(card: PopupCard, ui: PopupUi, onAction: (CardAction) -> Unit, onSnooze: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (SnoozeRules.suggestMoveToTomorrow(card.snoozeCount)) {
            val tomorrow = ui.snoozeOptions(card).firstOrNull { it.choice == SnoozeChoice.TOMORROW && it.allowed }?.at
            MoveToTomorrowSuggestion(card.snoozeCount, enabled = tomorrow != null) {
                tomorrow?.let { onAction(CardAction.Snooze(it)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.popup_snooze))
            }
            Button(onClick = { onAction(CardAction.Done) }, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.popup_done))
            }
        }
        TextButton(
            onClick = { onAction(CardAction.Reschedule) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.popup_reschedule)) }
    }
}

@Composable
private fun MoveToTomorrowSuggestion(count: Int, enabled: Boolean, onMove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.popup_snoozed_many, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onMove, enabled = enabled, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.popup_move_to_tomorrow))
        }
    }
}

@Composable
private fun timeText(card: PopupCard, ui: PopupUi): String {
    val time = card.itemTime ?: card.shownAt
    return if (card.itemAllDay) ui.formatDate(time) else ui.formatTime(time)
}
