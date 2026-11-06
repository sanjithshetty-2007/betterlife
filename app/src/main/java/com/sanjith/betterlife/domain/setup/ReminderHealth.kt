package com.sanjith.betterlife.domain.setup

import java.time.Duration
import java.time.Instant

/**
 * The Reminder Health headline (Prompt 11): how many rows are fine out of how many, which rows need
 * the owner, and whether Home shows the red banner.
 *
 * Example: 9 rows with Draw over apps RED and Battery YELLOW -> score 7 of 9, 2 need you, banner on.
 */
public data class HealthSummary(
    val score: Int,
    val total: Int,
    val needsYou: List<SetupCheck>,
    val redChecks: List<SetupCheck>,
) {
    /** Home shows a banner only when something stops reminders from showing (a RED row). */
    val showBanner: Boolean get() = redChecks.isNotEmpty()

    public companion object {
        public fun of(status: SetupStatus): HealthSummary = HealthSummary(
            score = status.items.count { it.level == SetupLevel.GREEN },
            total = status.items.size,
            needsYou = status.items.filter { it.level != SetupLevel.GREEN }.map { it.check },
            redChecks = status.items.filter { it.level == SetupLevel.RED }.map { it.check },
        )
    }
}

/**
 * "Fix all" walks through the RED rows one at a time: open the first one's settings, and after the
 * owner comes back offer the next RED row not yet visited. A row that is still RED after its visit
 * is not offered again, so the walk always ends.
 */
public object FixAllWalk {

    /** The next RED row to open, or null when the walk is finished. */
    public fun next(status: SetupStatus, visited: Set<SetupCheck>): SetupCheck? =
        status.items.firstOrNull { it.level == SetupLevel.RED && it.check !in visited }?.check

    /** Rows still RED after the walk (shown so the owner knows what is left). */
    public fun leftOver(status: SetupStatus): List<SetupCheck> =
        status.items.filter { it.level == SetupLevel.RED }.map { it.check }
}

/**
 * Expected importance of each reminder channel versus what the owner left it at. Channel
 * importances use Android's NotificationManager numbers (0 = off, 3 = default, 4 = high).
 */
public object ChannelCheck {

    public const val IMPORTANCE_NONE: Int = 0

    /**
     * BLOCKED if any reminder channel is off, LOWERED if any is below the importance the app created
     * it with, else OK. A channel that does not exist yet ([actual] has no entry) is OK: the app
     * creates it on the next start.
     */
    public fun evaluate(expected: Map<String, Int>, actual: Map<String, Int>): ChannelState {
        val found = expected.keys.mapNotNull { id -> actual[id]?.let { id to it } }
        return when {
            found.any { (_, importance) -> importance == IMPORTANCE_NONE } -> ChannelState.BLOCKED
            found.any { (id, importance) -> importance < expected.getValue(id) } -> ChannelState.LOWERED
            else -> ChannelState.OK
        }
    }
}

/** How the "Test after closing the app" reminder went (Prompt 11). */
public sealed interface ClosedAppTestResult {
    /** Not due yet. */
    public data class Waiting(val due: Instant) : ClosedAppTestResult

    /** Delivered [lateBy] after it was due. */
    public data class Fired(val at: Instant, val lateBy: Duration) : ClosedAppTestResult

    /** Due more than the grace time ago and nothing was delivered, or the engine logged it as missed/failed. */
    public data class DidNotFire(val due: Instant) : ClosedAppTestResult
}

public object ClosedAppTest {

    /** How long after the due time the test may still arrive before it counts as not fired. */
    public val GRACE: Duration = Duration.ofMinutes(2)

    /**
     * The result from the log: [firedAt] is the first FIRED row for the test reminder, [failed] is
     * true if a MISSED or FAILED row exists.
     *
     * Example: due 10:00:00, fired 10:00:01 -> Fired(lateBy = 1 s).
     */
    public fun result(due: Instant, now: Instant, firedAt: Instant?, failed: Boolean): ClosedAppTestResult = when {
        firedAt != null -> ClosedAppTestResult.Fired(firedAt, Duration.between(due, firedAt).coerceAtLeast(Duration.ZERO))
        failed || now.isAfter(due.plus(GRACE)) -> ClosedAppTestResult.DidNotFire(due)
        else -> ClosedAppTestResult.Waiting(due)
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this
}
