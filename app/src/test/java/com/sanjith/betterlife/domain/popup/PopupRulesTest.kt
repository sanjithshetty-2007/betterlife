package com.sanjith.betterlife.domain.popup

import com.sanjith.betterlife.domain.model.Importance
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
import java.time.ZonedDateTime

public class PopupRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(local: String): ZonedDateTime = LocalDateTime.parse(local).atZone(zone)
    private val tonight = LocalTime.of(20, 0)
    private val tomorrow = LocalTime.of(9, 0)

    private fun options(now: String, eventEnd: Instant? = null) =
        SnoozeRules.options(at(now), tonight, tomorrow, rolloverHour = 3, eventEnd = eventEnd).associateBy { it.choice }

    // Snooze options

    @Test
    public fun fixedSnoozesAddTenThirtyAndSixtyMinutes() {
        val o = options("2026-10-08T18:00")
        assertEquals(at("2026-10-08T18:10").toInstant(), o.getValue(SnoozeChoice.MINUTES_10).at)
        assertEquals(at("2026-10-08T18:30").toInstant(), o.getValue(SnoozeChoice.MINUTES_30).at)
        assertEquals(at("2026-10-08T19:00").toInstant(), o.getValue(SnoozeChoice.HOUR_1).at)
    }

    @Test
    public fun tonightAndTomorrowAtTheirDefaultTimes() {
        val o = options("2026-10-08T18:00")
        assertEquals(at("2026-10-08T20:00").toInstant(), o.getValue(SnoozeChoice.TONIGHT).at)
        assertEquals(at("2026-10-09T09:00").toInstant(), o.getValue(SnoozeChoice.TOMORROW).at)
        assertTrue(o.values.all { it.allowed })
    }

    @Test
    public fun tonightIsNotAllowedAfter2000() {
        val o = options("2026-10-08T21:00")
        assertNull(o.getValue(SnoozeChoice.TONIGHT).at)
        assertFalse(o.getValue(SnoozeChoice.TONIGHT).allowed)
    }

    @Test
    public fun tonightExactlyAt2000IsNotAllowed() {
        assertFalse(options("2026-10-08T20:00").getValue(SnoozeChoice.TONIGHT).allowed)
    }

    @Test
    public fun afterMidnightTomorrowMeansThisMorning() {
        assertEquals(at("2026-10-09T09:00").toInstant(), options("2026-10-09T01:00").getValue(SnoozeChoice.TOMORROW).at)
    }

    @Test
    public fun afterTheRolloverTomorrowIsTheNextDay() {
        assertEquals(at("2026-10-10T09:00").toInstant(), options("2026-10-09T03:30").getValue(SnoozeChoice.TOMORROW).at)
    }

    @Test
    public fun pickTimeIsAlwaysOfferedWithoutATime() {
        val pick = options("2026-10-08T18:00").getValue(SnoozeChoice.PICK_TIME)
        assertNull(pick.at)
        assertTrue(pick.allowed)
    }

    @Test
    public fun eventsCannotBeSnoozedPastTheirEnd() {
        val o = options("2026-10-08T18:00", eventEnd = at("2026-10-08T18:45").toInstant())
        assertTrue(o.getValue(SnoozeChoice.MINUTES_10).allowed)
        assertTrue(o.getValue(SnoozeChoice.MINUTES_30).allowed)
        assertFalse(o.getValue(SnoozeChoice.HOUR_1).allowed)
        assertFalse(o.getValue(SnoozeChoice.TONIGHT).allowed)
        assertFalse(o.getValue(SnoozeChoice.TOMORROW).allowed)
    }

    @Test
    public fun snoozeExactlyToTheEventEndIsAllowed() {
        assertTrue(options("2026-10-08T18:00", eventEnd = at("2026-10-08T18:10").toInstant()).getValue(SnoozeChoice.MINUTES_10).allowed)
    }

    @Test
    public fun pickedTimeLaterTodayOrTomorrow() {
        assertEquals(at("2026-10-08T19:15").toInstant(), SnoozeRules.pickedTime(at("2026-10-08T18:00"), LocalTime.of(19, 15)))
        assertEquals(at("2026-10-09T07:00").toInstant(), SnoozeRules.pickedTime(at("2026-10-08T18:00"), LocalTime.of(7, 0)))
        assertEquals(at("2026-10-09T18:00").toInstant(), SnoozeRules.pickedTime(at("2026-10-08T18:00"), LocalTime.of(18, 0)))
    }

    @Test
    public fun moveToTomorrowIsSuggestedFromTheThirdSnooze() {
        assertFalse(SnoozeRules.suggestMoveToTomorrow(0))
        assertFalse(SnoozeRules.suggestMoveToTomorrow(2))
        assertTrue(SnoozeRules.suggestMoveToTomorrow(3))
        assertTrue(SnoozeRules.suggestMoveToTomorrow(7))
    }

    // Escalation

    @Test
    public fun fullScreenStartsGentleAndGetsLouderEveryMinuteUntilTheTimeout() {
        val steps = Escalation.steps(Importance.FULL_SCREEN, Duration.ofMinutes(5))
        assertEquals(listOf(0L, 60L, 120L, 180L, 240L), steps.map { it.at.seconds })
        assertEquals(listOf(0.4f, 0.6f, 0.8f, 1f, 1f), steps.map { it.volume }.map { Math.round(it * 10) / 10f })
        assertTrue(steps.none { it.loop })
    }

    @Test
    public fun nothingPlaysAtOrAfterTheTimeout() {
        assertEquals(listOf(0L, 60L), Escalation.steps(Importance.FULL_SCREEN, Duration.ofMinutes(2)).map { it.at.seconds })
    }

    @Test
    public fun alarmRingsInALoopAtFullVolumeFromTheStart() {
        assertEquals(listOf(SoundStep(Duration.ZERO, 1f, loop = true)), Escalation.steps(Importance.ALARM, Duration.ofMinutes(5)))
    }

    @Test
    public fun normalLevelIsSilentHere() {
        assertTrue(Escalation.steps(Importance.NORMAL, Duration.ofMinutes(5)).isEmpty())
    }

    // Stack reducer

    private fun card(key: String, title: String = key, importance: Importance = Importance.FULL_SCREEN) = PopupCard(
        key, key, title, null, importance, null, null, null, false, 0, null, 0, Instant.EPOCH,
    )

    @Test
    public fun cardsStackInArrivalOrderInOneState() {
        var s = PopupStackState()
        s = reducePopupStack(s, PopupStackEvent.Added(card("a")))
        s = reducePopupStack(s, PopupStackEvent.Added(card("b")))
        assertEquals(listOf("a", "b"), s.cards.map { it.key })
    }

    @Test
    public fun theSameReminderReplacesItsCardInPlace() {
        var s = PopupStackState(listOf(card("a"), card("b")))
        s = reducePopupStack(s, PopupStackEvent.Added(card("a", title = "again")))
        assertEquals(listOf("again", "b"), s.cards.map { it.title })
    }

    @Test
    public fun removeAndUpdate() {
        var s = PopupStackState(listOf(card("a"), card("b")))
        s = reducePopupStack(s, PopupStackEvent.Updated("b", "renamed"))
        s = reducePopupStack(s, PopupStackEvent.Removed("a"))
        assertEquals(listOf("renamed"), s.cards.map { it.title })
        assertTrue(reducePopupStack(s, PopupStackEvent.Removed("b")).isEmpty)
    }

    @Test
    public fun removingAnUnknownCardChangesNothing() {
        val s = PopupStackState(listOf(card("a")))
        assertEquals(s, reducePopupStack(s, PopupStackEvent.Removed("zzz")))
    }

    @Test
    public fun theLoudestLevelDecidesTheSound() {
        val s = PopupStackState(listOf(card("a", importance = Importance.FULL_SCREEN), card("b", importance = Importance.ALARM)))
        assertEquals(Importance.ALARM, s.loudest)
        assertNull(PopupStackState().loudest)
    }

    // "Why am I seeing this?"

    @Test
    public fun offsetsUseTheLargestWholeUnit() {
        assertEquals(OffsetDescription.AtTime, describeOffset(0))
        assertEquals(OffsetDescription.Before(10, OffsetUnit.MINUTES), describeOffset(10))
        assertEquals(OffsetDescription.Before(90, OffsetUnit.MINUTES), describeOffset(90))
        assertEquals(OffsetDescription.Before(1, OffsetUnit.HOURS), describeOffset(60))
        assertEquals(OffsetDescription.Before(3, OffsetUnit.HOURS), describeOffset(180))
        assertEquals(OffsetDescription.Before(1, OffsetUnit.DAYS), describeOffset(1_440))
        assertEquals(OffsetDescription.Before(2, OffsetUnit.DAYS), describeOffset(2_880))
        assertEquals(OffsetDescription.Before(1, OffsetUnit.WEEKS), describeOffset(10_080))
    }
}
