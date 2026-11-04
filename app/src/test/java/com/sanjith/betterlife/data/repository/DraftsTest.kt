package com.sanjith.betterlife.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

public class DraftsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test(expected = IllegalArgumentException::class)
    public fun blankTaskTitleIsRejected() {
        TaskDraft(title = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    public fun taskCannotBeDueAtAnInstantAndOnADay() {
        TaskDraft(title = "x", dueAtMillis = 1L, dueZoneId = zone, dueDate = LocalDate.of(2026, 10, 8))
    }

    @Test(expected = IllegalArgumentException::class)
    public fun instantWithoutZoneIsRejected() {
        TaskDraft(title = "x", dueAtMillis = 1L)
    }

    @Test
    public fun undatedTaskIsAllowed() {
        assertEquals("anniversary gift", TaskDraft(title = "anniversary gift").title)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun timedEventWithoutZoneIsRejected() {
        EventDraft(title = "Meeting", startAtMillis = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun allDayEventWithInstantIsRejected() {
        EventDraft(title = "Birthday", allDay = true, startDate = LocalDate.of(2026, 2, 28), startAtMillis = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun eventEndingBeforeStartIsRejected() {
        EventDraft(title = "Meeting", startAtMillis = 2_000L, endAtMillis = 1_000L, zoneId = zone)
    }

    @Test
    public fun eventEndingAtStartIsAllowed() {
        EventDraft(title = "Meeting", startAtMillis = 1_000L, endAtMillis = 1_000L, zoneId = zone)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun negativeReminderOffsetIsRejected() {
        ReminderDraft(offsetMinutes = -5)
    }
}
