package com.sanjith.betterlife.domain.silent

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** How a silent reminder feels, so the owner can tell them apart without looking. Stored by name. */
public enum class VibrationPattern { STEADY, PULSE, HEARTBEAT, TRIPLE_TAP, WAVE }

/**
 * A silent reminder (owner's feature, 2026-11-21): vibration only, at each of [times] every day, for
 * [seconds] (10, 20 or 30), then it stops by itself and never repeats or snoozes.
 */
public data class SilentReminder(
    val id: String,
    val name: String,
    val times: List<LocalTime>,
    val pattern: VibrationPattern,
    val seconds: Int,
    val enabled: Boolean,
)

/** A vibration as Android takes it: segment lengths in ms and their strengths (0 = off, 255 = full). */
public class Waveform(public val timings: LongArray, public val amplitudes: IntArray) {
    public val totalMillis: Long get() = timings.sum()
}

/**
 * The silent reminder rules: when the next buzz is, which reminders it is for, and the vibration.
 *
 * Example: Mewing at 10:00 and 18:30, now 12:00: the next buzz is today 18:30; at 19:00 it is
 * tomorrow 10:00. Two reminders at 18:30 buzz one after the other in one vibration.
 */
public object SilentRules {
    public val LENGTHS_SECONDS: List<Int> = listOf(10, 20, 30)
    public const val DEFAULT_SECONDS: Int = 10
    public const val PREVIEW_MILLIS: Long = 2_000
    public const val GAP_MILLIS: Long = 600
    private const val MILLIS_PER_SECOND = 1_000L
    private const val FULL = 255

    /** The times as stored ("07:30,18:00"): sorted, without repeats. */
    public fun timesToText(times: List<LocalTime>): String = times.map { it.withSecond(0).withNano(0) }.distinct().sorted().joinToString(",")

    /** Stored text back to times; a broken entry is skipped, never a crash. */
    public fun textToTimes(text: String): List<LocalTime> =
        text.split(',').mapNotNull { part -> runCatching { LocalTime.parse(part.trim()) }.getOrNull() }.distinct().sorted()

    /** The first buzz strictly after [after], over every enabled reminder; null when there is none. */
    public fun nextBuzz(after: Instant, zone: ZoneId, reminders: List<SilentReminder>): Instant? {
        val today = after.atZone(zone).toLocalDate()
        return reminders.filter { it.enabled }.flatMap { it.times }.minOfOrNull { time ->
            val todayAt = ZonedDateTime.of(today, time, zone).toInstant()
            if (todayAt.isAfter(after)) todayAt else ZonedDateTime.of(today.plusDays(1), time, zone).toInstant()
        }
    }

    /** The enabled reminders that have a buzz at exactly [at] (the alarm's planned moment), by name. */
    public fun dueAt(at: Instant, zone: ZoneId, reminders: List<SilentReminder>): List<SilentReminder> {
        val date = at.atZone(zone).toLocalDate()
        return reminders.filter { r -> r.enabled && r.times.any { ZonedDateTime.of(date, it, zone).toInstant() == at } }.sortedBy { it.name }
    }

    /** One vibration for everything due at once: each reminder's pattern for its length, a short gap between. */
    public fun waveformFor(due: List<SilentReminder>): Waveform? {
        if (due.isEmpty()) return null
        val parts = due.mapIndexed { i, r ->
            val segments = segments(r.pattern, r.seconds * MILLIS_PER_SECOND)
            if (i < due.lastIndex) segments + (GAP_MILLIS to 0) else segments
        }.flatten()
        return Waveform(parts.map { it.first }.toLongArray(), parts.map { it.second }.toIntArray())
    }

    /** [pattern] repeated to fill exactly [millis]. */
    public fun waveform(pattern: VibrationPattern, millis: Long): Waveform {
        val parts = segments(pattern, millis)
        return Waveform(parts.map { it.first }.toLongArray(), parts.map { it.second }.toIntArray())
    }

    private fun segments(pattern: VibrationPattern, millis: Long): List<Pair<Long, Int>> {
        val cycle = cycle(pattern)
        val out = mutableListOf<Pair<Long, Int>>()
        var left = millis
        var i = 0
        while (left > 0) {
            val (length, strength) = cycle[i % cycle.size]
            val take = minOf(length, left)
            out += take to strength
            left -= take
            i++
        }
        return out
    }

    private fun cycle(pattern: VibrationPattern): List<Pair<Long, Int>> = when (pattern) {
        VibrationPattern.STEADY -> listOf(1_000L to FULL)
        VibrationPattern.PULSE -> listOf(400L to FULL, 300L to 0)
        VibrationPattern.HEARTBEAT -> listOf(150L to FULL, 120L to 0, 150L to FULL, 700L to 0)
        VibrationPattern.TRIPLE_TAP -> listOf(100L to FULL, 100L to 0, 100L to FULL, 100L to 0, 100L to FULL, 700L to 0)
        VibrationPattern.WAVE -> listOf(150L to 60, 150L to 120, 150L to 190, 300L to FULL, 150L to 190, 150L to 120, 150L to 60, 300L to 0)
    }
}
