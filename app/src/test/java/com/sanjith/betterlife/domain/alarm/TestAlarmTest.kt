package com.sanjith.betterlife.domain.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

public class TestAlarmTest {

    private val now = 1_800_000_000_000L

    @Test
    public fun triggerAfterOneFiveAndFifteenMinutes() {
        assertEquals(now + 60_000, triggerAfterMinutes(now, 1))
        assertEquals(now + 300_000, triggerAfterMinutes(now, 5))
        assertEquals(now + 900_000, triggerAfterMinutes(now, 15))
    }

    @Test(expected = IllegalArgumentException::class)
    public fun zeroMinutesIsRejected() {
        triggerAfterMinutes(now, 0)
    }

    @Test
    public fun futureAlarmKeepsItsTime() {
        val pending = PendingTestAlarm(now + 120_000, AlarmKind.ALARM_CLOCK)
        assertEquals(RearmPlan.AtOriginalTime(now + 120_000), planRearm(pending, now))
    }

    @Test
    public fun alarmOneMillisecondAheadKeepsItsTime() {
        val pending = PendingTestAlarm(now + 1, AlarmKind.EXACT_ALLOW_WHILE_IDLE)
        assertEquals(RearmPlan.AtOriginalTime(now + 1), planRearm(pending, now))
    }

    @Test
    public fun alarmDueExactlyNowIsOverdue() {
        val pending = PendingTestAlarm(now, AlarmKind.EXACT_ALLOW_WHILE_IDLE)
        assertEquals(RearmPlan.Overdue(fireAtMillis = now + OVERDUE_GRACE_MILLIS, missedByMillis = 0), planRearm(pending, now))
    }

    @Test
    public fun alarmMissedWhileOffFiresAfterGraceAndReportsHowLate() {
        val pending = PendingTestAlarm(now - 420_000, AlarmKind.ALARM_CLOCK)
        assertEquals(
            RearmPlan.Overdue(fireAtMillis = now + OVERDUE_GRACE_MILLIS, missedByMillis = 420_000),
            planRearm(pending, now),
        )
    }

    @Test
    public fun customGraceIsUsed() {
        val pending = PendingTestAlarm(now - 1, AlarmKind.ALARM_CLOCK)
        assertEquals(RearmPlan.Overdue(fireAtMillis = now + 10, missedByMillis = 1), planRearm(pending, now, graceMillis = 10))
    }
}
