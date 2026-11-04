package com.sanjith.betterlife.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When an item happens: at an exact instant, or on a whole day (birthdays, all-day items). */
public sealed interface ItemTime {
    public data class At(val instant: Instant, val zone: ZoneId) : ItemTime
    public data class AllDay(val date: LocalDate) : ItemTime
}

/** One reminder firing: [offsetMinutes] before the item, at [at]. */
public data class ReminderFire(val offsetMinutes: Int, val at: Instant)

/** The early-reminder presets from context.md section 5.1, in minutes before the item. */
public object ReminderOffsets {
    public const val AT_TIME: Int = 0
    public const val MINUTES_5: Int = 5
    public const val MINUTES_10: Int = 10
    public const val MINUTES_30: Int = 30
    public const val HOUR_1: Int = 60
    public const val HOURS_3: Int = 180
    public const val DAY_1: Int = 1_440
    public const val DAYS_2: Int = 2_880
    public const val WEEK_1: Int = 10_080
    public val PRESETS: List<Int> = listOf(AT_TIME, MINUTES_5, MINUTES_10, MINUTES_30, HOUR_1, HOURS_3, DAY_1, DAYS_2, WEEK_1)
}

/**
 * Turns an item time and its early-reminder offsets into the instants to fire at.
 */
public object EarlyReminderCalculator {

    private const val MINUTES_PER_DAY = 1_440

    /**
     * Fire instants for [offsets], earliest first, dropping any before [now] (a reminder exactly at
     * [now] is kept). All-day items fire at [allDayFireTime] in [deviceZone] (default 09:00). Whole-day
     * offsets keep the wall-clock time ("1 day before" a 09:00 birthday is 09:00 the day before), even
     * across a daylight-saving change.
     *
     * Example: birthday 2026-10-09, offsets [1 day, at time], now 2026-10-08 18:00 -> only 2026-10-09 09:00
     * (the 2026-10-08 09:00 reminder is already past).
     */
    public fun fireTimes(
        item: ItemTime,
        offsets: Collection<Int>,
        now: Instant,
        deviceZone: ZoneId,
        allDayFireTime: LocalTime = LocalTime.of(9, 0),
    ): List<ReminderFire> {
        val base: ZonedDateTime = when (item) {
            is ItemTime.At -> item.instant.atZone(item.zone)
            is ItemTime.AllDay -> item.date.atTime(allDayFireTime).atZone(deviceZone)
        }
        return offsets.distinct()
            .onEach { require(it >= 0) { "Offsets are minutes before the item, never negative: $it" } }
            .map { offset -> ReminderFire(offset, base.minusOffset(offset).toInstant()) }
            .filter { !it.at.isBefore(now) }
            .sortedBy { it.at }
    }

    private fun ZonedDateTime.minusOffset(offsetMinutes: Int): ZonedDateTime =
        if (offsetMinutes % MINUTES_PER_DAY == 0) {
            minusDays((offsetMinutes / MINUTES_PER_DAY).toLong())
        } else {
            minusMinutes(offsetMinutes.toLong())
        }
}
