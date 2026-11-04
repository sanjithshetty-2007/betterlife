package com.sanjith.betterlife.domain.parser

import com.sanjith.betterlife.domain.model.Priority
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Rule-based quick-add parser (no AI). Reads date, time, priority, #tags and @project out of one line
 * of text; everything it does not recognise stays in the title. Never throws: any text, including
 * emoji, other scripts, empty or very long input, gives a result. The grammar is in docs/PARSER.md.
 *
 * Example (now = Thursday 2026-10-08 18:00): "call mom tomorrow 5pm" -> title "call mom",
 * date 2026-10-09, time 17:00.
 */
public object QuickAddParser {

    /**
     * Parses [text] as typed at [now]. Pass [amPm] after the owner answered the AM/PM question to get
     * the final result for the same text.
     */
    public fun parse(text: String, now: ZonedDateTime, amPm: AmPm? = null): QuickAddResult {
        val state = Scan(now.toLocalDate())
        val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        var i = 0
        while (i < words.size) {
            i += state.consume(words, i)
        }
        return state.finish(now, amPm)
    }

    private val WHITESPACE = Regex("\\s+")
    private const val AT_WORD = "at"

    /** Collects what the words mean; [consume] returns how many words it used (at least one). */
    private class Scan(private val today: LocalDate) {
        val titleWords = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var project: String? = null
        var priority: Priority = Priority.NONE
        var date: ParsedDate? = null
        var time: ParsedTime? = null

        fun consume(words: List<String>, i: Int): Int {
            val word = words[i]
            val next = words.getOrNull(i + 1)
            return consumeMarker(word)
                ?: consumeDate(word, next)
                ?: consumeTime(word, next)
                ?: run {
                    titleWords += word
                    1
                }
        }

        private fun consumeMarker(word: String): Int? {
            QuickAddWords.priority(word)?.let { priority = it; return 1 }
            QuickAddWords.tag(word)?.let { if (it !in tags) tags += it; return 1 }
            if (project == null) QuickAddWords.project(word)?.let { project = it; return 1 }
            return null
        }

        // Only the first date and the first time count; later ones stay in the title.
        private fun consumeDate(word: String, next: String?): Int? {
            if (date != null) return null
            QuickAddWords.relativeDay(word, today)?.let { date = ParsedDate(it, yearGiven = true); return 1 }
            QuickAddWords.weekday(word, today)?.let { date = ParsedDate(it, yearGiven = true); return 1 }
            QuickAddWords.numericDate(word, today)?.let { date = it; return 1 }
            if (next != null) QuickAddWords.dayAndMonthName(word, next, today)?.let { date = it; return 2 }
            return null
        }

        private fun consumeTime(word: String, next: String?): Int? {
            if (time != null) return null
            QuickAddWords.timeWithSuffix(word)?.let { time = it; return 1 }
            if (next != null) QuickAddWords.timeWithSeparateSuffix(word, next)?.let { time = it; return 2 }
            QuickAddWords.colonTime(word)?.let { time = it; return 1 }
            if (QuickAddWords.lower(word) == AT_WORD && next != null) {
                QuickAddWords.timeWithSuffix(next)?.let { time = it; return 2 }
                QuickAddWords.numberAfterAt(next)?.let { time = it; return 2 }
            }
            return null
        }

        fun finish(now: ZonedDateTime, amPm: AmPm?): QuickAddResult {
            val warnings = mutableListOf<QuickAddWarning>()
            val parsedTime = time
            val question = parsedTime?.takeIf { it.ambiguous && amPm == null }?.let { AmPmQuestion(it.hour, it.minute) }
            val resolvedTime: LocalTime? = when {
                parsedTime == null || question != null -> null
                parsedTime.ambiguous && amPm != null -> parsedTime.resolve(amPm)
                else -> parsedTime.toLocalTime()
            }
            val resolvedDate = resolveDate(resolvedTime, now, warnings)
            val title = titleWords.joinToString(" ").trim()
            if (title.isEmpty()) warnings += QuickAddWarning.EMPTY_TITLE
            return QuickAddResult(title, resolvedDate, resolvedTime, priority, tags.toList(), project, warnings, question)
        }

        private fun resolveDate(time: LocalTime?, now: ZonedDateTime, warnings: MutableList<QuickAddWarning>): LocalDate? {
            val parsed = date
            if (parsed == null) {
                if (time == null) return null
                // A time alone means today, or tomorrow if it has already passed (shown as a warning).
                if (time.isAfter(now.toLocalTime())) return today
                warnings += QuickAddWarning.MOVED_TO_TOMORROW
                return today.plusDays(1)
            }
            if (!parsed.yearGiven && parsed.date.isBefore(today)) {
                warnings += QuickAddWarning.MOVED_TO_NEXT_YEAR
                return parsed.date.plusYears(1)
            }
            if (parsed.date == today && time != null && !time.isAfter(now.toLocalTime())) {
                warnings += QuickAddWarning.TIME_ALREADY_PASSED
            }
            return parsed.date
        }
    }
}
