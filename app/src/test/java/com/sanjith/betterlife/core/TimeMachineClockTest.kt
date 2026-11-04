package com.sanjith.betterlife.core

import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.habitToday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

public class TimeMachineClockTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val real = FixedClock.of("2026-10-08T18:00", "Asia/Kolkata")

    @Test
    public fun startsAtRealTime() {
        assertEquals(real.now(), TimeMachineClock(real).now())
    }

    @Test
    public fun shiftsAddUpAndResetReturnsToRealTime() {
        val machine = TimeMachineClock(real)
        machine.shift(Duration.ofDays(1))
        machine.shift(Duration.ofHours(-2))
        assertEquals(real.now().plus(Duration.ofHours(22)), machine.now())
        machine.reset()
        assertEquals(real.now(), machine.now())
    }

    @Test
    public fun jumpingTo0100TonightIsStillTodayForHabits() {
        val machine = TimeMachineClock(real)
        machine.setTo(LocalDateTime.parse("2026-10-09T01:00").atZone(zone).toInstant())
        assertEquals(LocalDate.of(2026, 10, 8), machine.habitToday(rolloverHour = 3))
    }
}
