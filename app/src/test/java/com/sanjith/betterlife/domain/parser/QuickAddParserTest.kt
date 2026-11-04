package com.sanjith.betterlife.domain.parser

import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.time.FixedClock
import com.sanjith.betterlife.domain.time.zonedNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The fixed clock is Thursday 2026-10-08 18:00 local (context.md / Prompt 6). */
public class QuickAddParserTest {

    private val now = FixedClock.of("2026-10-08T18:00", "Asia/Kolkata").zonedNow()

    private fun parse(text: String, amPm: AmPm? = null) = QuickAddParser.parse(text, now, amPm)

    @Test
    public fun callMomTomorrow5pm() {
        val r = parse("call mom tomorrow 5pm")
        assertEquals("call mom", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.isReadyToSave)
    }

    @Test
    public fun submitReportFriday3pmHigh() {
        val r = parse("submit report friday 3pm !high")
        assertEquals("submit report", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
        assertEquals(Priority.HIGH, r.priority)
    }

    @Test
    public fun gymThursdayOnAThursdayMeansNextWeek() {
        val r = parse("gym thursday")
        assertEquals("gym", r.title)
        assertEquals(LocalDate.of(2026, 10, 15), r.date)
        assertNull(r.time)
    }

    @Test
    public fun buyMilk5pmAfter5pmMovesToTomorrowWithWarning() {
        val r = parse("buy milk 5pm")
        assertEquals("buy milk", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals(listOf(QuickAddWarning.MOVED_TO_TOMORROW), r.warnings)
    }

    @Test
    public fun meetingAt5AsksAmOrPm() {
        val r = parse("meeting at 5")
        assertEquals("meeting", r.title)
        assertEquals(AmPmQuestion(5, 0), r.question)
        assertNull(r.time)
        assertFalse(r.isReadyToSave)
    }

    @Test
    public fun meetingAt5AnsweredPmIsTomorrowBecause17HasPassed() {
        val r = parse("meeting at 5", AmPm.PM)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertNull(r.question)
        assertTrue(r.isReadyToSave)
    }

    @Test
    public fun meetingAt5AnsweredAmIsTomorrowMorning() {
        val r = parse("meeting at 5", AmPm.AM)
        assertEquals(LocalTime.of(5, 0), r.time)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
    }

    @Test
    public fun dentist12Slash10IsDayFirst() {
        val r = parse("dentist 12/10")
        assertEquals("dentist", r.title)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
        assertNull(r.time)
    }

    @Test
    public fun report1730WithTagAndProjectMovesToTomorrow() {
        val r = parse("report 17:30 #work @projectx")
        assertEquals("report", r.title)
        assertEquals(listOf("work"), r.tags)
        assertEquals("projectx", r.project)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals(LocalTime.of(17, 30), r.time)
        assertEquals(listOf(QuickAddWarning.MOVED_TO_TOMORROW), r.warnings)
    }

    @Test
    public fun anniversaryGiftIsTitleOnlyForTheInbox() {
        val r = parse("anniversary gift")
        assertEquals("anniversary gift", r.title)
        assertNull(r.date)
        assertNull(r.time)
        assertEquals(Priority.NONE, r.priority)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    public fun laterTimeTodayStaysToday() {
        val r = parse("call dad 9pm")
        assertEquals(LocalDate.of(2026, 10, 8), r.date)
        assertEquals(LocalTime.of(21, 0), r.time)
    }

    @Test
    public fun timeExactlyNowCountsAsPassed() {
        assertEquals(listOf(QuickAddWarning.MOVED_TO_TOMORROW), parse("stretch 6pm").warnings)
    }

    @Test
    public fun separateAmPmWordAndMinutes() {
        val r = parse("standup 9:30 am tomorrow")
        assertEquals("standup", r.title)
        assertEquals(LocalTime.of(9, 30), r.time)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
    }

    @Test
    public fun twelveAmIsMidnightAndTwelvePmIsNoon() {
        assertEquals(LocalTime.of(0, 0), parse("x tomorrow 12am").time)
        assertEquals(LocalTime.of(12, 0), parse("x tomorrow 12pm").time)
    }

    @Test
    public fun leadingZeroOrHourAbove12IsUnambiguous24Hour() {
        assertEquals(LocalTime.of(5, 30), parse("run 05:30 tomorrow").time)
        assertEquals(LocalTime.of(17, 0), parse("call at 17").time)
        assertNull(parse("run 05:30 tomorrow").question)
    }

    @Test
    public fun colonTimeWithoutSuffixUnder13Asks() {
        assertEquals(AmPmQuestion(5, 30), parse("call 5:30").question)
    }

    @Test
    public fun dayAndMonthName() {
        val r = parse("pay rent 15 nov")
        assertEquals("pay rent", r.title)
        assertEquals(LocalDate.of(2026, 11, 15), r.date)
    }

    @Test
    public fun pastDayAndMonthMovesToNextYear() {
        val r = parse("renew passport 1/3")
        assertEquals(LocalDate.of(2027, 3, 1), r.date)
        assertEquals(listOf(QuickAddWarning.MOVED_TO_NEXT_YEAR), r.warnings)
    }

    @Test
    public fun explicitYearIsKeptEvenIfPast() {
        val r = parse("old thing 1/3/2026")
        assertEquals(LocalDate.of(2026, 3, 1), r.date)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    public fun twoDigitYear() {
        assertEquals(LocalDate.of(2027, 1, 5), parse("trip 5/1/27").date)
    }

    @Test
    public fun todayWithPassedTimeKeepsTodayButWarns() {
        val r = parse("call today 9am")
        assertEquals(LocalDate.of(2026, 10, 8), r.date)
        assertEquals(listOf(QuickAddWarning.TIME_ALREADY_PASSED), r.warnings)
    }

    @Test
    public fun invalidDateStaysInTheTitle() {
        val r = parse("note 31/02")
        assertEquals("note 31/02", r.title)
        assertNull(r.date)
    }

    @Test
    public fun invalidTimeStaysInTheTitle() {
        val r = parse("code 25:99")
        assertEquals("code 25:99", r.title)
        assertNull(r.time)
    }

    @Test
    public fun bareNumbersAreNotTimes() {
        val r = parse("buy 2 apples")
        assertEquals("buy 2 apples", r.title)
        assertNull(r.time)
    }

    @Test
    public fun atWithoutTimeStaysInTitle() {
        assertEquals("meet at cafe", parse("meet at cafe").title)
    }

    @Test
    public fun onlyTheFirstDateAndTimeCount() {
        val r = parse("move from monday to friday 9pm 10pm")
        assertEquals("move from to friday 10pm", r.title)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
        assertEquals(LocalTime.of(21, 0), r.time)
    }

    @Test
    public fun nameTomIsNotTomorrow() {
        val r = parse("call tom")
        assertEquals("call tom", r.title)
        assertNull(r.date)
    }

    @Test
    public fun caseInsensitiveWordsAndUnicodeTags() {
        val r = parse("Plan TOMORROW 5PM #Work #काम !LOW")
        assertEquals("Plan", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals(listOf("work", "काम"), r.tags)
        assertEquals(Priority.LOW, r.priority)
    }

    @Test
    public fun kannadaAndTamilTagsAndProjects() {
        val r = parse("ಓದು #ಕೆಲಸ @திட்டம்")
        assertEquals("ಓದು", r.title)
        assertEquals(listOf("ಕೆಲಸ"), r.tags)
        assertEquals("திட்டம்", r.project)
    }

    @Test
    public fun duplicateTagsAreKeptOnce() {
        assertEquals(listOf("home"), parse("fix sink #home #HOME").tags)
    }

    @Test
    public fun onlyDateWordsGiveAnEmptyTitleWarning() {
        val r = parse("tomorrow 5pm")
        assertEquals("", r.title)
        assertTrue(QuickAddWarning.EMPTY_TITLE in r.warnings)
        assertFalse(r.isReadyToSave)
    }

    @Test
    public fun emptyAndBlankInputNeverCrash() {
        listOf("", "   ", "\n\t").forEach { text ->
            val r = parse(text)
            assertEquals("", r.title)
            assertEquals(listOf(QuickAddWarning.EMPTY_TITLE), r.warnings)
        }
    }

    @Test
    public fun emojiNonLatinAndPunctuationNeverCrash() {
        assertEquals("🎂 cake 🎉", parse("🎂 cake 🎉").title)
        assertEquals("माँ को फ़ोन करो", parse("माँ को फ़ोन करो tomorrow").title)
        assertEquals("打电话给妈妈", parse("打电话给妈妈").title)
        assertEquals("!!! ??? ... # @ !", parse("!!! ??? ... # @ !").title)
        assertEquals("مرحبا", parse("مرحبا 9pm").title)
    }

    @Test
    public fun fiveHundredCharactersNeverCrash() {
        val long = "word ".repeat(100).trim()
        val r = parse("$long tomorrow")
        assertEquals(long, r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
    }

    @Test
    public fun extraSpacesCollapse() {
        assertEquals("call mom", parse("  call    mom   tomorrow  ").title)
    }

    @Test
    public fun weekdayAbbreviationsWork() {
        assertEquals(LocalDate.of(2026, 10, 12), parse("x mon").date)
        assertEquals(LocalDate.of(2026, 10, 13), parse("x tues").date)
        assertEquals(LocalDate.of(2026, 10, 10), parse("x sat").date)
    }
}
