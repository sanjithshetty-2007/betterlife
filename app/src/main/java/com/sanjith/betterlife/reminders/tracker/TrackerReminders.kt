package com.sanjith.betterlife.reminders.tracker

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.tracker.TrackerRepository
import com.sanjith.betterlife.data.tracker.TrackerSettingsStore
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.DailyAsk
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.habitDay
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerReminderRules
import com.sanjith.betterlife.domain.tracker.TrackerRules
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.popup.PopupCoordinator
import com.sanjith.betterlife.reminders.popup.ReminderPopupService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * One tracker's daily reminder (protein, sleep; owner's features, 2026-11-21). ONE alarm per tracker
 * at a time, on the same exact AlarmManager approach as the other reminders; setting it again replaces
 * it, so it is never doubled. Re-planned on boot, app update, time and time-zone changes, app launch
 * and every change to the tracker. When it goes off it shows the tracker's card in the shared pop-up
 * (ring, overlay, lock screen). For sleep the card asks about last night. [clock] is the real clock.
 */
public class TrackerReminders(
    private val kind: TrackerKind,
    private val context: Context,
    private val store: TrackerSettingsStore,
    private val repository: TrackerRepository,
    private val clock: Clock,
    private val rolloverHour: suspend () -> Int,
    private val callActive: () -> Boolean,
    private val coordinator: () -> PopupCoordinator,
    private val question: String,
    private val log: DebugLog,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** The day a reminder at [at] asks about: today for protein, last night for sleep (after the rollover hour). */
    private fun askedDay(at: Instant, rollover: Int): LocalDate = TrackerRules.latestDay(kind, habitDay(at, clock.zone, rollover))

    /** Sets the next alarm, or cancels it when there is no goal or the reminder is off. */
    public suspend fun replan() {
        if (kind == TrackerKind.PROTEIN) cancelLegacyProteinAlarm()
        val settings = store.current()
        if (!settings.reminderOn || settings.target == null) {
            alarmManager.cancel(operation())
            store.setRepeatAt(null)
            return
        }
        val rollover = rolloverHour()
        val repeatAt = store.repeatAt()
        val logged = loggedDays(repeatAt, rollover)
        val now = clock.now()
        val next = TrackerReminderRules.nextFire(now, clock.zone, settings.reminderTime, repeatAt, { askedDay(it, rollover) }, logged::contains)
        // A repeat that is no longer used (its day was logged, or midnight passed) is forgotten.
        if (repeatAt != null && next != repeatAt && next != now) store.setRepeatAt(null)
        set(next)
    }

    /** The alarm went off: show the card (or wait out a phone call), keep the next repeat on disk, re-plan. */
    public suspend fun fire() {
        val settings = store.current()
        val now = clock.now()
        val target = settings.target
        val day = askedDay(now, rolloverHour())
        if (!settings.reminderOn || target == null || repository.isLogged(day)) {
            store.setRepeatAt(null)
            replan()
            return
        }
        if (callActive()) {
            log.add("$kind reminder: in a call, trying again later")
            store.setRepeatAt(TrackerReminderRules.repeatAfterCall(now, clock.zone))
        } else {
            // Kept on disk before showing, so a killed process or a reboot still brings it back.
            store.setRepeatAt(TrackerReminderRules.repeatAfterFire(now, clock.zone))
            show(DailyAsk(kind, day, target), now)
        }
        replan()
    }

    private fun show(ask: DailyAsk, now: Instant) {
        coordinator().show(
            PopupCard(
                key = kind.name.lowercase() + "-" + ask.date,
                reminderId = null,
                title = question,
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
                ask = ask,
            ),
        )
        try {
            ReminderPopupService.show(context)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            log.add("$kind reminder: pop-up service refused (${e.javaClass.simpleName}); full-screen notification instead")
            ReminderNotifier(context).postPopupStack(question, Importance.ALARM, hideTitle = false)
        }
        log.add("$kind reminder shown")
    }

    /** The days the planner may ask about (the pending repeat's day and the days around today). */
    private suspend fun loggedDays(repeatAt: Instant?, rollover: Int): Set<LocalDate> {
        val asked = askedDay(clock.now(), rollover)
        val days = listOfNotNull(repeatAt?.let { askedDay(it, rollover) }, asked.minusDays(1), asked, asked.plusDays(1), asked.plusDays(2))
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
        REQUEST_BASE + kind.ordinal,
        Intent(context, TrackerReminderReceiver::class.java).setAction(ACTION_PREFIX + kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The first protein build aimed its alarm at a receiver that is gone; cancel any such alarm once. */
    private fun cancelLegacyProteinAlarm() {
        val legacy = Intent().setClassName(context, LEGACY_PROTEIN_RECEIVER).setAction(LEGACY_PROTEIN_ACTION)
        PendingIntent.getBroadcast(context, REQUEST_BASE, legacy, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    internal companion object {
        const val ACTION_PREFIX = "com.sanjith.betterlife.action.TRACKER_REMINDER."
        const val REQUEST_BASE = 9_400
        const val LEGACY_PROTEIN_RECEIVER = "com.sanjith.betterlife.reminders.protein.ProteinReminderReceiver"
        const val LEGACY_PROTEIN_ACTION = "com.sanjith.betterlife.action.PROTEIN_REMINDER"
    }
}

/** Every tracker alarm's target. Not exported: only our own PendingIntents reach it. */
public class TrackerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.action?.removePrefix(TrackerReminders.ACTION_PREFIX)?.let { name -> TrackerKind.entries.firstOrNull { it.name == name } } ?: return
        val container = (context.applicationContext as BetterLifeApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.trackers[kind].reminders.fire()
            } finally {
                pending.finish()
            }
        }
    }
}
