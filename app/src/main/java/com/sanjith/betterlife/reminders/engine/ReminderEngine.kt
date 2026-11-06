package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.domain.delivery.CalmInputs
import com.sanjith.betterlife.domain.delivery.DeliveryAction
import com.sanjith.betterlife.domain.delivery.DeliveryDecision
import com.sanjith.betterlife.domain.delivery.DeliveryInput
import com.sanjith.betterlife.domain.delivery.DeliveryPipeline
import com.sanjith.betterlife.domain.delivery.FATIGUE_WINDOW
import com.sanjith.betterlife.domain.delivery.FatigueInputs
import com.sanjith.betterlife.domain.delivery.IgnoreStreak
import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.delivery.Pause
import com.sanjith.betterlife.domain.delivery.PresentationPath
import com.sanjith.betterlife.domain.delivery.QuietHours
import com.sanjith.betterlife.domain.delivery.QuietHoursSettings
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.reminder.MissedReminder
import com.sanjith.betterlife.domain.reminder.ReminderLogRules
import com.sanjith.betterlife.domain.reminder.SchedulePlanner
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** What happened when an alarm arrived (returned for tests and the debug log). */
public enum class FireOutcome { DELIVERED, HELD, DROPPED, FAILED, DUPLICATE, UNKNOWN_REMINDER }

/** Log detail of a reminder delivered in the Pause / Quiet Hours digest. */
public const val DIGEST_DETAIL: String = "digest"

/**
 * The reminder engine: keeps AlarmManager in step with the database, and runs every firing through
 * the ordered [DeliveryPipeline]. One lock serialises all work so a reschedule and a firing can never
 * interleave. [clock] must be the REAL clock: the debug Time Machine must never move real alarms.
 */
public class ReminderEngine(
    private val store: ReminderStore,
    private val alarms: AlarmScheduler,
    private val device: DeviceStateReader,
    private val presenter: ReminderPresenter,
    private val log: ReminderLogSink,
    private val clock: Clock,
    private val allDayFireTime: suspend () -> LocalTime,
    private val calmStore: CalmStateStore = InMemoryCalmStateStore(),
    private val settings: suspend () -> AppSettings = { AppSettings() },
    /** When a habit pause range covers now, the moment it ends (Prompt 17); null = no range. */
    private val rangePauseUntil: suspend (Instant) -> Instant? = { null },
) {
    private val lock = Mutex()

    public suspend fun rescheduleAll(trigger: RescheduleTrigger): Unit = lock.withLock { rescheduleLocked(trigger) }

    public suspend fun onAlarm(reminderId: String, fireAt: Instant): FireOutcome {
        val outcome = lock.withLock { fireLocked(reminderId, fireAt) }
        // A repeating item now needs its next occurrence scheduled; a one-off needs its alarm cleared.
        if (outcome != FireOutcome.DUPLICATE && outcome != FireOutcome.UNKNOWN_REMINDER) {
            rescheduleAll(RescheduleTrigger.AFTER_FIRE)
        }
        return outcome
    }

    private suspend fun rescheduleLocked(trigger: RescheduleTrigger) {
        val now = clock.now()
        store.idsToUnschedule().forEach { id ->
            alarms.cancel(id)
            store.setScheduledFireAt(id, null)
        }
        val targets = store.loadTargets()
        val byId = targets.associateBy { it.reminderId }
        val plan = SchedulePlanner.plan(targets, now, clock.zone, allDayFireTime())
        val exactAllowed = alarms.canScheduleExact()
        plan.alarms.forEach { planned ->
            val target = byId.getValue(planned.reminderId)
            val fireAt = planned.fireAt
            if (fireAt == null) {
                if (target.scheduledFireAt != null) {
                    alarms.cancel(planned.reminderId)
                    store.setScheduledFireAt(planned.reminderId, null)
                }
                return@forEach
            }
            // Always set: after a reboot AlarmManager is empty even though the database remembers the time.
            alarms.set(planned.reminderId, fireAt, planned.kind)
            if (target.scheduledFireAt != fireAt) {
                store.setScheduledFireAt(planned.reminderId, fireAt)
                val reason = if (exactAllowed) null else ReasonCode.EXACT_ALARM_DENIED
                log.append(planned.reminderId, ReminderLogStatus.SCHEDULED, target.importance, reason, "${planned.kind} $trigger", fireAt)
            }
        }
        handleMissed(plan.missed, trigger, now)
        // After a reboot AlarmManager forgot the digest alarm; a time already past fires at once.
        calmStore.read().digestAt?.let { alarms.setDigest(it) }
    }

    private suspend fun handleMissed(missed: List<MissedReminder>, trigger: RescheduleTrigger, now: Instant) {
        val reason = if (trigger == RescheduleTrigger.BOOT) ReasonCode.DEVICE_OFF else null
        var summarised = 0
        missed.forEach { (target, missedAt) ->
            val context = store.fireContext(target.reminderId) ?: return@forEach
            val itemStillAhead = missedAt.plus(Duration.ofMinutes(target.offsetMinutes.toLong())).isAfter(now)
            val state = if (target.recurrence != null) ReminderState.SCHEDULED else ReminderState.MISSED
            if (!store.markFired(target.reminderId, missedAt, state)) return@forEach
            if (itemStillAhead) {
                // An early reminder for something still to come is still useful: send it, quietly.
                presenter.postNotification(target.reminderId, context.title, Importance.NORMAL)
                log.append(target.reminderId, ReminderLogStatus.FIRED, Importance.NORMAL, reason, "late, item still ahead", now)
            } else {
                summarised++
                log.append(target.reminderId, ReminderLogStatus.MISSED, target.importance, reason, "due ${missedAt}", now)
            }
        }
        if (summarised > 0) presenter.postMissedSummary(summarised)
    }

    private suspend fun fireLocked(reminderId: String, fireAt: Instant): FireOutcome {
        val context = store.fireContext(reminderId) ?: return FireOutcome.UNKNOWN_REMINDER
        val state = if (context.recurring) ReminderState.SCHEDULED else ReminderState.FIRED
        if (!store.markFired(reminderId, fireAt, state)) return FireOutcome.DUPLICATE
        val now = clock.now()
        val decision = DeliveryPipeline.decide(
            DeliveryInput(
                importance = context.importance,
                itemStatus = context.itemStatus,
                device = device.presentationInputs(),
                callActive = device.callActive(),
                now = now,
                calendarItem = context.itemType == ReminderItemType.EVENT,
                calm = calmInputs(now),
            ),
        )
        // Re-read after markFired: a normal (not snoozed) firing has just reset the snooze count.
        return execute(store.fireContext(reminderId) ?: context, decision)
    }

    /**
     * Before a restore replaces the database (Prompt 13): cancel every alarm the current data set, so
     * none is left behind. The restarted app then schedules the restored reminders.
     */
    public suspend fun cancelAllAlarms() {
        lock.withLock {
            store.scheduledIds().forEach { alarms.cancel(it) }
            alarms.cancelDigest()
        }
    }

    /** Pause, Quiet Hours and fatigue state at [now], read from settings, the calm store and the log. */
    private suspend fun calmInputs(now: Instant): CalmInputs {
        val s = settings()
        val state = calmStore.read()
        // A habit pause range (vacation / sick) holds reminders like "I'm busy", with the same exemptions.
        val rangePause = rangePauseUntil(now)?.let { Pause(it, holdsCalendar = !s.pauseExemptsEventsAndAlarms, holdsAlarm = !s.pauseExemptsEventsAndAlarms) }
        return CalmInputs(
            pause = state.pause ?: rangePause,
            quietUntil = QuietHours.activeUntil(s.quietHours(), now.atZone(clock.zone)),
            alarmBypassesQuiet = s.alarmBypassesQuietHours,
            fatigue = FatigueInputs(
                maxPerHour = s.fatigueMaxFullScreenPerHour,
                groupWindow = Duration.ofMinutes(s.fatigueGroupWindowMinutes.toLong()),
                recentPopups = store.recentPopupStarts(now.minus(FATIGUE_WINDOW)),
                calmUntil = state.calmUntil,
                downgradeEnabled = s.fatigueDowngradeEnabled,
                alarmExempt = s.alarmExemptFromFatigue,
            ),
        )
    }

    /** "I am busy" (Prompt 12): hold reminders until [until]; the digest alarm guarantees the pause ends. */
    public suspend fun startPause(until: Instant, holdsCalendar: Boolean, holdsAlarm: Boolean) {
        lock.withLock {
            calmStore.update { it.copy(pause = Pause(until, holdsCalendar, holdsAlarm)) }
            armDigest(until)
        }
    }

    /** Resume: the pause ends now and anything held arrives in the digest straight away. */
    public suspend fun resume() {
        lock.withLock { calmStore.update { it.copy(pause = null) } }
        deliverDigest()
    }

    /**
     * The digest alarm: if a pause or Quiet Hours window is still on, wait for its end; otherwise end
     * the pause and deliver every held reminder as ONE notification (context.md 5.1).
     */
    public suspend fun deliverDigest() {
        val delivered = lock.withLock {
            val now = clock.now()
            val pauseUntil = (calmStore.read().pause?.until ?: rangePauseUntil(now))?.takeIf { now.isBefore(it) }
            val quietUntil = QuietHours.activeUntil(settings().quietHours(), now.atZone(clock.zone))
            val held = store.heldReminderIds()
            // A running pause always keeps its end alarm; Quiet Hours only matter when something is held.
            val waitUntil = if (pauseUntil != null) listOfNotNull(pauseUntil, quietUntil).max() else quietUntil.takeIf { held.isNotEmpty() }
            if (waitUntil != null) {
                calmStore.update { it.copy(digestAt = waitUntil) }
                alarms.setDigest(waitUntil)
                return@withLock false
            }
            calmStore.update { it.copy(pause = null, digestAt = null) }
            alarms.cancelDigest()
            val titles = held.mapNotNull { id -> releaseHeld(id, now) }
            if (titles.isNotEmpty()) presenter.postDigest(titles)
            titles.isNotEmpty()
        }
        if (delivered) rescheduleAll(RescheduleTrigger.AFTER_FIRE)
    }

    // Marks one held reminder delivered (or dropped if its item went away meanwhile); its title if delivered.
    private suspend fun releaseHeld(id: String, now: Instant): String? {
        val context = store.fireContext(id) ?: return null
        if (context.itemStatus != ItemStatus.ACTIVE) {
            store.finish(id, if (context.recurring) ReminderState.SCHEDULED else ReminderState.DISMISSED)
            val reason = if (context.itemStatus == ItemStatus.COMPLETED) ReasonCode.ITEM_COMPLETED else ReasonCode.ITEM_DELETED
            log.append(id, ReminderLogStatus.DISMISSED, context.importance, reason, "dropped from digest", now)
            return null
        }
        store.finish(id, if (context.recurring) ReminderState.SCHEDULED else ReminderState.FIRED)
        log.append(id, ReminderLogStatus.FIRED, Importance.NORMAL, null, DIGEST_DETAIL, now)
        return context.title
    }

    // The digest alarm goes off at the earliest pending end; at that time it re-checks and moves on if needed.
    private suspend fun armDigest(at: Instant) {
        val current = calmStore.read().digestAt
        val next = if (current != null && current.isAfter(clock.now()) && current.isBefore(at)) current else at
        calmStore.update { it.copy(digestAt = next) }
        alarms.setDigest(next)
    }

    // Fatigue: pop-ups closed with Back or left to time out count as ignored; any answer resets the count.
    private suspend fun recordIgnored() {
        val s = settings()
        val state = calmStore.read()
        val result = IgnoreStreak.onIgnored(state.ignoreStreak, state.calmUntil, s.fatigueIgnoredLimit, s.fatigueDowngradeEnabled, clock.now())
        calmStore.update { it.copy(ignoreStreak = result.streak, calmUntil = result.calmUntil) }
        if (result.calmStarted) result.calmUntil?.let { presenter.postCalmNotice(it) }
    }

    private suspend fun recordAnswered() {
        calmStore.update { it.copy(ignoreStreak = 0) }
    }

    /** Done on the pop-up: completes the task (or just this reminder, for events) and cancels its alarms. */
    public suspend fun complete(reminderId: String) {
        lock.withLock {
            val context = store.fireContext(reminderId) ?: return@withLock
            store.completeTask(reminderId)
            store.finish(reminderId, ReminderState.COMPLETED)
            log.append(reminderId, ReminderLogStatus.COMPLETED, context.importance, null, context.itemType?.name, clock.now())
            recordAnswered()
        }
        rescheduleAll(RescheduleTrigger.AFTER_FIRE)
    }

    /** Snooze on the pop-up: the reminder comes back at [until]. */
    public suspend fun snooze(reminderId: String, until: Instant) {
        lock.withLock {
            val context = store.fireContext(reminderId) ?: return@withLock
            val count = store.snooze(reminderId, until)
            log.append(reminderId, ReminderLogStatus.SNOOZED, context.importance, null, ReminderLogRules.snoozeDetail(count, until), clock.now())
            recordAnswered()
        }
        rescheduleAll(RescheduleTrigger.AFTER_FIRE)
    }

    /** Back or swipe on the pop-up: closed without an answer. The reminder is not shown again. */
    public suspend fun dismiss(reminderId: String) {
        lock.withLock {
            val context = store.fireContext(reminderId) ?: return@withLock
            store.finish(reminderId, if (context.recurring) ReminderState.SCHEDULED else ReminderState.DISMISSED)
            log.append(reminderId, ReminderLogStatus.DISMISSED, context.importance, null, "closed without an answer", clock.now())
            recordIgnored()
        }
    }

    /** Reschedule on the pop-up: the editor opens; the real change is saved there (Prompt 9). */
    public suspend fun openedEditor(reminderId: String) {
        lock.withLock {
            val context = store.fireContext(reminderId) ?: return@withLock
            store.finish(reminderId, if (context.recurring) ReminderState.SCHEDULED else ReminderState.DISMISSED)
            log.append(reminderId, ReminderLogStatus.RESCHEDULED, context.importance, null, "opened the editor", clock.now())
            recordAnswered()
        }
    }

    /** No answer before the timeout: a MISSED entry plus one quiet notification (context.md 5.1). */
    public suspend fun timedOut(reminderId: String, timeoutMinutes: Int) {
        lock.withLock {
            val context = store.fireContext(reminderId) ?: return@withLock
            store.finish(reminderId, if (context.recurring) ReminderState.SCHEDULED else ReminderState.MISSED)
            log.append(reminderId, ReminderLogStatus.MISSED, context.importance, null, "no answer in $timeoutMinutes min", clock.now())
            presenter.postNotification(reminderId, context.title, Importance.NORMAL)
            recordIgnored()
        }
    }

    private suspend fun execute(context: FireContext, decision: DeliveryDecision): FireOutcome {
        val now = clock.now()
        val id = context.reminderId
        if (decision.action == DeliveryAction.DROP) {
            log.append(id, ReminderLogStatus.DISMISSED, decision.level, decision.reasonCode, "dropped", now)
            return FireOutcome.DROPPED
        }
        if (decision.action == DeliveryAction.HOLD) {
            // State HELD marks it for the digest; the digest alarm goes off when the hold ends.
            store.finish(id, ReminderState.HELD)
            log.append(id, ReminderLogStatus.HELD, decision.level, decision.reasonCode, "until ${decision.holdUntil}", now)
            decision.holdUntil?.let { armDigest(it) }
            return FireOutcome.HELD
        }
        when (decision.path) {
            PresentationPath.OVERLAY, PresentationPath.FULL_SCREEN_INTENT -> presenter.showPopup(context, decision.level)
            PresentationPath.NOTIFICATION_ONLY -> presenter.postNotification(id, context.title, decision.level)
            PresentationPath.NONE -> {
                log.append(id, ReminderLogStatus.FAILED, context.importance, decision.reasonCode, "nothing can be shown", now)
                return FireOutcome.FAILED
            }
        }
        log.append(id, ReminderLogStatus.FIRED, decision.level, decision.reasonCode, decision.path.name, now)
        return FireOutcome.DELIVERED
    }
}

/** The Quiet Hours part of the settings. */
internal fun AppSettings.quietHours(): QuietHoursSettings =
    QuietHoursSettings(quietHoursEnabled, quietHoursStart, quietHoursEnd, quietHoursDays)
