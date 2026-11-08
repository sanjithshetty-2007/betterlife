package com.sanjith.betterlife.domain.popup

import com.sanjith.betterlife.domain.model.Importance
import java.time.Duration

/** Play the level's sound at [at] after the pop-up appeared, at [volume] (0 to 1), once or looping. */
public data class SoundStep(val at: Duration, val volume: Float, val loop: Boolean)

/**
 * How a pop-up sounds while unanswered (owner's change, 2026-11-08: reminders were too easy to miss).
 * FULL_SCREEN and ALARM both ring in a loop at full volume, with strong vibration, from the first
 * moment until answered or until the ring time ends. NORMAL is a notification and plays nothing here.
 */
public object Escalation {
    public const val MAX_VOLUME: Float = 1f

    public fun steps(level: Importance): List<SoundStep> = when (level) {
        Importance.NORMAL -> emptyList()
        Importance.FULL_SCREEN, Importance.ALARM -> listOf(SoundStep(Duration.ZERO, MAX_VOLUME, loop = true))
    }
}
