package com.sanjith.betterlife.reminders.alarm

import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.data.alarm.PendingAlarmStore
import com.sanjith.betterlife.domain.alarm.AlarmKind
import com.sanjith.betterlife.domain.alarm.PendingTestAlarm
import com.sanjith.betterlife.domain.alarm.RearmPlan
import com.sanjith.betterlife.domain.alarm.planRearm
import com.sanjith.betterlife.domain.alarm.triggerAfterMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Keeps the scheduled test alarm and its persisted copy in step, and logs every change. */
public class TestAlarmController(
    private val scheduler: TestAlarmScheduler,
    private val store: PendingAlarmStore,
    private val log: DebugLog,
    private val nowMillis: () -> Long,
) {

    public suspend fun scheduleIn(minutes: Int, kind: AlarmKind): ScheduleResult {
        val trigger = triggerAfterMinutes(nowMillis(), minutes)
        val result = scheduler.schedule(trigger, kind)
        if (result == ScheduleResult.SCHEDULED) {
            store.save(PendingTestAlarm(trigger, kind))
            log.add("Scheduled $kind for ${format(trigger)} (in $minutes min)")
        } else {
            log.add("Not scheduled: exact alarms are not allowed (EXACT_ALARM_DENIED)")
        }
        return result
    }

    public suspend fun cancel() {
        scheduler.cancel()
        store.clear()
        log.add("Cancelled the pending test alarm")
    }

    /** Called when the alarm fires: it is no longer pending. */
    public suspend fun markFired() {
        store.clear()
    }

    /** Re-arms the persisted alarm after boot or an app update. [why] goes into the log. */
    public suspend fun rearm(why: String) {
        val pending = store.current()
        if (pending == null) {
            log.add("$why: no pending test alarm to re-arm")
            return
        }
        val plan = planRearm(pending, nowMillis())
        val trigger = when (plan) {
            is RearmPlan.AtOriginalTime -> plan.triggerAtMillis
            is RearmPlan.Overdue -> plan.fireAtMillis
        }
        val result = scheduler.schedule(trigger, pending.kind)
        val detail = when (plan) {
            is RearmPlan.AtOriginalTime -> "at the original time ${format(trigger)}"
            is RearmPlan.Overdue ->
                "missed by ${plan.missedByMillis / MILLIS_PER_SECOND} s while off (DEVICE_OFF), firing at ${format(trigger)}"
        }
        log.add("$why: re-armed ${pending.kind} $detail -> $result")
    }

    private fun format(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis))

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}
