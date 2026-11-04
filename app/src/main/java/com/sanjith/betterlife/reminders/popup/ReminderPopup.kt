package com.sanjith.betterlife.reminders.popup

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.delivery.isTapAllowed
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

private const val ENTER_DURATION_MS = 280
private const val ENTER_START_SCALE = 0.85f

/** What the owner did to close the pop-up. */
public enum class PopupAction { DONE, SNOOZE }

/**
 * The full-screen reminder card, shared by the overlay window and the lock-screen Activity.
 * Buttons ignore taps for the first 700 ms ([isTapAllowed]); [onTapIgnored] lets the caller log them.
 */
@Composable
public fun ReminderPopup(
    title: String,
    timeText: String,
    reduceMotion: Boolean,
    onAction: (PopupAction) -> Unit,
    onTapIgnored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownAt = remember { SystemClock.uptimeMillis() }
    val visibility = remember { MutableTransitionState(reduceMotion).apply { targetState = true } }
    val guardedAction: (PopupAction) -> Unit = { action ->
        if (isTapAllowed(shownAt, SystemClock.uptimeMillis())) onAction(action) else onTapIgnored()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BetterLifeColors.Scrim)
            .safeDrawingPadding()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(ENTER_DURATION_MS)) + scaleIn(tween(ENTER_DURATION_MS), ENTER_START_SCALE)
            },
        ) {
            PopupCard(title = title, timeText = timeText, onAction = guardedAction)
        }
    }
}

@Composable
private fun PopupCard(title: String, timeText: String, onAction: (PopupAction) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = Dimens.popupMaxWidth),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(
                    onClick = { onAction(PopupAction.SNOOZE) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.minTouchTarget),
                ) { Text(stringResource(R.string.popup_snooze)) }
                Button(
                    onClick = { onAction(PopupAction.DONE) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.minTouchTarget),
                ) { Text(stringResource(R.string.popup_done)) }
            }
        }
    }
}
