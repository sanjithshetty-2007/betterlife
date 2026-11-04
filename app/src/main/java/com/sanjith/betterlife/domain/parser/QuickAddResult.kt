package com.sanjith.betterlife.domain.parser

import com.sanjith.betterlife.domain.model.Priority
import java.time.LocalDate
import java.time.LocalTime

/** Something the preview must point out before saving. */
public enum class QuickAddWarning {
    /** A time with no date had already passed today, so it was moved to tomorrow. */
    MOVED_TO_TOMORROW,

    /** A day/month with no year had already passed this year, so next year was used. */
    MOVED_TO_NEXT_YEAR,

    /** "today 9am" typed after 09:00: kept as asked, but it is already in the past. */
    TIME_ALREADY_PASSED,

    /** Only date, time or tag words were typed, so there is no title yet. */
    EMPTY_TITLE,
}

/** "at 5" could be 05:00 or 17:00: the preview asks, and the text is parsed again with the answer. */
public data class AmPmQuestion(val hour: Int, val minute: Int)

public enum class AmPm { AM, PM }

/**
 * The always-present preview of quick-add text. [date] null means undated (Inbox); [time] null means
 * all day or no time. Tags are lowercase without '#'; the project is without '@'.
 */
public data class QuickAddResult(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val priority: Priority,
    val tags: List<String>,
    val project: String?,
    val warnings: List<QuickAddWarning>,
    val question: AmPmQuestion?,
) {
    /** Saving is allowed once there is a title and no open AM/PM question. */
    val isReadyToSave: Boolean
        get() = title.isNotBlank() && question == null
}
