package com.sanjith.betterlife.domain.parser

import com.sanjith.betterlife.domain.model.Priority
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.util.Locale

/** A clock time read from the text; [ambiguous] when it could be AM or PM. */
internal data class ParsedTime(val hour: Int, val minute: Int, val ambiguous: Boolean) {
    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    fun resolve(amPm: AmPm): LocalTime = LocalTime.of(to24Hour(hour, amPm), minute)
}

/** A date read from the text; [yearGiven] false means the parser may move it to next year. */
internal data class ParsedDate(val date: LocalDate, val yearGiven: Boolean)

/** Recognisers for single words. All matching is case-insensitive and never throws. */
internal object QuickAddWords {

    private val PRIORITIES = mapOf(
        "!high" to Priority.HIGH, "!h" to Priority.HIGH,
        "!medium" to Priority.MEDIUM, "!med" to Priority.MEDIUM, "!m" to Priority.MEDIUM,
        "!low" to Priority.LOW, "!l" to Priority.LOW,
    )
    // \p{M} (combining marks) is needed for Indic scripts: the vowel sign in "काम" is a mark, not a letter.
    private val TAG = Regex("^#([\\p{L}\\p{M}\\p{N}_-]+)$")
    private val PROJECT = Regex("^@([\\p{L}\\p{M}\\p{N}_-]+)$")
    private val NUMERIC_DATE = Regex("^(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{4}|\\d{2}))?$")
    private val TIME_WITH_SUFFIX = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a\\.m\\.|p\\.m\\.)$")
    private val TIME_COLON = Regex("^(\\d{1,2}):(\\d{2})$")
    private val BARE_NUMBER = Regex("^(\\d{1,2})(?::(\\d{2}))?$")
    private val SUFFIX = Regex("^(am|pm|a\\.m\\.|p\\.m\\.)$")
    private const val MAX_HOUR_12 = 12
    private const val MAX_HOUR_24 = 23
    private const val MAX_MINUTE = 59
    private const val CENTURY = 2000
    private const val TWO_DIGIT_YEAR = 2
    private const val DAYS_PER_WEEK = 7L

    fun lower(word: String): String = word.lowercase(Locale.ROOT)

    fun priority(word: String): Priority? = PRIORITIES[lower(word)]

    fun tag(word: String): String? = TAG.matchEntire(word)?.groupValues?.get(1)?.let(::lower)

    fun project(word: String): String? = PROJECT.matchEntire(word)?.groupValues?.get(1)

    fun relativeDay(word: String, today: LocalDate): LocalDate? = when (lower(word)) {
        "today" -> today
        // Not "tom": it is a common name ("call tom").
        "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
        else -> null
    }

    /** A weekday name means its next occurrence; today's own weekday means next week. */
    fun weekday(word: String, today: LocalDate): LocalDate? {
        val day = weekdayOf(lower(word)) ?: return null
        val ahead = (day.value - today.dayOfWeek.value + DAYS_PER_WEEK.toInt()) % DAYS_PER_WEEK.toInt()
        return today.plusDays(if (ahead == 0) DAYS_PER_WEEK else ahead.toLong())
    }

    /** dd/mm, dd/mm/yyyy or dd/mm/yy (also with '-' or '.'): day first. */
    fun numericDate(word: String, today: LocalDate): ParsedDate? {
        val match = NUMERIC_DATE.matchEntire(word) ?: return null
        val (dayText, monthText, yearText) = match.destructured
        val year = when {
            yearText.isEmpty() -> today.year
            yearText.length == TWO_DIGIT_YEAR -> CENTURY + yearText.toInt()
            else -> yearText.toInt()
        }
        return dateOrNull(year, monthText.toInt(), dayText.toInt())?.let { ParsedDate(it, yearText.isNotEmpty()) }
    }

    /** "12 oct" / "12 october" read as two words. */
    fun dayAndMonthName(dayWord: String, monthWord: String, today: LocalDate): ParsedDate? {
        val day = dayWord.toIntOrNull() ?: return null
        val month = monthOf(lower(monthWord)) ?: return null
        return dateOrNull(today.year, month.value, day)?.let { ParsedDate(it, yearGiven = false) }
    }

    /** "5pm", "5:30pm", "5:30 p.m." (single word). */
    fun timeWithSuffix(word: String): ParsedTime? {
        val match = TIME_WITH_SUFFIX.matchEntire(lower(word)) ?: return null
        val (hourText, minuteText, suffix) = match.destructured
        return twelveHour(hourText.toInt(), minuteText.ifEmpty { "0" }.toInt(), suffix)
    }

    /** A number followed by a separate "am"/"pm" word: "5 pm", "5:30 am". */
    fun timeWithSeparateSuffix(numberWord: String, suffixWord: String): ParsedTime? {
        if (!SUFFIX.matches(lower(suffixWord))) return null
        val match = BARE_NUMBER.matchEntire(numberWord) ?: return null
        val (hourText, minuteText) = match.destructured
        return twelveHour(hourText.toInt(), minuteText.ifEmpty { "0" }.toInt(), lower(suffixWord))
    }

    /** "17:30" or "05:30" is 24-hour; "5:30" or "12:00" could be either, so it is ambiguous. */
    fun colonTime(word: String): ParsedTime? {
        val match = TIME_COLON.matchEntire(word) ?: return null
        val (hourText, minuteText) = match.destructured
        return clockTime(hourText, minuteText.toInt())
    }

    /** The number after "at": "at 5" (ambiguous), "at 17" (24-hour), "at 5:30". */
    fun numberAfterAt(word: String): ParsedTime? {
        val match = BARE_NUMBER.matchEntire(word) ?: return null
        val (hourText, minuteText) = match.destructured
        return clockTime(hourText, minuteText.ifEmpty { "0" }.toInt())
    }

    private fun clockTime(hourText: String, minute: Int): ParsedTime? {
        val hour = hourText.toInt()
        if (hour > MAX_HOUR_24 || minute > MAX_MINUTE) return null
        // A leading zero ("05") or an hour outside 1..12 can only be 24-hour time.
        val ambiguous = hour in 1..MAX_HOUR_12 && !hourText.startsWith("0")
        return ParsedTime(hour, minute, ambiguous)
    }

    private fun twelveHour(hour: Int, minute: Int, suffix: String): ParsedTime? {
        if (hour !in 1..MAX_HOUR_12 || minute > MAX_MINUTE) return null
        val amPm = if (suffix.startsWith("p")) AmPm.PM else AmPm.AM
        return ParsedTime(to24Hour(hour, amPm), minute, ambiguous = false)
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private fun weekdayOf(word: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { day ->
        val name = lower(day.name)
        word == name || word == name.take(3) || (word.length >= 4 && name.startsWith(word))
    }

    private fun monthOf(word: String): Month? = Month.entries.firstOrNull { month ->
        val name = lower(month.name)
        word == name || word == name.take(3) || (word.length >= 4 && name.startsWith(word))
    }
}

internal fun to24Hour(hour12: Int, amPm: AmPm): Int {
    val noonBased = hour12 % 12
    return if (amPm == AmPm.PM) noonBased + 12 else noonBased
}
