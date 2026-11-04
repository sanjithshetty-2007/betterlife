package com.sanjith.betterlife.domain.recurrence

import java.time.DayOfWeek
import java.time.Month

/**
 * The text stored in the recurrenceRule column. Small, readable and stable: changing it means a
 * database migration. Unknown or broken text decodes to null instead of crashing.
 *
 * Examples:
 * - `DAILY;INTERVAL=1`
 * - `WEEKDAYS`
 * - `WEEKLY;DAYS=MON,WED;INTERVAL=2`
 * - `MONTHLY;DAY=31;INTERVAL=1`
 * - `YEARLY;MONTH=2;DAY=29;INTERVAL=1`
 */
public object RecurrenceRuleText {

    public fun encode(rule: RecurrenceRule): String = when (rule) {
        is RecurrenceRule.Daily -> "DAILY;INTERVAL=${rule.interval}"
        RecurrenceRule.Weekdays -> "WEEKDAYS"
        is RecurrenceRule.Weekly ->
            "WEEKLY;DAYS=${rule.days.sortedBy { it.value }.joinToString(",") { it.name.take(DAY_CODE_LENGTH) }};INTERVAL=${rule.interval}"
        is RecurrenceRule.Monthly -> "MONTHLY;DAY=${rule.dayOfMonth};INTERVAL=${rule.interval}"
        is RecurrenceRule.Yearly -> "YEARLY;MONTH=${rule.month.value};DAY=${rule.dayOfMonth};INTERVAL=${rule.interval}"
    }

    public fun decode(text: String?): RecurrenceRule? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(';')
        val fields = parts.drop(1).mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { (key, value) -> key to value }
        }.toMap()
        val interval = fields["INTERVAL"]?.toIntOrNull() ?: 1
        return runCatching {
            when (parts.first()) {
                "DAILY" -> RecurrenceRule.Daily(interval)
                "WEEKDAYS" -> RecurrenceRule.Weekdays
                "WEEKLY" -> RecurrenceRule.Weekly(parseDays(fields.getValue("DAYS")), interval)
                "MONTHLY" -> RecurrenceRule.Monthly(fields.getValue("DAY").toInt(), interval)
                "YEARLY" -> RecurrenceRule.Yearly(
                    Month.of(fields.getValue("MONTH").toInt()),
                    fields.getValue("DAY").toInt(),
                    interval,
                )
                else -> null
            }
        }.getOrNull()
    }

    private fun parseDays(text: String): Set<DayOfWeek> = text.split(',').map { code ->
        DayOfWeek.entries.first { it.name.take(DAY_CODE_LENGTH) == code }
    }.toSet()

    private const val DAY_CODE_LENGTH = 3
}
