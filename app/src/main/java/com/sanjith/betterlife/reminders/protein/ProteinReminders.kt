package com.sanjith.betterlife.reminders.protein

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.protein.ProteinRepository
import com.sanjith.betterlife.data.protein.ProteinSettingsStore
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.popup.ProteinPrompt
import com.sanjith.betterlife.domain.protein.ProteinReminderRules
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.habitDay
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.popup.PopupCoordinator
import com.sanjith.betterlife.reminders.popup.ReminderPopupService
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The daily protein reminder (owner's feature, 2026-11-21). ONE alarm at a time, on the same exact
 * AlarmManager approach as the other reminders; setting it again replaces it, so it is never doubled.
 * Re-planned on boot, app update, time and time-zone changes, app launch and every protein change.
 * When it goes off it shows a protein card in the shared pop-up (ring, overlay, lock screen).
 * [clock] is the real clock, like every alarm.
 */
public class ProteinReminders(
    private val context: Context,
    private val store: ProteinSettingsStore,
    private val repository: ProteinRepository,
    private val clock: Clock,
    private val rolloverHour: suspend () -> Int,
    private val callActive: () -> Boolean,
    private val coordinator: () -> PopupCoordinator,
    private val log: DebugLog,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Sets the next alarm, or cancels it when there is no goal or the reminder is off. */
    public suspend fun replan() {
        val settings = store.current()
        if (!settings.reminderOn || settings.targetGrams == null) {
            alarmManager.cancel(operation())
            store.setRepeatAt(null)
            return
        }
        val rollover = rolloverHour()
        val repeatAt = store.repeatAt()
        val logged = loggedDays(repeatAt, rollover)
        val now = clock.now()
        val next = ProteinReminderRules.nextFire(now, clock.zone, settings.reminderTime, repeatAt, { habitDay(it, clock.zone, rollover) }, logged::contains)
        // A repeat that is no longer used (its day was logged, or midnight passed) is forgotten.
        if (repeatAt != null && next != repeatAt && next != now) store.setRepeatAt(null)
        set(next)
    }

    /** The alarm went off: show the card (or wait out a phone call), keep the next repeat on disk, re-plan. */
    public suspend fun fire() {
        val settings = store.current()
        val now = clock.now()
        val target = settings.targetGrams
        val day = habitDay(now, clock.zone, rolloverHour())
        if (!settings.reminderOn || target == null || repository.isLogged(day)) {
            store.setRepeatAt(null)
            replan()
            return
        }
        if (callActive()) {
            log.add("Protein reminder: in a call, trying again later")
            store.setRepeatAt(ProteinReminderRules.repeatAfterCall(now, clock.zone))
        } else {
            // Kept on disk before showing, so a killed process or a reboot still brings it back.
            store.setRepeatAt(ProteinReminderRules.repeatAfterFire(now, clock.zone))
            show(ProteinPrompt(day, target), now)
        }
        replan()
    }

    private fun show(prompt: ProteinPrompt, now: Instant) {
        val title = context.getString(R.string.protein_popup_question)
        coordinator().show(
            PopupCard(
                key = KEY_PREFIX + prompt.date,
                reminderId = null,
                title = title,
                notesPreview = null,
                importance = Importance.ALARM,
                itemType = null,
                itemId = null,
                itemTime = null,
                itemAllDay = false,
                offsetMinutes = 0,
                eventEnd = null,
                snoozeCount = 0,
                shownAt = now,
                protein = prompt,
            ),
        )
        try {
            ReminderPopupService.show(context)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            log.add("Protein reminder: pop-up service refused (${e.javaClass.simpleName}); full-screen notification instead")
            ReminderNotifier(context).postPopupStack(title, Importance.ALARM, hideTitle = false)
        }
        log.add("Protein reminder shown")
    }

    /** The protein days the planner may ask about (the pending repeat's day, today and tomorrow). */
    private suspend fun loggedDays(repeatAt: Instant?, rollover: Int): Set<java.time.LocalDate> {
        val today = habitDay(clock.now(), clock.zone, rollover)
        val days = listOfNotNull(repeatAt?.let { habitDay(it, clock.zone, rollover) }, today.minusDays(1), today, today.plusDays(1), today.plusDays(2))
        return days.filter { repository.isLogged(it) }.toSet()
    }

    private fun set(at: Instant) {
        val millis = at.toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation())
        }
    }

    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ProteinReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val ACTION = "com.sanjith.betterlife.action.PROTEIN_REMINDER"
        const val REQUEST_CODE = 9_400
        const val KEY_PREFIX = "protein-"
    }
}

/** The protein alarm's target. Not exported: only our own PendingIntent reaches it. */
public class ProteinReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProteinReminders.ACTION) return
        val container = (context.applicationContext as BetterLifeApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.proteinReminders.fire()
            } finally {
                pending.finish()
            }
        }
    }
}
