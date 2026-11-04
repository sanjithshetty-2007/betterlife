package com.sanjith.betterlife.reminders.engine

import com.sanjith.betterlife.domain.delivery.ItemStatus
import com.sanjith.betterlife.domain.delivery.PresentationInputs
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import com.sanjith.betterlife.domain.reminder.AlarmKind
import com.sanjith.betterlife.domain.reminder.ItemTime
import com.sanjith.betterlife.domain.reminder.ScheduleTarget
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

public class ReminderEngineTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private class MutableClock(var instant: Instant, override val zone: ZoneId) : Clock {
        override fun now(): Instant = instant
    }

    /** A record per reminder, standing in for the database. */
    private class StoredReminder(
        val target: ScheduleTarget,
        val title: String,
        var status: ItemStatus = ItemStatus.ACTIVE,
        var deleted: Boolean = false,
    ) {
        var scheduledFireAt: Instant? = target.scheduledFireAt
        var lastFired: Instant? = target.lastFiredFireAt
        var state: ReminderState = ReminderState.SCHEDULED
        var snoozedUntil: Instant? = null
        var snoozeCount = 0
        var taskCompleted = false
    }

    private class FakeStore : ReminderStore {
        val rows = linkedMapOf<String, StoredReminder>()

        override suspend fun loadTargets() = rows.values.filter { !it.deleted }.map {
            it.target.copy(
                scheduledFireAt = it.scheduledFireAt,
                lastFiredFireAt = it.lastFired,
                itemDone = it.status != ItemStatus.ACTIVE,
                snoozedUntil = it.snoozedUntil,
            )
        }
        override suspend fun idsToUnschedule() = rows.values.filter { it.deleted && it.scheduledFireAt != null }.map { it.target.reminderId }
        override suspend fun setScheduledFireAt(reminderId: String, fireAt: Instant?) {
            rows.getValue(reminderId).scheduledFireAt = fireAt
        }
        override suspend fun fireContext(reminderId: String) = rows[reminderId]?.let {
            FireContext(
                reminderId, it.target.importance, if (it.deleted) ItemStatus.DELETED else it.status, it.title,
                it.target.recurrence != null, it.target.offsetMinutes, snoozeCount = it.snoozeCount,
            )
        }
        override suspend fun markFired(reminderId: String, fireAt: Instant, state: ReminderState): Boolean {
            val row = rows.getValue(reminderId)
            if (row.lastFired == fireAt) return false
            row.lastFired = fireAt
            row.state = state
            if (row.snoozedUntil != fireAt) {
                row.snoozedUntil = null
                row.snoozeCount = 0
            }
            return true
        }
        override suspend fun snooze(reminderId: String, until: Instant): Int {
            val row = rows.getValue(reminderId)
            row.snoozedUntil = until
            row.snoozeCount++
            row.state = ReminderState.SNOOZED
            return row.snoozeCount
        }
        override suspend fun finish(reminderId: String, state: ReminderState) {
            val row = rows.getValue(reminderId)
            row.snoozedUntil = null
            row.snoozeCount = 0
            row.state = state
        }
        override suspend fun completeTask(reminderId: String) {
            val row = rows.getValue(reminderId)
            row.taskCompleted = true
            row.status = ItemStatus.COMPLETED
        }
    }

    /** Like AlarmManager: one alarm per reminder id, so setting again replaces. */
    private class FakeAlarms(var exact: Boolean = true) : AlarmScheduler {
        val set = linkedMapOf<String, Pair<Instant, AlarmKind>>()
        override fun canScheduleExact() = exact
        override fun set(reminderId: String, fireAt: Instant, kind: AlarmKind) {
            set[reminderId] = fireAt to kind
        }
        override fun cancel(reminderId: String) {
            set.remove(reminderId)
        }
    }

    private class FakeDevice(var inputs: PresentationInputs, var call: Boolean = false) : DeviceStateReader {
        override fun presentationInputs() = inputs
        override fun callActive() = call
    }

    private class FakePresenter : ReminderPresenter {
        val shown = mutableListOf<String>()
        override fun showPopup(context: FireContext, level: Importance) {
            shown += "popup:${context.title}:$level"
        }
        override fun postNotification(reminderId: String, title: String, level: Importance) {
            shown += "notification:$title:$level"
        }
        override fun postMissedSummary(count: Int) {
            shown += "summary:$count"
        }
    }

    private data class LogRow(val id: String, val status: ReminderLogStatus, val level: Importance, val reason: ReasonCode?)

    private val inUse = PresentationInputs(true, false, true, true, true)
    private val clock = MutableClock(at("2026-10-08T18:00"), zone)
    private val store = FakeStore()
    private val alarms = FakeAlarms()
    private val device = FakeDevice(inUse)
    private val presenter = FakePresenter()
    private val logRows = mutableListOf<LogRow>()
    private val engine = ReminderEngine(
        store, alarms, device, presenter,
        log = { id, status, level, reason, _, _ -> logRows += LogRow(id, status, level, reason) },
        clock = clock,
        allDayFireTime = { LocalTime.of(9, 0) },
    )

    private fun add(
        id: String,
        due: String,
        importance: Importance = Importance.FULL_SCREEN,
        offset: Int = 0,
        recurrence: RecurrenceRule? = null,
        title: String = "Task $id",
    ): StoredReminder {
        val row = StoredReminder(ScheduleTarget(id, importance, offset, ItemTime.At(at(due), zone), recurrence, false, null, null), title)
        store.rows[id] = row
        return row
    }

    @Test
    public fun rescheduleSetsAlarmsWithTheRightKindAndLogsOnce() = runTest {
        add("full", "2026-10-09T09:00")
        add("normal", "2026-10-09T10:00", Importance.NORMAL)

        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        assertEquals(at("2026-10-09T09:00") to AlarmKind.ALARM_CLOCK, alarms.set["full"])
        assertEquals(at("2026-10-09T10:00") to AlarmKind.EXACT_WHILE_IDLE, alarms.set["normal"])
        // Rescheduling twice replaces instead of duplicating, and logs SCHEDULED only when the time changes.
        assertEquals(2, alarms.set.size)
        assertEquals(2, logRows.count { it.status == ReminderLogStatus.SCHEDULED })
    }

    @Test
    public fun rebootClearsAlarmManagerAndRescheduleRestoresThem() = runTest {
        add("a", "2026-10-09T09:00")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        alarms.set.clear() // what a reboot does
        engine.rescheduleAll(RescheduleTrigger.BOOT)

        assertEquals(at("2026-10-09T09:00"), alarms.set["a"]?.first)
    }

    @Test
    public fun firingDeliversOnceEvenIfTheAlarmArrivesTwice() = runTest {
        add("a", "2026-10-08T18:00", title = "Call mom")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        assertEquals(FireOutcome.DELIVERED, engine.onAlarm("a", at("2026-10-08T18:00")))
        assertEquals(FireOutcome.DUPLICATE, engine.onAlarm("a", at("2026-10-08T18:00")))

        assertEquals(listOf("popup:Call mom:FULL_SCREEN"), presenter.shown)
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.FIRED })
        assertEquals(ReminderState.FIRED, store.rows.getValue("a").state)
    }

    @Test
    public fun firedOneOffHasItsAlarmCleared() = runTest {
        add("a", "2026-10-08T18:00")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        clock.instant = at("2026-10-08T18:00:01")

        engine.onAlarm("a", at("2026-10-08T18:00"))

        assertTrue("a" !in alarms.set)
    }

    @Test
    public fun firedRepeatingItemGetsItsNextOccurrence() = runTest {
        add("daily", "2026-10-01T18:00", recurrence = RecurrenceRule.Daily())
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        clock.instant = at("2026-10-08T18:00:01")

        engine.onAlarm("daily", at("2026-10-08T18:00"))

        assertEquals(at("2026-10-09T18:00"), alarms.set["daily"]?.first)
        assertEquals(ReminderState.SCHEDULED, store.rows.getValue("daily").state)
    }

    @Test
    public fun completedItemIsDroppedAndLogged() = runTest {
        add("a", "2026-10-08T18:00").status = ItemStatus.COMPLETED

        assertEquals(FireOutcome.DROPPED, engine.onAlarm("a", at("2026-10-08T18:00")))

        assertTrue(presenter.shown.isEmpty())
        assertEquals(ReasonCode.ITEM_COMPLETED, logRows.single { it.status == ReminderLogStatus.DISMISSED }.reason)
    }

    @Test
    public fun inCallBecomesANormalNotification() = runTest {
        add("a", "2026-10-08T18:00", Importance.ALARM, title = "Pills")
        device.call = true

        engine.onAlarm("a", at("2026-10-08T18:00"))

        assertEquals(listOf("notification:Pills:NORMAL"), presenter.shown)
        assertEquals(ReasonCode.IN_CALL, logRows.single { it.status == ReminderLogStatus.FIRED }.reason)
    }

    @Test
    public fun nothingShowableIsLoggedAsFailed() = runTest {
        add("a", "2026-10-08T18:00", Importance.NORMAL)
        device.inputs = inUse.copy(notificationsEnabled = false)

        assertEquals(FireOutcome.FAILED, engine.onAlarm("a", at("2026-10-08T18:00")))
        assertEquals(ReasonCode.PERMISSION_MISSING, logRows.single { it.status == ReminderLogStatus.FAILED }.reason)
    }

    @Test
    public fun unknownReminderIsIgnored() = runTest {
        assertEquals(FireOutcome.UNKNOWN_REMINDER, engine.onAlarm("ghost", at("2026-10-08T18:00")))
    }

    @Test
    public fun deletedReminderLosesItsAlarm() = runTest {
        val row = add("a", "2026-10-09T09:00")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        row.deleted = true
        engine.rescheduleAll(RescheduleTrigger.MANUAL)

        assertTrue("a" !in alarms.set)
        assertEquals(null, row.scheduledFireAt)
    }

    @Test
    public fun missedWhileOffGivesOneSummaryAndDeviceOffLogs() = runTest {
        clock.instant = at("2026-10-08T08:00")
        add("a", "2026-10-08T10:00")
        add("b", "2026-10-08T11:00")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        // Phone was off from 09:00 to 12:00: both alarms passed without firing.
        clock.instant = at("2026-10-08T12:00")
        alarms.set.clear()

        engine.rescheduleAll(RescheduleTrigger.BOOT)

        assertEquals(listOf("summary:2"), presenter.shown)
        val missed = logRows.filter { it.status == ReminderLogStatus.MISSED }
        assertEquals(2, missed.size)
        assertTrue(missed.all { it.reason == ReasonCode.DEVICE_OFF })
        // A second reschedule does not report them again.
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        assertEquals(1, presenter.shown.size)
    }

    @Test
    public fun missedEarlyReminderForAFutureEventArrivesAsANormalNotification() = runTest {
        // "1 day before" an event tomorrow 18:00 was due today 18:00; phone off until 19:00.
        clock.instant = at("2026-10-08T08:00")
        add("early", "2026-10-09T18:00", Importance.FULL_SCREEN, offset = 1_440, title = "Concert")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        clock.instant = at("2026-10-08T19:00")
        alarms.set.clear()

        engine.rescheduleAll(RescheduleTrigger.BOOT)

        assertEquals(listOf("notification:Concert:NORMAL"), presenter.shown)
        assertEquals(ReasonCode.DEVICE_OFF, logRows.single { it.status == ReminderLogStatus.FIRED }.reason)
    }

    @Test
    public fun deniedExactAlarmsAreLoggedNotCrashed() = runTest {
        alarms.exact = false
        add("a", "2026-10-09T09:00")

        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        assertEquals(ReasonCode.EXACT_ALARM_DENIED, logRows.single { it.status == ReminderLogStatus.SCHEDULED }.reason)
        assertTrue("a" in alarms.set)
    }

    @Test
    public fun doneCompletesTheTaskAndCancelsItsAlarms() = runTest {
        val row = add("a", "2026-10-09T09:00")
        add("b", "2026-10-09T10:00")
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)

        engine.complete("a")

        assertTrue(row.taskCompleted)
        assertEquals(ReminderState.COMPLETED, row.state)
        assertTrue("a" !in alarms.set)
        assertTrue("b" in alarms.set)
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.COMPLETED })
    }

    @Test
    public fun snoozeSchedulesTheNewTimeAndCountsUp() = runTest {
        val row = add("a", "2026-10-08T18:00")
        engine.onAlarm("a", at("2026-10-08T18:00"))
        clock.instant = at("2026-10-08T18:01")

        engine.snooze("a", at("2026-10-08T18:11"))

        assertEquals(at("2026-10-08T18:11"), alarms.set["a"]?.first)
        assertEquals(1, row.snoozeCount)
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.SNOOZED })
    }

    @Test
    public fun snoozedFiringKeepsTheCountAndANormalFiringResetsIt() = runTest {
        val row = add("a", "2026-10-08T18:00", recurrence = RecurrenceRule.Daily())
        engine.onAlarm("a", at("2026-10-08T18:00"))
        engine.snooze("a", at("2026-10-08T18:10"))
        engine.onAlarm("a", at("2026-10-08T18:10"))
        engine.snooze("a", at("2026-10-08T18:20"))
        assertEquals(2, row.snoozeCount)

        // Tomorrow's normal occurrence starts a fresh count.
        engine.onAlarm("a", at("2026-10-09T18:00"))
        assertEquals(0, row.snoozeCount)
    }

    @Test
    public fun timeoutLogsMissedAndPostsOneQuietNotification() = runTest {
        val row = add("a", "2026-10-08T18:00", title = "Pills")
        engine.onAlarm("a", at("2026-10-08T18:00"))

        engine.timedOut("a", timeoutMinutes = 5)

        assertEquals(ReminderState.MISSED, row.state)
        assertEquals(listOf("popup:Pills:FULL_SCREEN", "notification:Pills:NORMAL"), presenter.shown)
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.MISSED })
    }

    @Test
    public fun dismissAndRescheduleAreLogged() = runTest {
        add("a", "2026-10-08T18:00")
        add("b", "2026-10-08T18:00")

        engine.dismiss("a")
        engine.openedEditor("b")

        assertEquals(1, logRows.count { it.status == ReminderLogStatus.DISMISSED })
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.RESCHEDULED })
    }
}
