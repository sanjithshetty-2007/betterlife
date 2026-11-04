package com.sanjith.betterlife.domain.popup

import com.sanjith.betterlife.domain.model.Importance
import java.time.Duration

/** Play the level's sound at [at] after the pop-up appeared, at [volume] (0 to 1), once or looping. */
public data class SoundStep(val at: Duration, val volume: Float, val loop: Boolean)

/**
 * How a pop-up sounds while unanswered (context.md section 5.1): FULL_SCREEN plays one gentle sound,
 * then repeats every minute a little louder; ALARM rings in a loop at full volume on the alarm
 * stream. Nothing plays at or after the timeout, when the pop-up becomes a missed reminder.
 */
public object Escalation {

    public val REPEAT_EVERY: Duration = Duration.ofSeconds(60)
    public const val START_VOLUME: Float = 0.4f
    public const val VOLUME_STEP: Float = 0.2f
    public const val MAX_VOLUME: Float = 1f

    /**
     * Example: FULL_SCREEN with a 5-minute timeout -> 0 s at 0.4, 60 s at 0.6, 120 s at 0.8,
     * 180 s at 1.0, 240 s at 1.0.
     */
    public fun steps(level: Importance, timeout: Duration): List<SoundStep> = when (level) {
        Importance.NORMAL -> emptyList()
        Importance.ALARM -> listOf(SoundStep(Duration.ZERO, MAX_VOLUME, loop = true))
        Importance.FULL_SCREEN -> generateSequence(0) { it + 1 }
            .map { i -> SoundStep(REPEAT_EVERY.multipliedBy(i.toLong()), volumeAt(i), loop = false) }
            .takeWhile { it.at < timeout }
            .toList()
    }

    private fun volumeAt(repeat: Int): Float = minOf(MAX_VOLUME, START_VOLUME + VOLUME_STEP * repeat)
}
