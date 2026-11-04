package com.sanjith.betterlife.domain.event

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Items that share one minute; [minute] is null for an item without a time (never grouped). */
public data class TimeGroup<T>(val minute: Instant?, val items: List<T>) {
    val count: Int get() = items.size
}

/**
 * Collapses items at the same minute into one entry with a count ("2:00 PM, 2 items"; context.md
 * 5.5). Used by the Today screen (Prompt 16). Groups keep the order of their first item; items
 * without a time each stay on their own.
 *
 * Example: [A 14:00:10, B 14:00:50, C 15:00, D no time] -> [(14:00, A B), (15:00, C), (null, D)].
 */
public object SameTimeGrouping {

    public fun <T> group(items: List<T>, timeOf: (T) -> Instant?): List<TimeGroup<T>> {
        val byMinute = LinkedHashMap<Any, MutableList<T>>()
        val minutes = HashMap<Any, Instant?>()
        items.forEachIndexed { index, item ->
            val minute = timeOf(item)?.truncatedTo(ChronoUnit.MINUTES)
            // Untimed items get a unique key so they are never merged.
            val key: Any = minute ?: Untimed(index)
            byMinute.getOrPut(key) { mutableListOf() } += item
            minutes[key] = minute
        }
        return byMinute.map { (key, list) -> TimeGroup(minutes[key], list) }
    }

    private data class Untimed(val index: Int)
}
