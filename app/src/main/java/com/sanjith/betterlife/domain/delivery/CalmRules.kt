package com.sanjith.betterlife.domain.delivery

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** An active "I am busy" pause. Calendar items and ALARM level are held only when the owner said so. */
public data class Pause(val until: Instant, val holdsCalendar: Boolean, val holdsAlarm: Boolean)

/** The Pause choices of the "I am busy" sheet (context.md 5.1). */
public enum class PauseOption { MINUTES_30, HOUR_1, HOURS_3, TONIGHT }

public object PauseRules {

    /**
     * When a pause chosen at [now] ends; null for TONIGHT once [tonight] has passed (the sheet hides it).
     * Example: now 14:10, HOURS_3 -> 17:10; TONIGHT with tonight 20:00 -> 20:00 today.
     */
    public fun endFor(option: PauseOption, now: ZonedDateTime, tonight: LocalTime): Instant? = when (option) {
        PauseOption.MINUTES_30 -> now.plusMinutes(MINUTES_30).toInstant()
        PauseOption.HOUR_1 -> now.plusHours(1).toInstant()
        PauseOption.HOURS_3 -> now.plusHours(HOURS_3).toInstant()
        PauseOption.TONIGHT -> now.toLocalDate().atTime(tonight).atZone(now.zone).takeIf { it.isAfter(now) }?.toInstant()
    }

    public fun isActive(pause: Pause?, now: Instant): Boolean = pause != null && now.isBefore(pause.until)

    private const val MINUTES_30 = 30L
    private const val HOURS_3 = 3L
}

/** The owner's Quiet Hours (context.md 5.1). [days] are the days a window STARTS on. */
public data class QuietHoursSettings(
    val enabled: Boolean,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

public object QuietHours {

    /**
     * The end of the quiet window [now] falls in, or null when it is not quiet. A window that ends at
     * or before its start crosses midnight (22:00 to 07:00) and belongs to the day it starts on, so
     * "weekdays only" with 22:00-07:00 is quiet on Friday night until Saturday 07:00 but not on
     * Saturday night. Start is included, end is not. Equal start and end means no window.
     *
     * Example: 22:00-07:00 every day, now Tue 23:30 -> Wed 07:00; now Wed 06:59 -> Wed 07:00; now Wed 07:00 -> null.
     */
    public fun activeUntil(settings: QuietHoursSettings, now: ZonedDateTime): Instant? {
        if (!settings.enabled || settings.start == settings.end) return null
        val crossesMidnight = !settings.end.isAfter(settings.start)
        val today = now.toLocalDate()
        // A window that crosses midnight may have started yesterday.
        val candidateStarts = if (crossesMidnight) listOf(today.minusDays(1), today) else listOf(today)
        return candidateStarts
            .filter { it.dayOfWeek in settings.days }
            .map { day ->
                val from = day.atTime(settings.start).atZone(now.zone)
                val to = (if (crossesMidnight) day.plusDays(1) else day).atTime(settings.end).atZone(now.zone)
                from to to
            }
            .firstOrNull { (from, to) -> !now.isBefore(from) && now.isBefore(to) }
            ?.second?.toInstant()
    }
}

/**
 * What fatigue protection knows at the moment of firing. [recentPopups] are the pop-ups that started
 * a group (grouped ones join an existing pop-up and are not new interruptions), newest first, from
 * the reminder log. [calmUntil] is set after the owner ignored pop-ups several times in a row.
 */
public data class FatigueInputs(
    val maxPerHour: Int,
    val groupWindow: Duration,
    val recentPopups: List<Instant>,
    val calmUntil: Instant?,
    val downgradeEnabled: Boolean,
    val alarmExempt: Boolean,
)

/** Everything steps 4 to 6 of the pipeline need. Null rules mean "not active". */
public data class CalmInputs(
    val pause: Pause? = null,
    val quietUntil: Instant? = null,
    val alarmBypassesQuiet: Boolean = true,
    val fatigue: FatigueInputs? = null,
) {
    public companion object {
        /** Nothing holds or changes anything (used by tests of the other steps). */
        public val NONE: CalmInputs = CalmInputs()
    }
}

/** How long fatigue protection looks back for the hourly maximum. */
public val FATIGUE_WINDOW: Duration = Duration.ofHours(1)

/** How long pop-ups stay calm after being ignored several times in a row. */
public val CALM_DURATION: Duration = Duration.ofHours(24)

/**
 * Tracks pop-ups ignored in a row (closed with Back, or timed out). An answer resets the count;
 * reaching [limit] starts a calm period and resets it, so the next calm needs [limit] new ignores.
 */
public object IgnoreStreak {

    public data class Result(val streak: Int, val calmUntil: Instant?, val calmStarted: Boolean)

    public fun onIgnored(streak: Int, calmUntil: Instant?, limit: Int, enabled: Boolean, now: Instant): Result {
        val next = streak + 1
        return if (enabled && next >= limit) Result(0, now.plus(CALM_DURATION), calmStarted = true) else Result(next, calmUntil, false)
    }

    public fun onAnswered(calmUntil: Instant?): Result = Result(0, calmUntil, calmStarted = false)
}
