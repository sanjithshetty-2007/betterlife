package com.sanjith.betterlife.domain.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** Blip's mood, stage and the celebration line (Prompt 16). Today = Fri 20 Nov 2026. */
public class MascotRulesTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun day(back: Long, possible: Int, done: Int, paused: Boolean = false) = DayActivity(today.minusDays(back), possible, done, paused)

    private fun mood(hour: Int, d0: DayActivity, d1: DayActivity, d2: DayActivity, tasks: Int = 0) =
        MascotMoodRules.pick(MoodInputs(hour, listOf(d0, d1, d2), tasks))

    @Test
    public fun everyHabitDoneTodayCheers() {
        assertEquals(MoodResult(MascotMood.CHEERING, MoodReason.ALL_DONE_TODAY), mood(18, day(0, 4, 4), day(1, 4, 0), day(2, 4, 0)))
    }

    @Test
    public fun seventyPercentOverThreeDaysIsProudAndJustBelowIsNot() {
        // 7 of 10 (today 1 of 2 still pending counts as done so far).
        assertEquals(MascotMood.PROUD, mood(18, day(0, 2, 1), day(1, 4, 3), day(2, 4, 3)).mood)
        // 6 of 10.
        assertEquals(MascotMood.CURIOUS, mood(18, day(0, 2, 0), day(1, 4, 3), day(2, 4, 3)).mood)
        assertEquals(MoodResult(MascotMood.PROUD, MoodReason.MANY_TASKS), mood(18, day(0, 0, 0), day(1, 0, 0), day(2, 0, 0), tasks = 3))
    }

    @Test
    public fun idleIsCurious() {
        assertEquals(MoodResult(MascotMood.CURIOUS, MoodReason.NOTHING_SPECIAL), mood(10, day(0, 4, 1), day(1, 4, 1), day(2, 4, 0)))
        assertEquals(MascotMood.CURIOUS, mood(10, day(0, 0, 0), day(1, 0, 0), day(2, 0, 0)).mood)
    }

    @Test
    public fun twoQuietPastDaysAreDownButTodayPendingNeverCounts() {
        assertEquals(MoodResult(MascotMood.DISAPPOINTED, MoodReason.QUIET_DAYS), mood(10, day(0, 4, 0), day(1, 4, 0), day(2, 4, 0)))
        // Only one quiet past day: not down.
        assertEquals(MascotMood.CURIOUS, mood(10, day(0, 4, 0), day(1, 4, 0), day(2, 4, 1)).mood)
    }

    @Test
    public fun pausedDaysAreNeverSad() {
        assertEquals(MoodResult(MascotMood.CURIOUS, MoodReason.PAUSED_TODAY), mood(10, day(0, 4, 0, paused = true), day(1, 4, 0, true), day(2, 4, 0, true)))
        assertEquals(MascotMood.SLEEPY, mood(23, day(0, 4, 0, paused = true), day(1, 4, 0), day(2, 4, 0)).mood)
        // Paused past days are not quiet days.
        assertEquals(MascotMood.CURIOUS, mood(10, day(0, 4, 0), day(1, 4, 0, paused = true), day(2, 4, 0)).mood)
    }

    @Test
    public fun lateNightIsSleepyBetweenElevenAndFive() {
        listOf(23, 0, 4).forEach { h -> assertEquals("$h", MascotMood.SLEEPY, mood(h, day(0, 4, 4), day(1, 4, 4), day(2, 4, 4)).mood) }
        assertEquals(MascotMood.CHEERING, mood(22, day(0, 4, 4), day(1, 4, 4), day(2, 4, 4)).mood)
        assertEquals(MascotMood.CHEERING, mood(5, day(0, 4, 4), day(1, 4, 4), day(2, 4, 4)).mood)
    }

    @Test
    public fun stagesNeverPassWhatIsDrawnAndFollowConsistentDays() {
        assertEquals(MascotStage.POD, MascotStages.forConsistentDays(0))
        assertEquals(MascotStage.POD, MascotStages.forConsistentDays(6))
        assertEquals(MascotStage.BABY, MascotStages.forConsistentDays(7))
        assertEquals(MascotStage.BABY, MascotStages.forConsistentDays(500))
        assertEquals(MascotStage.TEEN, MascotStages.forConsistentDays(30, highest = MascotStage.LEGEND))
        assertEquals(MascotStage.LEGEND, MascotStages.forConsistentDays(200, highest = MascotStage.LEGEND))
    }

    @Test
    public fun theOwnersLinesComeFirstAndTheBuiltInOnesFillAnEmptyList() {
        val defaults = listOf("Built in")
        assertEquals("Built in", CelebrationMessages.pick(emptyList(), 7, defaults))
        assertEquals("Built in", CelebrationMessages.pick(listOf(CelebrationLine("For thirty", 30), CelebrationLine("   ", null)), 7, defaults))
        assertEquals("Mine", CelebrationMessages.pick(listOf(CelebrationLine(" Mine ", null)), 7, defaults))
        assertEquals("Seven!", CelebrationMessages.pick(listOf(CelebrationLine("Seven!", 7), CelebrationLine("Thirty!", 30)), 7, defaults))
        val many = listOf(CelebrationLine("A", null), CelebrationLine("B", 10))
        val seen = (0 until 50).map { CelebrationMessages.pick(many, 10, defaults, Random(it)) }.toSet()
        assertEquals(setOf("A", "B"), seen)
        assertTrue(CelebrationMessages.pick(emptyList(), 7, emptyList()).isEmpty())
    }
}
