package com.sanjith.betterlife.domain.popup

/** "Why am I seeing this?": which rule fired, in units the UI turns into words. */
public sealed interface OffsetDescription {
    public data object AtTime : OffsetDescription
    public data class Before(val amount: Int, val unit: OffsetUnit) : OffsetDescription
}

public enum class OffsetUnit { MINUTES, HOURS, DAYS, WEEKS }

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1_440
private const val MINUTES_PER_WEEK = 10_080

/**
 * The largest whole unit for an offset. Example: 60 -> 1 hour, 1440 -> 1 day, 2880 -> 2 days,
 * 10080 -> 1 week, 90 -> 90 minutes, 0 -> at time.
 */
public fun describeOffset(offsetMinutes: Int): OffsetDescription = when {
    offsetMinutes <= 0 -> OffsetDescription.AtTime
    offsetMinutes % MINUTES_PER_WEEK == 0 -> OffsetDescription.Before(offsetMinutes / MINUTES_PER_WEEK, OffsetUnit.WEEKS)
    offsetMinutes % MINUTES_PER_DAY == 0 -> OffsetDescription.Before(offsetMinutes / MINUTES_PER_DAY, OffsetUnit.DAYS)
    offsetMinutes % MINUTES_PER_HOUR == 0 -> OffsetDescription.Before(offsetMinutes / MINUTES_PER_HOUR, OffsetUnit.HOURS)
    else -> OffsetDescription.Before(offsetMinutes, OffsetUnit.MINUTES)
}
