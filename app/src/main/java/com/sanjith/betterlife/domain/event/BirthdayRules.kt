package com.sanjith.betterlife.domain.event

import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.Year

/**
 * Birthdays are all-day, yearly events (context.md section 5.2). The year may be unknown: then the
 * stored start date uses [UNKNOWN_YEAR_ANCHOR], a leap year, so 29 February is still a valid date.
 * In non-leap years a 29 February birthday falls on 28 February (RecurrenceRule.Yearly does that).
 */
public object BirthdayRules {

    /** A leap year, so every month-day (29 Feb included) exists in it. */
    public const val UNKNOWN_YEAR_ANCHOR: Int = 2000

    /** The start date stored for a birthday on [monthDay], born in [birthYear] (null = unknown). */
    public fun startDate(monthDay: MonthDay, birthYear: Int?): LocalDate {
        require(birthYear == null || monthDay.isValidYear(birthYear)) { "$monthDay does not exist in $birthYear" }
        return monthDay.atYear(birthYear ?: UNKNOWN_YEAR_ANCHOR)
    }

    /**
     * True when [birthYear] is unknown, or is a plausible past year in which [monthDay] exists
     * (29 February needs a leap year).
     */
    public fun isValid(monthDay: MonthDay, birthYear: Int?, today: LocalDate): Boolean =
        birthYear == null || (birthYear in MIN_BIRTH_YEAR..today.year && monthDay.isValidYear(birthYear))

    public const val MIN_BIRTH_YEAR: Int = 1900

    public fun rule(monthDay: MonthDay): RecurrenceRule = RecurrenceRule.Yearly(monthDay.month, monthDay.dayOfMonth)

    /**
     * The age reached on the birthday that falls on [occurrence]; null when the year is unknown or the
     * occurrence is before the birth. Example: born 1999-10-20, occurrence 2026-10-20 -> 27.
     */
    public fun ageOn(occurrence: LocalDate, birthYear: Int?): Int? =
        birthYear?.let { occurrence.year - it }?.takeIf { it >= 0 }

    /** The birthday date in [year]: 29 February becomes 28 February when [year] is not a leap year. */
    public fun dateIn(monthDay: MonthDay, year: Int): LocalDate =
        if (monthDay.month == Month.FEBRUARY && monthDay.dayOfMonth == LEAP_DAY && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, Month.FEBRUARY, LEAP_DAY - 1)
        } else {
            monthDay.atYear(year)
        }

    private const val LEAP_DAY = 29
}
