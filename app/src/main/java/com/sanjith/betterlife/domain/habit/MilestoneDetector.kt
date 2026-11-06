package com.sanjith.betterlife.domain.habit

import java.time.LocalDate

/** FULL = full-screen celebration with confetti; QUIET = a small banner only. */
public enum class CelebrationKind { FULL, QUIET }

/** A milestone the owner just reached on the streak that started on [streakStart]. */
public data class MilestoneEvent(val milestone: Int, val kind: CelebrationKind, val streakStart: LocalDate)

/** Milestones to write to the ledger now, and the one to show (the highest), if any. */
public data class MilestoneOutcome(val record: List<Int>, val event: MilestoneEvent?) {
    public companion object {
        public val NONE: MilestoneOutcome = MilestoneOutcome(emptyList(), null)
    }
}

/**
 * Milestones at 7, 10, 15 and 30 days (context.md 5.4). Each fires ONCE per streak: the ledger
 * remembers (habit, streak start date, milestone), so unticking and ticking a day again never
 * celebrates twice.
 *
 * - A tick on today that makes the run exactly reach a milestone: FULL.
 * - A tick on an earlier day (a backfill) that completes a milestone: QUIET.
 * - Milestones the run already passed but the ledger does not know (for example after a backfill
 *   joined two runs into one with an earlier start) are recorded quietly too; only the highest is
 *   shown.
 */
public object MilestoneDetector {
    public val MILESTONES: List<Int> = listOf(7, 10, 15, 30)

    public fun detect(run: StreakRun, alreadyRecorded: Set<Int>, isBackfill: Boolean): MilestoneOutcome {
        val reached = MILESTONES.filter { it <= run.length && it !in alreadyRecorded }
        if (reached.isEmpty()) return MilestoneOutcome.NONE
        val top = reached.max()
        val kind = if (!isBackfill && run.length == top) CelebrationKind.FULL else CelebrationKind.QUIET
        return MilestoneOutcome(reached, MilestoneEvent(top, kind, run.start))
    }
}
