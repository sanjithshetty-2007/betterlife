package com.sanjith.betterlife.reminders.popup

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sanjith.betterlife.AppContainer
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.domain.popup.SnoozeRules
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.zonedNow
import java.util.Date

/**
 * Draws the shared pop-up stack for the overlay window and the lock-screen activity. [onScreenLock]
 * is true when shown over the lock screen, where the privacy setting may hide titles.
 */
@Composable
public fun PopupHost(container: AppContainer, onScreenLock: Boolean) {
    val context = LocalContext.current
    val coordinator = container.popupCoordinator
    val stack by coordinator.state.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(AppSettings())
    val clock = container.realClock
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
    val ui = PopupUi(
        hideTitles = onScreenLock && settings.hideTitleOnLockScreen,
        reduceMotion = context.isReducedMotion(),
        snoozeOptions = { card ->
            SnoozeRules.options(clock.zonedNow(), settings.snoozeTonight, settings.snoozeTomorrow, settings.rolloverHour, card.eventEnd)
        },
        pickTime = { card, time ->
            SnoozeRules.pickedTime(clock.zonedNow(), time).takeIf { SnoozeRules.allowedAt(it, clock.now(), card.eventEnd) }
        },
        formatTime = { timeFormat.format(Date.from(it)) },
        formatDate = { dateFormat.format(Date.from(it)) },
    )
    ReminderPopupStack(
        cards = stack.cards,
        ui = ui,
        onAction = coordinator::act,
        onTapIgnored = { container.debugLog.add("Pop-up: tap ignored (tap guard)") },
    )
}
