package com.sanjith.betterlife.domain.mascot

import java.time.LocalDate
import kotlin.random.Random

/** Blip's moods (context.md 6). DISAPPOINTED looks sad but never comes with blaming words. */
public enum class MascotMood { CHEERING, PROUD, CURIOUS, SLEEPY, DISAPPOINTED }

/** The five permanent evolution stages and the consistent days each needs. Only POD and BABY are drawn until Prompt 27. */
public enum class MascotStage(public val consistentDays: Int) {
    POD(0),
    BABY(7),
    TEEN(30),
    ADULT(90),
    LEGEND(200),
}

/** Why the mood was picked, for the debug screen. */
public enum class MoodReason { PAUSED_TODAY, LATE_NIGHT, ALL_DONE_TODAY, STRONG_DAYS, MANY_TASKS, QUIET_DAYS, NOTHING_SPECIAL }

public data class MoodResult(val mood: MascotMood, val reason: MoodReason)

/**
 * One habit day as the mood sees it: how many habits could be done, how many were (done or kept),
 * and whether the day was paused (a pause range, or every habit marked paused).
 */
public data class DayActivity(val date: LocalDate, val possible: Int, val done: Int, val paused: Boolean)

/**
 * What the mood looks at: the local [hour], [days] = today, yesterday and the day before (today
 * first), and tasks completed in the last 24 hours. Nothing older than 72 hours counts.
 */
public data class MoodInputs(val hour: Int, val days: List<DayActivity>, val tasksDoneLast24h: Int)

/**
 * Picks Blip's mood (Prompt 16), first rule that matches:
 * 1. today paused: SLEEPY late at night, CURIOUS otherwise (a pause is never sad);
 * 2. 23:00 to 04:59: SLEEPY;
 * 3. every habit done today: CHEERING;
 * 4. 70 % or more of the habits done over the three days (paused days left out), or 3+ tasks done
 *    in the last 24 hours: PROUD;
 * 5. yesterday AND the day before had habits, were not paused and had nothing done: DISAPPOINTED;
 * 6. otherwise CURIOUS (idle).
 * Today not done yet never counts against the owner.
 */
public object MascotMoodRules {
    public const val LATE_FROM_HOUR: Int = 23
    public const val LATE_UNTIL_HOUR: Int = 5
    public const val PROUD_PERCENT: Int = 70
    public const val PROUD_TASKS: Int = 3
    public const val QUIET_DAYS_FOR_DOWN: Int = 2
    private const val PERCENT = 100

    public fun pick(inputs: MoodInputs): MoodResult {
        val today = inputs.days.firstOrNull()
        val late = inputs.hour >= LATE_FROM_HOUR || inputs.hour < LATE_UNTIL_HOUR
        if (today?.paused == true) return MoodResult(if (late) MascotMood.SLEEPY else MascotMood.CURIOUS, MoodReason.PAUSED_TODAY)
        if (late) return MoodResult(MascotMood.SLEEPY, MoodReason.LATE_NIGHT)
        if (today != null && today.possible > 0 && today.done >= today.possible) return MoodResult(MascotMood.CHEERING, MoodReason.ALL_DONE_TODAY)

        val counted = inputs.days.filter { !it.paused && it.possible > 0 }
        val possible = counted.sumOf { it.possible }
        val done = counted.sumOf { minOf(it.done, it.possible) }
        if (possible > 0 && done * PERCENT >= possible * PROUD_PERCENT) return MoodResult(MascotMood.PROUD, MoodReason.STRONG_DAYS)
        if (inputs.tasksDoneLast24h >= PROUD_TASKS) return MoodResult(MascotMood.PROUD, MoodReason.MANY_TASKS)

        val quietPastDays = inputs.days.drop(1).count { !it.paused && it.possible > 0 && it.done == 0 }
        if (quietPastDays >= QUIET_DAYS_FOR_DOWN) return MoodResult(MascotMood.DISAPPOINTED, MoodReason.QUIET_DAYS)
        return MoodResult(MascotMood.CURIOUS, MoodReason.NOTHING_SPECIAL)
    }
}

/** Evolution never goes down: the stage comes from the total of consistent days ever. */
public object MascotStages {
    /** The highest stage drawn so far (Prompt 16 draws POD and BABY). */
    public val HIGHEST_DRAWN: MascotStage = MascotStage.BABY

    public fun forConsistentDays(days: Int, highest: MascotStage = HIGHEST_DRAWN): MascotStage =
        MascotStage.entries.last { days >= it.consistentDays && it <= highest }
}

/** One of the owner's celebration lines; [milestone] null = for any milestone. */
public data class CelebrationLine(val text: String, val milestone: Int?)

/** Picks the line shown on a celebration: the owner's own first, the built-in set when there are none. */
public object CelebrationMessages {
    public fun pick(own: List<CelebrationLine>, milestone: Int, defaults: List<String>, random: Random = Random.Default): String {
        val mine = own.filter { (it.milestone == null || it.milestone == milestone) && it.text.isNotBlank() }.map { it.text.trim() }
        val pool = mine.ifEmpty { defaults.filter { it.isNotBlank() } }
        return if (pool.isEmpty()) "" else pool.random(random)
    }
}
