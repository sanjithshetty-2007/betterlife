package com.sanjith.betterlife.domain.recurrence

import java.time.DayOfWeek
import java.time.Month

/**
 * How an item repeats. Every rule has an [interval] ("every 2 weeks"), which is how custom repeats
 * are expressed. Occurrences are calendar dates; the time of day belongs to the item.
 */
public sealed interface RecurrenceRule {
    public val interval: Int

    public data class Daily(override val interval: Int = 1) : RecurrenceRule {
        init { requirePositive(interval) }
    }

    /** Monday to Friday. */
    public data object Weekdays : RecurrenceRule {
        override val interval: Int = 1
    }

    public data class Weekly(val days: Set<DayOfWeek>, override val interval: Int = 1) : RecurrenceRule {
        init {
            requirePositive(interval)
            require(days.isNotEmpty()) { "A weekly rule needs at least one day" }
        }
    }

    /** On [dayOfMonth]; in shorter months it falls on the last day (31st -> 30th, 28th or 29th). */
    public data class Monthly(val dayOfMonth: Int, override val interval: Int = 1) : RecurrenceRule {
        init {
            requirePositive(interval)
            require(dayOfMonth in 1..MAX_DAY_OF_MONTH) { "dayOfMonth must be 1 to 31: $dayOfMonth" }
        }
    }

    /** Once a year; 29 February falls on 28 February in non-leap years. */
    public data class Yearly(val month: Month, val dayOfMonth: Int, override val interval: Int = 1) : RecurrenceRule {
        init {
            requirePositive(interval)
            require(dayOfMonth in 1..month.maxLength()) { "$month has no day $dayOfMonth" }
        }
    }

    private companion object {
        const val MAX_DAY_OF_MONTH = 31
    }
}

/** Repeat from the planned due date (default) or from the day the task was actually completed. */
public enum class RepeatMode { FROM_DUE_DATE, FROM_COMPLETION }

private fun requirePositive(interval: Int) = require(interval > 0) { "interval must be positive: $interval" }
