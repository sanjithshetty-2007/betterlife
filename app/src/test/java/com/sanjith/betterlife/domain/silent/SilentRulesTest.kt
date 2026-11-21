package com.sanjith.betterlife.domain.silent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

public class SilentRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun t(text: String): LocalTime = LocalTime.parse(text)

    private val mewing = SilentReminder("m", "Mewing", listOf(t("10:00"), t("18:30")), VibrationPattern.PULSE, 20, enabled = true)
    private val water = SilentReminder("w", "Posture", listOf(t("18:30")), VibrationPattern.HEARTBEAT, 10, enabled = true)

    @Test
    public fun timesAreStoredSortedWithoutRepeatsAndBrokenTextIsSkipped() {
        assertEquals("07:30,18:00", SilentRules.timesToText(listOf(t("18:00"), t("07:30"), t("18:00"))))
        assertEquals(listOf(t("07:30"), t("18:00")), SilentRules.textToTimes("18:00, 07:30,nonsense,"))
        assertTrue(SilentRules.textToTimes("").isEmpty())
    }

    @Test
    public fun nextBuzzIsTheNextTimeTodayOrTomorrow() {
        assertEquals(at("2026-11-21T10:00"), SilentRules.nextBuzz(at("2026-11-21T08:00"), zone, listOf(mewing)))
        assertEquals(at("2026-11-21T18:30"), SilentRules.nextBuzz(at("2026-11-21T12:00"), zone, listOf(mewing)))
        assertEquals(at("2026-11-22T10:00"), SilentRules.nextBuzz(at("2026-11-21T19:00"), zone, listOf(mewing)))
    }

    @Test
    public fun aBuzzIsNeverRepeatedAtItsOwnMoment() {
        // Planned strictly after the moment that just fired.
        assertEquals(at("2026-11-21T18:30"), SilentRules.nextBuzz(at("2026-11-21T10:00"), zone, listOf(mewing)))
    }

    @Test
    public fun turnedOffRemindersAndEmptyListsPlanNothing() {
        assertNull(SilentRules.nextBuzz(at("2026-11-21T08:00"), zone, listOf(mewing.copy(enabled = false))))
        assertNull(SilentRules.nextBuzz(at("2026-11-21T08:00"), zone, emptyList()))
        assertNull(SilentRules.nextBuzz(at("2026-11-21T08:00"), zone, listOf(mewing.copy(times = emptyList()))))
    }

    @Test
    public fun theScheduleFollowsATimeZoneChange() {
        val dubai = ZoneId.of("Asia/Dubai")
        val next = SilentRules.nextBuzz(at("2026-11-21T08:00"), dubai, listOf(mewing))
        assertEquals(LocalDateTime.parse("2026-11-21T10:00"), next?.atZone(dubai)?.toLocalDateTime())
    }

    @Test
    public fun dueAtFindsEveryReminderAtThatMinute() {
        val due = SilentRules.dueAt(at("2026-11-21T18:30"), zone, listOf(mewing, water, mewing.copy(id = "off", enabled = false)))
        assertEquals(listOf("Mewing", "Posture"), due.map { it.name })
        assertTrue(SilentRules.dueAt(at("2026-11-21T18:31"), zone, listOf(mewing)).isEmpty())
    }

    @Test
    public fun eachPatternFillsExactlyItsLength() {
        VibrationPattern.entries.forEach { pattern ->
            SilentRules.LENGTHS_SECONDS.forEach { seconds ->
                val w = SilentRules.waveform(pattern, seconds * 1_000L)
                assertEquals("$pattern $seconds s", seconds * 1_000L, w.totalMillis)
                assertEquals(w.timings.size, w.amplitudes.size)
                assertTrue(w.amplitudes.all { it in 0..255 })
                assertTrue(w.amplitudes.any { it > 0 })
            }
        }
    }

    @Test
    public fun remindersDueTogetherPlayOneAfterTheOther() {
        val w = SilentRules.waveformFor(listOf(mewing, water))
        // 20 s, a 0.6 s pause, then 10 s; then it stops (no repeat).
        assertEquals(20_000L + SilentRules.GAP_MILLIS + 10_000L, w?.totalMillis)
        assertNull(SilentRules.waveformFor(emptyList()))
    }

    @Test
    public fun thePreviewIsTwoSeconds() {
        assertEquals(2_000L, SilentRules.waveform(VibrationPattern.WAVE, SilentRules.PREVIEW_MILLIS).totalMillis)
    }
}
