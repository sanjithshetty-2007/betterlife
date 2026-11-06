package com.sanjith.betterlife.domain.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

public class ReminderHealthTest {

    private val allGranted = DeviceChecks(
        notificationsEnabled = true,
        exactAlarmsAllowed = true,
        canDrawOverlays = true,
        canUseFullScreenIntent = true,
        batteryUnrestricted = true,
    )
    private val allTicked = SetupCheck.MANUAL_CHECKS

    // Health aggregation

    @Test
    public fun everythingFineIsAFullScoreWithNoBanner() {
        val summary = HealthSummary.of(buildSetupStatus(allGranted, allTicked, isVivo = true))
        assertEquals(10, summary.total)
        assertEquals(10, summary.score)
        assertTrue(summary.needsYou.isEmpty())
        assertFalse(summary.showBanner)
    }

    @Test
    public fun redAndYellowRowsNeedYouButOnlyRedShowsTheBanner() {
        val device = allGranted.copy(canDrawOverlays = false, batteryUnrestricted = false)
        val summary = HealthSummary.of(buildSetupStatus(device, allTicked, isVivo = true))
        assertEquals(8, summary.score)
        assertEquals(listOf(SetupCheck.DRAW_OVER_APPS, SetupCheck.BATTERY_UNRESTRICTED), summary.needsYou)
        assertEquals(listOf(SetupCheck.DRAW_OVER_APPS), summary.redChecks)
        assertTrue(summary.showBanner)
    }

    @Test
    public fun onlyYellowRowsGiveNoBanner() {
        val summary = HealthSummary.of(buildSetupStatus(allGranted.copy(batteryUnrestricted = false), emptySet(), isVivo = true))
        assertEquals(5, summary.needsYou.size)
        assertFalse(summary.showBanner)
    }

    @Test
    public fun otherBrandsAreScoredOutOfSix() {
        val summary = HealthSummary.of(buildSetupStatus(allGranted, emptySet(), isVivo = false))
        assertEquals(6, summary.total)
        assertEquals(6, summary.score)
    }

    // Channels

    @Test
    public fun channelRowFollowsTheChannelState() {
        fun row(state: ChannelState) = buildSetupStatus(allGranted.copy(channels = state), allTicked, isVivo = true)
            .items.single { it.check == SetupCheck.CHANNELS }
        assertEquals(SetupItem(SetupCheck.CHANNELS, SetupLevel.GREEN, SetupReason.GRANTED), row(ChannelState.OK))
        assertEquals(SetupItem(SetupCheck.CHANNELS, SetupLevel.YELLOW, SetupReason.CHANNEL_LOWERED), row(ChannelState.LOWERED))
        assertEquals(SetupItem(SetupCheck.CHANNELS, SetupLevel.RED, SetupReason.CHANNEL_BLOCKED), row(ChannelState.BLOCKED))
    }

    @Test
    public fun channelCheckComparesEachChannelWithItsExpectedImportance() {
        val expected = mapOf("normal" to 3, "full" to 4, "alarm" to 4)
        assertEquals(ChannelState.OK, ChannelCheck.evaluate(expected, mapOf("normal" to 3, "full" to 4, "alarm" to 4)))
        assertEquals(ChannelState.OK, ChannelCheck.evaluate(expected, mapOf("normal" to 4, "full" to 4, "alarm" to 5)))
        assertEquals(ChannelState.LOWERED, ChannelCheck.evaluate(expected, mapOf("normal" to 3, "full" to 2, "alarm" to 4)))
        assertEquals(ChannelState.BLOCKED, ChannelCheck.evaluate(expected, mapOf("normal" to 0, "full" to 2, "alarm" to 4)))
        // Not created yet: the app makes it on its next start, so it is not a problem.
        assertEquals(ChannelState.OK, ChannelCheck.evaluate(expected, emptyMap()))
    }

    // Fix all

    @Test
    public fun fixAllVisitsEachRedRowOnceInOrderThenStops() {
        val device = allGranted.copy(notificationsEnabled = false, canDrawOverlays = false, batteryUnrestricted = false)
        val status = buildSetupStatus(device, emptySet(), isVivo = true)
        assertEquals(SetupCheck.NOTIFICATIONS, FixAllWalk.next(status, emptySet()))
        assertEquals(SetupCheck.DRAW_OVER_APPS, FixAllWalk.next(status, setOf(SetupCheck.NOTIFICATIONS)))
        // Still red after its visit: never offered twice, so the walk ends (yellow rows are not part of it).
        assertNull(FixAllWalk.next(status, setOf(SetupCheck.NOTIFICATIONS, SetupCheck.DRAW_OVER_APPS)))
        assertEquals(listOf(SetupCheck.NOTIFICATIONS, SetupCheck.DRAW_OVER_APPS), FixAllWalk.leftOver(status))
    }

    @Test
    public fun fixAllWithNothingRedHasNothingToOpen() {
        assertNull(FixAllWalk.next(buildSetupStatus(allGranted.copy(batteryUnrestricted = false), emptySet(), isVivo = true), emptySet()))
    }

    // Test after closing the app

    private val due = Instant.parse("2026-11-06T10:00:00Z")

    @Test
    public fun closedAppTestWaitsUntilItIsDue() {
        assertEquals(ClosedAppTestResult.Waiting(due), ClosedAppTest.result(due, due.minusSeconds(60), null, failed = false))
        // Within the grace time it may still arrive.
        assertEquals(ClosedAppTestResult.Waiting(due), ClosedAppTest.result(due, due.plusSeconds(90), null, failed = false))
    }

    @Test
    public fun closedAppTestReportsHowLateItArrived() {
        assertEquals(ClosedAppTestResult.Fired(due.plusSeconds(1), Duration.ofSeconds(1)), ClosedAppTest.result(due, due.plusSeconds(30), due.plusSeconds(1), false))
        // An early delivery (clock rounding) counts as on time.
        assertEquals(Duration.ZERO, (ClosedAppTest.result(due, due, due.minusMillis(5), false) as ClosedAppTestResult.Fired).lateBy)
    }

    @Test
    public fun closedAppTestFailsAfterTheGraceTimeOrALoggedFailure() {
        assertEquals(ClosedAppTestResult.DidNotFire(due), ClosedAppTest.result(due, due.plus(ClosedAppTest.GRACE).plusSeconds(1), null, false))
        assertEquals(ClosedAppTestResult.DidNotFire(due), ClosedAppTest.result(due, due.plusSeconds(5), null, failed = true))
    }
}
