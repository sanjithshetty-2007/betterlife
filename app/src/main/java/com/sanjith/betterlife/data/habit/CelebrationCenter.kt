package com.sanjith.betterlife.data.habit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A milestone to show: full screen, or a quiet banner on the Habits screen. */
public data class CelebrationEvent(val habitId: String, val habitName: String, val milestone: Int, val quiet: Boolean)

/**
 * Milestones waiting to be shown (Prompt 16). Full-screen ones queue until the app is in front
 * (a tick from a reminder's Done happens while it may not be); the screen takes them one by one.
 * Kept in memory: if Android ends the app first, the milestone stays in the ledger but is not shown.
 */
public class CelebrationCenter {
    private val _full = MutableStateFlow<List<CelebrationEvent>>(emptyList())
    public val full: StateFlow<List<CelebrationEvent>> = _full
    private val _quiet = MutableStateFlow<CelebrationEvent?>(null)
    public val quiet: StateFlow<CelebrationEvent?> = _quiet

    public fun publish(event: CelebrationEvent) {
        if (event.quiet) _quiet.value = event else _full.update { it + event }
    }

    /** The celebration screen showed [event]. */
    public fun shown(event: CelebrationEvent) {
        _full.update { it - event }
    }

    public fun quietSeen(event: CelebrationEvent) {
        _quiet.compareAndSet(event, null)
    }
}
