package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.domain.delivery.DeliveryAction
import com.sanjith.betterlife.domain.delivery.DeliveryDecision
import com.sanjith.betterlife.domain.delivery.DeliveryInput
import com.sanjith.betterlife.domain.delivery.DeliveryPipeline
import com.sanjith.betterlife.domain.delivery.PresentationPath
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.reminder.MissedReminder
import com.sanjith.betterlife.domain.reminder.SchedulePlanner
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** What happened when an alarm arrived (returned for tests and the debug log). */
public enum class FireOutcome { DELIVERED, DROPPED, FAILED, DUPLICATE, UNKNOWN_REMINDER }

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
        val decision = DeliveryPipeline.decide(
            DeliveryInput(context.importance, context.itemStatus, device.presentationInputs(), device.callActive(), clock.now()),
        )
        return execute(context, decision)
    }

    private suspend fun execute(context: FireContext, decision: DeliveryDecision): FireOutcome {
        val now = clock.now()
        val id = context.reminderId
        if (decision.action == DeliveryAction.DROP) {
            log.append(id, ReminderLogStatus.DISMISSED, decision.level, decision.reasonCode, "dropped", now)
            return FireOutcome.DROPPED
        }
        if (decision.action == DeliveryAction.HOLD) {
            log.append(id, ReminderLogStatus.HELD, decision.level, decision.reasonCode, "until ${decision.holdUntil}", now)
            return FireOutcome.DELIVERED
        }
        when (decision.path) {
            PresentationPath.OVERLAY, PresentationPath.FULL_SCREEN_INTENT -> presenter.showPopup(id, context.title, decision.level)
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
