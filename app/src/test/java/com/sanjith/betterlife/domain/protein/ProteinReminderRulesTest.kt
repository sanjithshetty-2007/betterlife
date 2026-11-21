package com.sanjith.betterlife.domain.protein

import com.sanjith.betterlife.domain.time.habitDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

public class ProteinReminderRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val eight = LocalTime.of(20, 0)

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun dayOf(instant: Instant): LocalDate = habitDay(instant, zone, rolloverHour = 3)

    private fun next(now: String, repeatAt: Instant? = null, logged: Set<String> = emptySet()) =
        ProteinReminderRules.nextFire(at(now), zone, eight, repeatAt, ::dayOf) { it.toString() in logged }

    @Test
    public fun timingsAreTenSecondsTwoMinutesAndFifteenMinutes() {
        assertEquals(10, ProteinReminderRules.RING.seconds)
        assertEquals(2, ProteinReminderRules.POPUP.toMinutes())
        assertEquals(15, ProteinReminderRules.SNOOZE.toMinutes())
    }

    @Test
    public fun firesAtTheChosenTimeTodayOrTomorrow() {
        assertEquals(at("2026-11-21T20:00"), next("2026-11-21T09:00"))
        assertEquals(at("2026-11-22T20:00"), next("2026-11-21T20:00"))
        assertEquals(at("2026-11-22T20:00"), next("2026-11-21T21:30"))
    }

    @Test
    public fun aLoggedDaySkipsToTheNextDay() {
        assertEquals(at("2026-11-22T20:00"), next("2026-11-21T09:00", logged = setOf("2026-11-21")))
    }

    @Test
    public fun repeatComesSeventeenMinutesAfterFiringUntilMidnight() {
        assertEquals(at("2026-11-21T20:17"), ProteinReminderRules.repeatAfterFire(at("2026-11-21T20:00"), zone))
        assertEquals(at("2026-11-21T23:59"), ProteinReminderRules.repeatAfterFire(at("2026-11-21T23:42"), zone))
        assertNull(ProteinReminderRules.repeatAfterFire(at("2026-11-21T23:43"), zone))
        assertEquals(at("2026-11-21T20:15"), ProteinReminderRules.repeatAfterCall(at("2026-11-21T20:00"), zone))
    }

    @Test
    public fun aPendingRepeatWinsUntilItsDayIsLogged() {
        val repeat = at("2026-11-21T20:17")
        assertEquals(repeat, next("2026-11-21T20:02", repeatAt = repeat))
        assertEquals(at("2026-11-22T20:00"), next("2026-11-21T20:05", repeatAt = repeat, logged = setOf("2026-11-21")))
    }

    @Test
    public fun aRepeatMissedWhileThePhoneWasOffFiresNowButNotAfterMidnight() {
        val repeat = at("2026-11-21T20:17")
        assertEquals(at("2026-11-21T21:00"), next("2026-11-21T21:00", repeatAt = repeat))
        assertEquals(at("2026-11-22T20:00"), next("2026-11-22T00:10", repeatAt = repeat))
    }

    @Test
    public fun theScheduleFollowsATimeZoneChange() {
        val dubai = ZoneId.of("Asia/Dubai")
        val now = at("2026-11-21T09:00")
        val fire = ProteinReminderRules.nextFire(now, dubai, eight, null, { habitDay(it, dubai, 3) }) { false }
        assertEquals(LocalDateTime.parse("2026-11-21T20:00"), fire.atZone(dubai).toLocalDateTime())
    }
}
