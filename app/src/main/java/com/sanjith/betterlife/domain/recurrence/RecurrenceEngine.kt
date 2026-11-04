package com.sanjith.betterlife.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Date maths for repeating items. Every occurrence is computed from the [anchor] (the first planned
 * date), never from the previous occurrence, so "monthly on the 31st" goes 31 Jan, 28 Feb, 31 Mar
 * instead of drifting to the 28th forever.
 */
public object RecurrenceEngine {

    /** Days after which an ignored recurring task's single overdue instance moves forward. */
    public const val ROLL_FORWARD_AFTER_DAYS: Long = 30

    /** Upper bound for [occurrencesBetween], so a huge range can never build a huge list. */
    public const val MAX_OCCURRENCES: Int = 1_000

    /** All occurrences on or after [anchor], in order. Infinite; always take a bounded part. */
    public fun occurrences(rule: RecurrenceRule, anchor: LocalDate): Sequence<LocalDate> = when (rule) {
        is RecurrenceRule.Daily -> counting { k -> anchor.plusDays(k * rule.interval) }
        RecurrenceRule.Weekdays -> generateSequence(anchor) { it.plusDays(1) }.filter { it.dayOfWeek.isWeekday() }
        is RecurrenceRule.Weekly -> weekly(rule, anchor)
        is RecurrenceRule.Monthly -> counting { k ->
            val month = YearMonth.from(anchor).plusMonths(k * rule.interval)
            month.atDay(minOf(rule.dayOfMonth, month.lengthOfMonth()))
        }
        is RecurrenceRule.Yearly -> counting { k ->
            val month = YearMonth.of(anchor.year + (k * rule.interval).toInt(), rule.month)
            month.atDay(minOf(rule.dayOfMonth, month.lengthOfMonth()))
        }
    }.filter { !it.isBefore(anchor) }

    /**
     * First occurrence strictly after [after].
     *
     * Example: Monthly(31) anchored 2026-01-31, after 2026-01-31 -> 2026-02-28.
     */
    public fun nextOccurrence(rule: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate =
        occurrences(rule, anchor).first { it.isAfter(after) }

    /** Occurrences in [from]..[to] (both inclusive), at most [MAX_OCCURRENCES]. */
    public fun occurrencesBetween(rule: RecurrenceRule, anchor: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrences(rule, anchor)
            .takeWhile { !it.isAfter(to) }
            .filter { !it.isBefore(from) }
            .take(MAX_OCCURRENCES)
            .toList()

    /** "Skip this occurrence": the one after [occurrence]. */
    public fun skipOccurrence(rule: RecurrenceRule, anchor: LocalDate, occurrence: LocalDate): LocalDate =
        nextOccurrence(rule, anchor, occurrence)

    /**
     * Due date of the next instance once the current one is completed.
     *
     * FROM_DUE_DATE: the next planned occurrence after both the due date and the completion day, so
     * finishing a week late never creates a backlog of already-missed instances.
     * FROM_COMPLETION: [RecurrenceRule.interval] units after the completion day (days, weeks, months or
     * years; Weekdays means the next weekday). Example: Daily(3) completed 2026-10-10 -> 2026-10-13.
     */
    public fun nextDue(
        rule: RecurrenceRule,
        anchor: LocalDate,
        currentDue: LocalDate,
        completedOn: LocalDate,
        mode: RepeatMode,
    ): LocalDate = when (mode) {
        RepeatMode.FROM_DUE_DATE -> nextOccurrence(rule, anchor, maxOf(currentDue, completedOn))
        RepeatMode.FROM_COMPLETION -> afterCompletion(rule, completedOn)
    }

    /**
     * Due date to show for an ignored recurring task. There is only ever ONE overdue instance: while it
     * is less than 30 days late it keeps its date; after that it rolls forward to the latest occurrence
     * on or before [today].
     *
     * Example: Daily anchored 2026-09-01, due 2026-09-01, today 2026-10-08 -> 2026-10-08.
     */
    public fun overdueInstanceDue(rule: RecurrenceRule, anchor: LocalDate, due: LocalDate, today: LocalDate): LocalDate {
        if (ChronoUnit.DAYS.between(due, today) < ROLL_FORWARD_AFTER_DAYS) return due
        return occurrences(rule, anchor)
            .dropWhile { !it.isAfter(due) }
            .takeWhile { !it.isAfter(today) }
            .lastOrNull() ?: due
    }

    private fun afterCompletion(rule: RecurrenceRule, completedOn: LocalDate): LocalDate = when (rule) {
        is RecurrenceRule.Daily -> completedOn.plusDays(rule.interval.toLong())
        RecurrenceRule.Weekdays -> generateSequence(completedOn.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek.isWeekday() }
        is RecurrenceRule.Weekly -> completedOn.plusWeeks(rule.interval.toLong())
        // java.time clamps to the last day of shorter months and turns 29 Feb into 28 Feb.
        is RecurrenceRule.Monthly -> completedOn.plusMonths(rule.interval.toLong())
        is RecurrenceRule.Yearly -> completedOn.plusYears(rule.interval.toLong())
    }

    private fun weekly(rule: RecurrenceRule.Weekly, anchor: LocalDate): Sequence<LocalDate> {
        val firstWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = rule.days.sortedBy { it.value }
        return counting { k -> firstWeek.plusWeeks(k * rule.interval) }
            .flatMap { weekStart -> days.asSequence().map { day -> weekStart.with(TemporalAdjusters.nextOrSame(day)) } }
    }

    private fun counting(at: (Long) -> LocalDate): Sequence<LocalDate> = generateSequence(0L) { it + 1 }.map(at)

    private fun DayOfWeek.isWeekday(): Boolean = this != DayOfWeek.SATURDAY && this != DayOfWeek.SUNDAY
}
