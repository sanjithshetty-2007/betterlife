package com.sanjith.betterlife.domain.delivery

import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pipeline steps 4 to 6 (Prompt 12): Pause, Quiet Hours, fatigue, and their precedence. */
public class DeliveryPipelineCalmTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
    private val now = at("2026-11-06T14:00")
    private val inUse = PresentationInputs(isInteractive = true, isKeyguardLocked = false, canDrawOverlays = true, canUseFullScreenIntent = true, notificationsEnabled = true)

    private val pause = Pause(until = at("2026-11-06T15:00"), holdsCalendar = false, holdsAlarm = false)

    private fun fatigue(
        recent: List<Instant> = emptyList(),
        calmUntil: Instant? = null,
        max: Int = 3,
        window: Long = 10,
        downgrade: Boolean = true,
        alarmExempt: Boolean = true,
    ) = FatigueInputs(max, Duration.ofMinutes(window), recent, calmUntil, downgrade, alarmExempt)

    private fun decide(
        level: Importance = Importance.FULL_SCREEN,
        calendar: Boolean = false,
        calm: CalmInputs = CalmInputs.NONE,
        call: Boolean = false,
    ) = DeliveryPipeline.decide(DeliveryInput(level, ItemStatus.ACTIVE, inUse, call, now, calendar, calm))

    // Pause

    @Test
    public fun pauseHoldsTaskRemindersOfNormalAndPopUpLevelUntilItEnds() {
        listOf(Importance.NORMAL, Importance.FULL_SCREEN).forEach { level ->
            val d = decide(level, calm = CalmInputs(pause = pause))
            assertEquals(DeliveryAction.HOLD, d.action)
            assertEquals(ReasonCode.PAUSED, d.reasonCode)
            assertEquals(pause.until, d.holdUntil)
        }
    }

    @Test
    public fun calendarItemsAndAlarmLevelAreExemptByDefault() {
        assertEquals(DeliveryAction.DELIVER, decide(calendar = true, calm = CalmInputs(pause = pause)).action)
        assertEquals(DeliveryAction.DELIVER, decide(Importance.ALARM, calm = CalmInputs(pause = pause)).action)
    }

    @Test
    public fun theOwnerCanHoldCalendarItemsAndAlarmsToo() {
        val holdsAll = pause.copy(holdsCalendar = true, holdsAlarm = true)
        assertEquals(DeliveryAction.HOLD, decide(calendar = true, calm = CalmInputs(pause = holdsAll)).action)
        assertEquals(DeliveryAction.HOLD, decide(Importance.ALARM, calm = CalmInputs(pause = holdsAll)).action)
    }

    @Test
    public fun anEndedPauseHoldsNothing() {
        assertEquals(DeliveryAction.DELIVER, decide(calm = CalmInputs(pause = pause.copy(until = now))).action)
    }

    // Quiet Hours

    @Test
    public fun quietHoursHoldEverythingUntilTheWindowEnds() {
        val d = decide(Importance.NORMAL, calendar = true, calm = CalmInputs(quietUntil = at("2026-11-07T07:00")))
        assertEquals(DeliveryAction.HOLD, d.action)
        assertEquals(ReasonCode.QUIET_HOURS, d.reasonCode)
        assertEquals(at("2026-11-07T07:00"), d.holdUntil)
    }

    @Test
    public fun alarmLevelPassesQuietHoursOnlyWhileTheSettingAllowsIt() {
        val quiet = CalmInputs(quietUntil = at("2026-11-07T07:00"))
        assertEquals(DeliveryAction.DELIVER, decide(Importance.ALARM, calm = quiet).action)
        assertEquals(DeliveryAction.HOLD, decide(Importance.ALARM, calm = quiet.copy(alarmBypassesQuiet = false)).action)
    }

    // Precedence: pause beats quiet hours beats fatigue

    @Test
    public fun pauseBeatsQuietHours() {
        val d = decide(calm = CalmInputs(pause = pause, quietUntil = at("2026-11-07T07:00")))
        assertEquals(ReasonCode.PAUSED, d.reasonCode)
        assertEquals(pause.until, d.holdUntil)
    }

    @Test
    public fun quietHoursBeatFatigue() {
        val calm = CalmInputs(quietUntil = at("2026-11-07T07:00"), fatigue = fatigue(recent = List(5) { now.minusSeconds(60L * (it + 1)) }))
        val d = decide(calm = calm)
        assertEquals(DeliveryAction.HOLD, d.action)
        assertEquals(ReasonCode.QUIET_HOURS, d.reasonCode)
    }

    @Test
    public fun aPauseHoldsEvenDuringACall() {
        assertEquals(ReasonCode.PAUSED, decide(call = true, calm = CalmInputs(pause = pause)).reasonCode)
    }

    @Test
    public fun inCallStillTurnsAPopUpIntoANotificationWhenNothingHolds() {
        val d = decide(call = true, calm = CalmInputs(fatigue = fatigue()))
        assertEquals(Importance.NORMAL, d.level)
        assertEquals(ReasonCode.IN_CALL, d.reasonCode)
    }

    // Fatigue: hourly maximum

    @Test
    public fun theFourthPopUpWithinAnHourBecomesANormalNotification() {
        val three = listOf(now.minusSeconds(15 * 60), now.minusSeconds(30 * 60), now.minusSeconds(45 * 60))
        val d = decide(calm = CalmInputs(fatigue = fatigue(recent = three)))
        assertEquals(DeliveryAction.DELIVER, d.action)
        assertEquals(Importance.NORMAL, d.level)
        assertEquals(PresentationPath.NOTIFICATION_ONLY, d.path)
        assertEquals(ReasonCode.FATIGUE_DOWNGRADED, d.reasonCode)
        assertEquals(Importance.FULL_SCREEN, decide(calm = CalmInputs(fatigue = fatigue(recent = three.take(2)))).level)
    }

    @Test
    public fun theHourIsRollingSoAPopUpOverAnHourAgoNoLongerCounts() {
        val boundary = listOf(now.minusSeconds(15 * 60), now.minusSeconds(30 * 60), now.minus(Duration.ofHours(1)))
        assertEquals(Importance.FULL_SCREEN, decide(calm = CalmInputs(fatigue = fatigue(recent = boundary))).level)
        val justInside = boundary.dropLast(1) + now.minus(Duration.ofHours(1)).plusSeconds(1)
        assertEquals(Importance.NORMAL, decide(calm = CalmInputs(fatigue = fatigue(recent = justInside))).level)
    }

    // Fatigue: grouping

    @Test
    public fun aPopUpWithinTheGroupingWindowJoinsTheLastOne() {
        val d = decide(calm = CalmInputs(fatigue = fatigue(recent = listOf(now.minusSeconds(4 * 60)))))
        assertEquals(Importance.FULL_SCREEN, d.level)
        assertTrue(d.grouped)
        assertEquals(ReasonCode.FATIGUE_GROUPED, d.reasonCode)
    }

    @Test
    public fun groupingWindowEdgesAreInclusiveAtExactlyTenMinutes() {
        assertTrue(decide(calm = CalmInputs(fatigue = fatigue(recent = listOf(now.minusSeconds(10 * 60))))).grouped)
        assertFalse(decide(calm = CalmInputs(fatigue = fatigue(recent = listOf(now.minusSeconds(10 * 60 + 1))))).grouped)
        assertFalse(decide(calm = CalmInputs(fatigue = fatigue(recent = listOf(now.minusSeconds(60)), window = 0))).grouped)
    }

    @Test
    public fun aGroupedPopUpIsNotDowngradedEvenWhenTheHourIsFull() {
        val full = listOf(now.minusSeconds(5 * 60), now.minusSeconds(20 * 60), now.minusSeconds(40 * 60))
        val d = decide(calm = CalmInputs(fatigue = fatigue(recent = full)))
        assertTrue(d.grouped)
        assertEquals(Importance.FULL_SCREEN, d.level)
    }

    // Fatigue: calm after ignores, and exemptions

    @Test
    public fun calmPeriodDowngradesPopUpsUnlessTurnedOff() {
        val calm = fatigue(calmUntil = now.plus(Duration.ofHours(20)))
        assertEquals(ReasonCode.FATIGUE_DOWNGRADED, decide(calm = CalmInputs(fatigue = calm)).reasonCode)
        assertEquals(Importance.FULL_SCREEN, decide(calm = CalmInputs(fatigue = calm.copy(downgradeEnabled = false))).level)
        assertEquals(Importance.FULL_SCREEN, decide(calm = CalmInputs(fatigue = calm.copy(calmUntil = now))).level)
    }

    @Test
    public fun alarmLevelIsExemptFromFatigueUnlessTheOwnerSaysOtherwise() {
        val busy = fatigue(recent = List(3) { now.minusSeconds(60L * 20 * (it + 1)) }, calmUntil = now.plusSeconds(60))
        assertEquals(Importance.ALARM, decide(Importance.ALARM, calm = CalmInputs(fatigue = busy)).level)
        assertEquals(Importance.NORMAL, decide(Importance.ALARM, calm = CalmInputs(fatigue = busy.copy(alarmExempt = false))).level)
    }

    @Test
    public fun normalRemindersAreNeverTouchedByFatigue() {
        val d = decide(Importance.NORMAL, calm = CalmInputs(fatigue = fatigue(recent = listOf(now.minusSeconds(60)), calmUntil = now.plusSeconds(60))))
        assertNull(d.reasonCode)
        assertFalse(d.grouped)
    }

    // Pause options

    @Test
    public fun pauseOptionsEndAtTheRightTime() {
        val zoned = LocalDateTime.parse("2026-11-06T14:10").atZone(zone)
        val tonight = LocalTime.of(20, 0)
        assertEquals(at("2026-11-06T14:40"), PauseRules.endFor(PauseOption.MINUTES_30, zoned, tonight))
        assertEquals(at("2026-11-06T15:10"), PauseRules.endFor(PauseOption.HOUR_1, zoned, tonight))
        assertEquals(at("2026-11-06T17:10"), PauseRules.endFor(PauseOption.HOURS_3, zoned, tonight))
        assertEquals(at("2026-11-06T20:00"), PauseRules.endFor(PauseOption.TONIGHT, zoned, tonight))
        // After 20:00 "until tonight" is not offered.
        assertNull(PauseRules.endFor(PauseOption.TONIGHT, LocalDateTime.parse("2026-11-06T20:00").atZone(zone), tonight))
    }

    // Ignore streak

    @Test
    public fun threeIgnoresInARowStartADayOfCalmAndAnAnswerResetsTheCount() {
        var r = IgnoreStreak.onIgnored(0, null, 3, true, now)
        r = IgnoreStreak.onIgnored(r.streak, r.calmUntil, 3, true, now)
        assertEquals(2, r.streak)
        assertFalse(r.calmStarted)
        assertEquals(0, IgnoreStreak.onAnswered(r.calmUntil).streak)
        val third = IgnoreStreak.onIgnored(r.streak, r.calmUntil, 3, true, now)
        assertTrue(third.calmStarted)
        assertEquals(now.plus(Duration.ofHours(24)), third.calmUntil)
        assertEquals(0, third.streak)
        // Turned off: counting goes on but no calm starts.
        assertFalse(IgnoreStreak.onIgnored(2, null, 3, false, now).calmStarted)
    }
}
