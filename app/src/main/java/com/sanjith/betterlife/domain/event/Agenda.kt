package com.sanjith.betterlife.domain.event

import com.sanjith.betterlife.domain.recurrence.RecurrenceEngine
import com.sanjith.betterlife.domain.recurrence.RecurrenceRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What an agenda row is. Only EVENT and MEETING can conflict (context.md 5.2: tasks never do). */
public enum class AgendaKind { EVENT, MEETING, BIRTHDAY, TASK, REMINDER }

/** When an item happens, as the agenda sees it. */
public sealed interface AgendaTime {
    /** [end] null means no end time was given. */
    public data class Timed(val start: Instant, val end: Instant?, val zone: ZoneId) : AgendaTime

    /** [end] is inclusive; null means one day. */
    public data class AllDay(val start: LocalDate, val end: LocalDate? = null) : AgendaTime
}

/** One stored item (event, birthday, task or standalone reminder) the agenda may show. */
public data class AgendaItem(
    val id: String,
    val kind: AgendaKind,
    val title: String,
    val time: AgendaTime,
    val recurrence: RecurrenceRule? = null,
    val location: String? = null,
    val colorCategory: String? = null,
    val birthYear: Int? = null,
)

/** One occurrence of an item on one day. [start] is null for all-day rows. */
public data class AgendaEntry(
    val item: AgendaItem,
    val date: LocalDate,
    val start: Instant?,
    val end: Instant?,
    val age: Int? = null,
    val conflict: Boolean = false,
) {
    /** Unique per occurrence, so a repeating event has one key per day. */
    val key: String get() = "${item.id}@$date"
    val allDay: Boolean get() = start == null
}

public data class AgendaDay(val date: LocalDate, val entries: List<AgendaEntry>)

/** Builds the agenda list: occurrences in a date window, grouped by day, with conflict badges. */
public object Agenda {

    /** A multi-day all-day item is listed on at most this many days. */
    private const val MAX_SPAN_DAYS = 31L

    /**
     * Every occurrence of [items] from [from] for [days] days, grouped by day (empty days left out).
     * Within a day: all-day rows first (birthdays before others), then by start time, then title.
     * Repeating items are expanded; a timed occurrence keeps its local time of day and its length.
     *
     * Example: a weekly Monday 10:00-11:00 meeting and a window of 14 days from Mon 2026-10-12 gives
     * rows on 2026-10-12 and 2026-10-19.
     */
    public fun build(items: List<AgendaItem>, from: LocalDate, days: Int, zone: ZoneId): List<AgendaDay> {
        require(days > 0) { "days must be positive" }
        val to = from.plusDays(days.toLong() - 1)
        val entries = items.flatMap { occurrences(it, from, to, zone) }
        val conflicts = ConflictDetector.conflictingKeys(entries)
        return entries
            .map { if (it.key in conflicts) it.copy(conflict = true) else it }
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, list) -> AgendaDay(date, list.sortedWith(DAY_ORDER)) }
    }

    private val DAY_ORDER: Comparator<AgendaEntry> = compareBy<AgendaEntry>(
        { !it.allDay },
        { it.item.kind != AgendaKind.BIRTHDAY },
        { it.start },
        { it.item.title.lowercase() },
    )

    private fun occurrences(item: AgendaItem, from: LocalDate, to: LocalDate, zone: ZoneId): List<AgendaEntry> =
        when (val time = item.time) {
            is AgendaTime.AllDay -> {
                val span = time.end?.let { Duration.between(time.start.atStartOfDay(), it.atStartOfDay()).toDays() }
                    ?.coerceIn(0, MAX_SPAN_DAYS) ?: 0
                // Days whose span reaches into the window: start up to `span` days before it.
                startDates(item.recurrence, time.start, from.minusDays(span), to).flatMap { start ->
                    (0..span).map { start.plusDays(it) }
                        .filter { !it.isBefore(from) && !it.isAfter(to) }
                        .map { day -> AgendaEntry(item, day, start = null, end = null, age = birthdayAge(item, start)) }
                }
            }
            is AgendaTime.Timed -> {
                val local = time.start.atZone(time.zone)
                val length = time.end?.let { Duration.between(time.start, it) }
                // A late-evening occurrence in another zone can land on a neighbouring day here.
                startDates(item.recurrence, local.toLocalDate(), from.minusDays(1), to.plusDays(1)).mapNotNull { date ->
                    val start = date.atTime(local.toLocalTime()).atZone(time.zone).toInstant()
                    val day = start.atZone(zone).toLocalDate()
                    if (day.isBefore(from) || day.isAfter(to)) return@mapNotNull null
                    AgendaEntry(item, day, start, length?.let { start.plus(it) })
                }
            }
        }

    private fun startDates(rule: RecurrenceRule?, anchor: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> =
        when (rule) {
            null -> listOf(anchor).filter { !it.isBefore(from) && !it.isAfter(to) }
            else -> RecurrenceEngine.occurrencesBetween(rule, anchor, from, to)
        }

    private fun birthdayAge(item: AgendaItem, occurrence: LocalDate): Int? =
        if (item.kind == AgendaKind.BIRTHDAY) BirthdayRules.ageOn(occurrence, item.birthYear) else null
}

/**
 * Two timed EVENTS or MEETINGS conflict when their times overlap. Touching boundaries (one ends at
 * 11:00, the next starts at 11:00) do not conflict; all-day rows, birthdays, tasks and reminders
 * never do. An event with no end time counts as a moment: it conflicts with an event running at
 * that moment, or another starting at the same minute.
 */
public object ConflictDetector {

    private val CAN_CONFLICT = setOf(AgendaKind.EVENT, AgendaKind.MEETING)

    public fun conflictingKeys(entries: List<AgendaEntry>): Set<String> {
        val timed = entries.filter { it.item.kind in CAN_CONFLICT && it.start != null }.sortedBy { it.start }
        val result = mutableSetOf<String>()
        for (i in timed.indices) {
            for (j in i + 1 until timed.size) {
                val a = timed[i]
                val b = timed[j]
                // Sorted by start: once b starts after a ends, no later row can overlap a.
                if (b.start!!.isAfter(a.endOrStart())) break
                if (a.item.id != b.item.id && overlaps(a.start!!, a.end, b.start, b.end)) {
                    result += a.key
                    result += b.key
                }
            }
        }
        return result
    }

    /** Overlap of [aStart, aEnd) and [bStart, bEnd); a null end is a single moment. */
    public fun overlaps(aStart: Instant, aEnd: Instant?, bStart: Instant, bEnd: Instant?): Boolean {
        if (aStart == bStart) return true
        val aStop = aEnd ?: aStart
        val bStop = bEnd ?: bStart
        return aStart.isBefore(bStop) && bStart.isBefore(aStop)
    }

    private fun AgendaEntry.endOrStart(): Instant = end ?: start!!
}
