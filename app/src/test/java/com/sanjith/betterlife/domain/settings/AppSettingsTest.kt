package com.sanjith.betterlife.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

public class AppSettingsTest {

    @Test
    public fun defaultsMatchContextMd() {
        val s = AppSettings()
        assertEquals(3, s.rolloverHour)
        assertEquals(LocalTime.of(20, 0), s.snoozeTonight)
        assertEquals(LocalTime.of(9, 0), s.snoozeTomorrow)
        assertEquals(LocalTime.of(9, 0), s.birthdayFireTime)
        assertEquals(5, s.popupTimeoutMinutes)
        assertFalse(s.quietHoursEnabled)
        assertEquals(LocalTime.of(22, 0), s.quietHoursStart)
        assertEquals(LocalTime.of(7, 0), s.quietHoursEnd)
        assertEquals(3, s.fatigueMaxFullScreenPerHour)
        assertEquals(10, s.fatigueGroupWindowMinutes)
        assertEquals(3, s.fatigueIgnoredLimit)
        assertTrue(s.fatigueDowngradeEnabled)
        assertTrue(s.alarmExemptFromFatigue)
        assertTrue(s.pauseExemptsEventsAndAlarms)
        assertEquals(DayOfWeek.MONDAY, s.weekStart)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun rolloverAfterFiveIsRejected() {
        AppSettings(rolloverHour = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun negativeRolloverIsRejected() {
        AppSettings(rolloverHour = -1)
    }

    @Test
    public fun midnightRolloverIsAllowed() {
        assertEquals(0, AppSettings(rolloverHour = 0).rolloverHour)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun zeroFullScreenPerHourIsRejected() {
        AppSettings(fatigueMaxFullScreenPerHour = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun zeroPopupTimeoutIsRejected() {
        AppSettings(popupTimeoutMinutes = 0)
    }
}
