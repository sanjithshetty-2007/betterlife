package com.sanjith.betterlife.domain.reminder

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

public class SchedulePlannerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val nine = LocalTime.of(9, 0)
    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val now = at("2026-10-08T18:00")

    private fun target(
        id: String = "r1",
        itemTime: ItemTime? = ItemTime.At(at("2026-10-09T17:00"), zone),
        offset: Int = 0,
        importance: Importance = Importance.NORMAL,
        recurrence: RecurrenceRule? = null,
        done: Boolean = false,
        scheduled: java.time.Instant? = null,
        lastFired: java.time.Instant? = null,
    ) = ScheduleTarget(id, importance, offset, itemTime, recurrence, done, scheduled, lastFired)

    private fun plan(vararg targets: ScheduleTarget) = SchedulePlanner.plan(targets.toList(), now, zone, nine)

    @Test
    public fun futureReminderIsScheduledWithTheRightKind() {
        val p = plan(target(), target("r2", importance = Importance.FULL_SCREEN), target("r3", importance = Importance.ALARM))
        assertEquals(
            listOf(
                PlannedAlarm("r1", at("2026-10-09T17:00"), AlarmKind.EXACT_WHILE_IDLE),
                PlannedAlarm("r2", at("2026-10-09T17:00"), AlarmKind.ALARM_CLOCK),
                PlannedAlarm("r3", at("2026-10-09T17:00"), AlarmKind.ALARM_CLOCK),
            ),
            p.alarms,
        )
        assertTrue(p.missed.isEmpty())
    }

    @Test
    public fun earlyOffsetIsApplied() {
        assertEquals(at("2026-10-09T16:00"), plan(target(offset = 60)).alarms.single().fireAt)
    }

    @Test
    public fun onlyTheNext30DaysAreScheduled() {
        val inside = target("in", itemTime = ItemTime.At(at("2026-11-06T18:00"), zone))
        val outside = target("out", itemTime = ItemTime.At(at("2026-11-08T18:00"), zone))
        val alarms = plan(inside, outside).alarms.associateBy { it.reminderId }
        assertEquals(at("2026-11-06T18:00"), alarms.getValue("in").fireAt)
        assertNull(alarms.getValue("out").fireAt)
    }

    @Test
    public fun pastOneOffThatWasNeverScheduledIsNotMissed() {
        val p = plan(target(itemTime = ItemTime.At(at("2026-10-01T10:00"), zone)))
        assertNull(p.alarms.single().fireAt)
        assertTrue(p.missed.isEmpty())
    }

    @Test
    public fun doneOrTimelessItemsGetNoAlarm() {
        val p = plan(target("done", done = true), target("timeless", itemTime = null))
        assertTrue(p.alarms.all { it.fireAt == null })
    }

    @Test
    public fun repeatingItemSchedulesOnlyItsNextOccurrence() {
        val daily = target(itemTime = ItemTime.At(at("2026-09-01T09:00"), zone), recurrence = RecurrenceRule.Daily())
        assertEquals(at("2026-10-09T09:00"), plan(daily).alarms.single().fireAt)
    }

    @Test
    public fun repeatingItemSkipsTheOccurrenceAlreadyDelivered() {
        val daily = target(
            itemTime = ItemTime.At(at("2026-09-01T18:00"), zone),
            recurrence = RecurrenceRule.Daily(),
            scheduled = at("2026-10-08T18:00"),
            lastFired = at("2026-10-08T18:00"),
        )
        assertEquals(at("2026-10-09T18:00"), plan(daily).alarms.single().fireAt)
    }

    @Test
    public fun weeklyMeetingNextOccurrence() {
        val weekly = target(
            itemTime = ItemTime.At(at("2026-10-05T10:00"), zone),
            recurrence = RecurrenceRule.Weekly(setOf(java.time.DayOfWeek.MONDAY)),
            offset = 10,
        )
        assertEquals(at("2026-10-12T09:50"), plan(weekly).alarms.single().fireAt)
    }

    @Test
    public fun birthdayOneDayBeforeAt0900() {
        val birthday = target(itemTime = ItemTime.AllDay(LocalDate.of(2026, 10, 20)), offset = ReminderOffsets.DAY_1)
        assertEquals(at("2026-10-19T09:00"), plan(birthday).alarms.single().fireAt)
    }

    @Test
    public fun yearlyBirthdayAlreadyPastThisYearIsNotScheduledYet() {
        // Next is 1 Oct 2027, outside the 30-day window: the daily top-up will set it later.
        val birthday = target(itemTime = ItemTime.AllDay(LocalDate.of(2020, 10, 1)), recurrence = RecurrenceRule.Yearly(java.time.Month.OCTOBER, 1))
        assertNull(plan(birthday).alarms.single().fireAt)
    }

    @Test
    public fun alarmPassedLongAgoWithoutFiringIsMissed() {
        val t = target(itemTime = ItemTime.At(at("2026-10-08T10:00"), zone), scheduled = at("2026-10-08T10:00"))
        val p = plan(t)
        assertEquals(listOf(MissedReminder(t, at("2026-10-08T10:00"))), p.missed)
        assertNull(p.alarms.single().fireAt)
    }

    @Test
    public fun alarmJustLateIsSetAgainToFireNow() {
        val t = target(itemTime = ItemTime.At(at("2026-10-08T17:59"), zone), scheduled = at("2026-10-08T17:59"))
        val p = plan(t)
        assertTrue(p.missed.isEmpty())
        assertEquals(at("2026-10-08T17:59"), p.alarms.single().fireAt)
    }

    @Test
    public fun alarmAlreadyDeliveredIsNotMissed() {
        val t = target(itemTime = ItemTime.At(at("2026-10-08T10:00"), zone), scheduled = at("2026-10-08T10:00"), lastFired = at("2026-10-08T10:00"))
        assertTrue(plan(t).missed.isEmpty())
    }

    @Test
    public fun missedRepeatingItemStillGetsItsNextAlarm() {
        val t = target(
            itemTime = ItemTime.At(at("2026-09-01T10:00"), zone),
            recurrence = RecurrenceRule.Daily(),
            scheduled = at("2026-10-08T10:00"),
        )
        val p = plan(t)
        assertEquals(1, p.missed.size)
        assertEquals(at("2026-10-09T10:00"), p.alarms.single().fireAt)
    }

    @Test
    public fun neverMoreThan400AlarmsAndTheEarliestWin() {
        val targets = (0 until 600).map { i ->
            target("r$i", itemTime = ItemTime.At(now.plusSeconds(60L * (i + 1)), zone))
        }
        val alarms = SchedulePlanner.plan(targets, now, zone, nine).alarms
        assertEquals(SchedulePlanner.MAX_ALARMS, alarms.count { it.fireAt != null })
        assertTrue(alarms.filter { it.fireAt != null }.all { it.reminderId.removePrefix("r").toInt() < SchedulePlanner.MAX_ALARMS })
    }

    @Test
    public fun planningIsDeterministic() {
        val targets = listOf(target("a"), target("b", offset = 30))
        assertEquals(SchedulePlanner.plan(targets, now, zone, nine), SchedulePlanner.plan(targets, now, zone, nine))
    }
}
