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
import com.sanjith.betterlife.domain.settings.AppSettings
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
        var eventEnd: Instant? = null
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
                it.target.recurrence != null, it.target.offsetMinutes, eventEnd = it.eventEnd, snoozeCount = it.snoozeCount,
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
        override suspend fun heldReminderIds() = rows.values.filter { it.state == ReminderState.HELD }.map { it.target.reminderId }
        override suspend fun scheduledIds() = rows.values.filter { it.scheduledFireAt != null }.map { it.target.reminderId }
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
        var digestAlarm: Instant? = null
        override fun setDigest(at: Instant) {
            digestAlarm = at
        }
        override fun cancelDigest() {
            digestAlarm = null
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
        override fun postDigest(titles: List<String>) {
            shown += "digest:${titles.joinToString("|")}"
        }
        override fun postCalmNotice(until: Instant) {
            shown += "calm:$until"
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
    private val calmStore = InMemoryCalmStateStore()
    private var appSettings = AppSettings()
    private var rangeUntil: Instant? = null
    private val engine = ReminderEngine(
        store, alarms, device, presenter,
        log = { id, status, level, reason, _, _ -> logRows += LogRow(id, status, level, reason) },
        clock = clock,
        allDayFireTime = { LocalTime.of(9, 0) },
        calmStore = calmStore,
        settings = { appSettings },
        rangePauseUntil = { now -> rangeUntil?.takeIf { now.isBefore(it) } },
    )

    // Prompt 17: a habit pause range (vacation / sick) holds task reminders like "I'm busy".

    @Test
    public fun aHabitPauseRangeHoldsTaskRemindersButAlarmsStillRing() = runTest {
        rangeUntil = at("2026-10-10T03:00")
        add("a", "2026-10-08T18:00", title = "Call mom")
        add("b", "2026-10-08T18:00", importance = Importance.ALARM, title = "Medicine")

        assertEquals(FireOutcome.HELD, engine.onAlarm("a", at("2026-10-08T18:00")))
        assertTrue(LogRow("a", ReminderLogStatus.HELD, Importance.FULL_SCREEN, ReasonCode.PAUSED) in logRows)
        assertEquals(at("2026-10-10T03:00"), alarms.digestAlarm)
        assertEquals(FireOutcome.DELIVERED, engine.onAlarm("b", at("2026-10-08T18:00")))
    }

    @Test
    public fun heldRemindersArriveTogetherWhenThePauseRangeEnds() = runTest {
        rangeUntil = at("2026-10-10T03:00")
        add("a", "2026-10-08T18:00", title = "Call mom")
        engine.onAlarm("a", at("2026-10-08T18:00"))

        // Still inside the range: the digest waits.
        clock.instant = at("2026-10-09T12:00")
        engine.deliverDigest()
        assertTrue(presenter.shown.none { it.startsWith("digest") })

        clock.instant = at("2026-10-10T03:00")
        engine.deliverDigest()
        assertEquals("digest:Call mom", presenter.shown.last())
    }

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
    public fun noAnswerSnoozesItselfAndRingsAgainUntilAnswered() = runTest {
        val row = add("a", "2026-10-08T18:00", title = "Pills")
        engine.onAlarm("a", at("2026-10-08T18:00"))

        repeat(3) { round ->
            clock.instant = at("2026-10-08T18:00").plusSeconds(round * 900L + 300)
            engine.timedOut("a", timeoutMinutes = 5)
            val until = clock.instant.plusSeconds(600)
            assertEquals(ReminderState.SNOOZED, row.state)
            assertEquals(until, row.snoozedUntil)
            assertEquals(until, alarms.set["a"]?.first)

            clock.instant = until
            assertEquals(FireOutcome.DELIVERED, engine.onAlarm("a", until))
        }

        assertEquals(List(4) { "popup:Pills:FULL_SCREEN" }, presenter.shown)
        assertEquals(3, logRows.count { it.status == ReminderLogStatus.SNOOZED })
        assertEquals(0, logRows.count { it.status == ReminderLogStatus.MISSED })
    }

    @Test
    public fun theAutomaticSnoozeFollowsTheOwnersSetting() = runTest {
        appSettings = AppSettings(autoSnoozeMinutes = 3)
        val row = add("a", "2026-10-08T18:00")
        engine.onAlarm("a", at("2026-10-08T18:00"))
        engine.timedOut("a", 5)
        assertEquals(at("2026-10-08T18:03"), row.snoozedUntil)
    }

    @Test
    public fun anEventPastItsEndBecomesMissedInsteadOfRingingAgain() = runTest {
        val row = add("a", "2026-10-08T18:00", title = "Meeting")
        row.eventEnd = at("2026-10-08T18:05")
        engine.onAlarm("a", at("2026-10-08T18:00"))

        engine.timedOut("a", 5)

        assertEquals(ReminderState.MISSED, row.state)
        assertEquals("notification:Meeting:NORMAL", presenter.shown.last())
        assertEquals(1, logRows.count { it.status == ReminderLogStatus.MISSED })
    }

    @Test
    public fun aReminderComingBackIsNeverDowngradedByFatigue() = runTest {
        calmStore.update { it.copy(calmUntil = at("2026-10-09T18:00")) }
        add("a", "2026-10-08T18:00", title = "Pills")
        engine.snooze("a", at("2026-10-08T18:10"))

        clock.instant = at("2026-10-08T18:10")
        engine.onAlarm("a", at("2026-10-08T18:10"))

        assertEquals(listOf("popup:Pills:FULL_SCREEN"), presenter.shown)
    }

    @Test
    public fun aPhoneCallOnlyDelaysAPopUp() = runTest {
        device.call = true
        val row = add("a", "2026-10-08T18:00", title = "Pills")

        engine.onAlarm("a", at("2026-10-08T18:00"))

        assertEquals(listOf("notification:Pills:NORMAL"), presenter.shown)
        assertEquals(at("2026-10-08T18:10"), row.snoozedUntil)
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

    // Prompt 12: Pause, Quiet Hours digest, fatigue counters

    @Test
    public fun aPausedReminderIsHeldAndTheDigestAlarmIsSetForThePauseEnd() = runTest {
        add("a", "2026-10-08T18:00", title = "Call mom")
        engine.startPause(at("2026-10-08T19:00"), holdsCalendar = false, holdsAlarm = false)
        assertEquals(at("2026-10-08T19:00"), alarms.digestAlarm)

        assertEquals(FireOutcome.HELD, engine.onAlarm("a", at("2026-10-08T18:00")))

        assertTrue(presenter.shown.isEmpty())
        assertEquals(ReminderState.HELD, store.rows.getValue("a").state)
        assertEquals(LogRow("a", ReminderLogStatus.HELD, Importance.FULL_SCREEN, ReasonCode.PAUSED), logRows.last())
    }

    @Test
    public fun whenThePauseEndsHeldRemindersArriveAsOneDigest() = runTest {
        add("a", "2026-10-08T18:00", title = "Call mom")
        add("b", "2026-10-08T18:10", title = "Pay bill")
        engine.startPause(at("2026-10-08T19:00"), holdsCalendar = false, holdsAlarm = false)
        engine.onAlarm("a", at("2026-10-08T18:00"))
        clock.instant = at("2026-10-08T18:10")
        engine.onAlarm("b", at("2026-10-08T18:10"))

        clock.instant = at("2026-10-08T19:00")
        engine.deliverDigest()

        assertEquals(listOf("digest:Call mom|Pay bill"), presenter.shown)
        assertEquals(ReminderState.FIRED, store.rows.getValue("a").state)
        assertEquals(2, logRows.count { it.status == ReminderLogStatus.FIRED && it.level == Importance.NORMAL })
        assertEquals(null, alarms.digestAlarm)
        assertEquals(null, calmStore.read().pause)
    }

    @Test
    public fun theDigestWaitsWhileThePauseOrQuietHoursAreStillOn() = runTest {
        appSettings = AppSettings(quietHoursEnabled = true, quietHoursStart = LocalTime.of(18, 30), quietHoursEnd = LocalTime.of(21, 0))
        add("a", "2026-10-08T18:00", title = "Call mom")
        engine.startPause(at("2026-10-08T19:00"), false, false)
        engine.onAlarm("a", at("2026-10-08T18:00"))

        // Pause over at 19:00, but Quiet Hours run until 21:00: the digest moves there.
        clock.instant = at("2026-10-08T19:00")
        engine.deliverDigest()
        assertTrue(presenter.shown.isEmpty())
        assertEquals(at("2026-10-08T21:00"), alarms.digestAlarm)

        clock.instant = at("2026-10-08T21:00")
        engine.deliverDigest()
        assertEquals(listOf("digest:Call mom"), presenter.shown)
    }

    @Test
    public fun thePauseEndsByItselfEvenWithNothingHeld() = runTest {
        engine.startPause(at("2026-10-08T18:30"), false, false)
        assertEquals(at("2026-10-08T18:30"), alarms.digestAlarm)

        clock.instant = at("2026-10-08T18:30")
        engine.deliverDigest()

        assertEquals(null, calmStore.read().pause)
        assertEquals(null, alarms.digestAlarm)
        assertTrue(presenter.shown.isEmpty())
    }

    @Test
    public fun resumeDeliversHeldRemindersStraightAway() = runTest {
        add("a", "2026-10-08T18:00", title = "Call mom")
        engine.startPause(at("2026-10-08T21:00"), false, false)
        engine.onAlarm("a", at("2026-10-08T18:00"))

        engine.resume()

        assertEquals(listOf("digest:Call mom"), presenter.shown)
        assertEquals(null, calmStore.read().pause)
    }

    @Test
    public fun aRebootReArmsThePendingDigest() = runTest {
        engine.startPause(at("2026-10-08T19:00"), false, false)
        alarms.digestAlarm = null // what a reboot does
        engine.rescheduleAll(RescheduleTrigger.BOOT)
        assertEquals(at("2026-10-08T19:00"), alarms.digestAlarm)
    }

    @Test
    public fun aHeldReminderWhoseTaskWasDoneMeanwhileIsLeftOutOfTheDigest() = runTest {
        add("a", "2026-10-08T18:00", title = "Call mom")
        add("b", "2026-10-08T18:00", title = "Pay bill")
        engine.startPause(at("2026-10-08T19:00"), false, false)
        engine.onAlarm("a", at("2026-10-08T18:00"))
        engine.onAlarm("b", at("2026-10-08T18:00"))
        store.rows.getValue("a").status = ItemStatus.COMPLETED

        clock.instant = at("2026-10-08T19:00")
        engine.deliverDigest()

        assertEquals(listOf("digest:Pay bill"), presenter.shown)
        assertEquals(LogRow("a", ReminderLogStatus.DISMISSED, Importance.FULL_SCREEN, ReasonCode.ITEM_COMPLETED), logRows.first { it.id == "a" && it.status == ReminderLogStatus.DISMISSED })
    }

    @Test
    public fun threeIgnoredPopUpsInARowStartADayOfCalmToldOnce() = runTest {
        listOf("a", "b", "c", "d").forEach { add(it, "2026-10-08T18:00") }
        engine.dismiss("a")
        engine.dismiss("b")
        engine.complete("c") // an answer resets the count
        engine.dismiss("a")
        engine.dismiss("b")
        engine.timedOut("c", 5) // an automatic snooze is not ignoring it
        assertTrue(presenter.shown.none { it.startsWith("calm:") })
        engine.dismiss("d")

        val calmUntil = at("2026-10-09T18:00")
        assertEquals(1, presenter.shown.count { it == "calm:$calmUntil" })
        assertEquals(calmUntil, calmStore.read().calmUntil)
        assertEquals(0, calmStore.read().ignoreStreak)
    }

    @Test
    public fun duringTheCalmPeriodPopUpsArriveAsNotifications() = runTest {
        calmStore.update { it.copy(calmUntil = at("2026-10-09T18:00")) }
        add("a", "2026-10-08T18:00", title = "Call mom")

        engine.onAlarm("a", at("2026-10-08T18:00"))

        assertEquals(listOf("notification:Call mom:NORMAL"), presenter.shown)
        assertEquals(ReasonCode.FATIGUE_DOWNGRADED, logRows.last().reason)
    }

    // Prompt 13: before a restore replaces the data, no alarm may be left behind.

    @Test
    public fun cancelAllAlarmsClearsEveryReminderAlarmAndTheDigest() = runTest {
        add("a", "2026-10-09T09:00")
        add("b", "2026-10-09T10:00", Importance.NORMAL)
        engine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
        engine.startPause(at("2026-10-08T19:00"), false, false)
        assertEquals(2, alarms.set.size)

        engine.cancelAllAlarms()

        assertTrue(alarms.set.isEmpty())
        assertEquals(null, alarms.digestAlarm)
    }
}
