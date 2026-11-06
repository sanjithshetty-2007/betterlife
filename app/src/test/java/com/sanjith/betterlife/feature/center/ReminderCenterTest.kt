package com.sanjith.betterlife.feature.center

import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.dao.CenterLogRow
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.reminder.CenterFilter
import com.sanjith.betterlife.domain.reminder.ReminderLogRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reason sentences, status looks, filters, log retention and day grouping for the Reminder Center. */
public class ReminderCenterTest {

    /** English text of every string in res/values, read from the XML so the sentences themselves are checked. */
    private val english: Map<String, String> by lazy {
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        dir.listFiles { f -> f.name.startsWith("strings") }!!.flatMap { file ->
            Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""").findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.toMap()
    }

    private fun nameOf(id: Int): String = R.string::class.java.fields.single { it.getInt(null) == id }.name

    private fun textOf(id: Int): String = english.getValue(nameOf(id))

    @Test
    public fun everyReasonCodeHasItsOwnPlainSentence() {
        val ids = ReasonCode.entries.map { it.sentence() }
        assertEquals("each code has its own sentence", ReasonCode.entries.size, ids.toSet().size)
        ReasonCode.entries.forEach { code ->
            val text = textOf(code.sentence())
            assertTrue("$code: '$text' is not a full sentence", text.length > MIN_SENTENCE && text.endsWith("."))
            assertFalse("$code: the sentence must not show the code itself", text.contains(code.name))
        }
    }

    @Test
    public fun reasonSentencesSayWhatHappened() {
        assertEquals("Held because Quiet Hours were active.", textOf(ReasonCode.QUIET_HOURS.sentence()))
        assertEquals("You were on a call, so it came as a normal notification.", textOf(ReasonCode.IN_CALL.sentence()))
        assertEquals("The phone was off or the app was stopped when this was due.", textOf(ReasonCode.DEVICE_OFF.sentence()))
    }

    @Test
    public fun reasonLineUsesTheCodeOrAGeneralSentenceForProblemRows() {
        assertEquals(R.string.reason_device_off, reasonLine(ReminderLogStatus.MISSED, ReasonCode.DEVICE_OFF))
        assertEquals(R.string.reason_in_call, reasonLine(ReminderLogStatus.FIRED, ReasonCode.IN_CALL))
        assertEquals(R.string.reason_missed_no_answer, reasonLine(ReminderLogStatus.MISSED, null))
        assertEquals(R.string.reason_failed_unknown, reasonLine(ReminderLogStatus.FAILED, null))
        assertEquals(R.string.reason_held_unknown, reasonLine(ReminderLogStatus.HELD, null))
        assertNull(reasonLine(ReminderLogStatus.FIRED, null))
        assertNull(reasonLine(ReminderLogStatus.COMPLETED, null))
        listOf(R.string.reason_missed_no_answer, R.string.reason_failed_unknown, R.string.reason_held_unknown).forEach {
            assertTrue(textOf(it).endsWith("."))
        }
    }

    @Test
    public fun everyStatusHasAWordAndEveryFilterALabel() {
        assertEquals(ReminderLogStatus.entries.size, ReminderLogStatus.entries.map { it.look().label }.toSet().size)
        ReminderLogStatus.entries.forEach { assertTrue(textOf(it.look().label).isNotBlank()) }
        assertEquals(ReminderLogStatus.entries.size + 1, CenterFilter.entries.size)
        assertEquals("All", textOf(CenterFilter.ALL.label()))
    }

    @Test
    public fun filtersMatchTheirStatusAndAllMatchesEverything() {
        assertTrue(CenterFilter.ALL.matches(ReminderLogStatus.FAILED))
        assertTrue(CenterFilter.HELD.matches(ReminderLogStatus.HELD))
        assertFalse(CenterFilter.HELD.matches(ReminderLogStatus.FIRED))
        CenterFilter.entries.filter { it != CenterFilter.ALL }.forEach { f -> assertEquals(1, ReminderLogStatus.entries.count(f::matches)) }
    }

    // Log retention

    @Test
    public fun purgeCutoffIsSixtyDaysBack() {
        assertEquals(Instant.parse("2026-09-07T10:00:00Z"), ReminderLogRules.purgeCutoff(Instant.parse("2026-11-06T10:00:00Z")))
        // Leap day crossed: still exactly 60 days of time.
        assertEquals(Instant.parse("2028-01-31T00:00:00Z"), ReminderLogRules.purgeCutoff(Instant.parse("2028-03-31T00:00:00Z")))
    }

    @Test
    public fun snoozeCountRoundTripsThroughTheLogDetail() {
        val detail = ReminderLogRules.snoozeDetail(2, Instant.parse("2026-11-06T10:10:00Z"))
        assertEquals(2, ReminderLogRules.snoozeCount(detail))
        assertNull(ReminderLogRules.snoozeCount("closed without an answer"))
        assertNull(ReminderLogRules.snoozeCount(null))
        assertNull(ReminderLogRules.snoozeCount("count x until y"))
    }

    // Day grouping

    @Test
    public fun rowsAreGroupedByLocalDayNewestFirst() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun row(at: String) = CenterLogRow(
            ReminderLogEntity("l-$at", "r", Instant.parse(at).toEpochMilli(), ReminderLogStatus.FIRED, null, Importance.NORMAL, null, 0, 0, null),
            null, null, null, null,
        )
        // 19:00Z is 00:30 the next day in India.
        val days = groupByDay(listOf(row("2026-11-05T19:00:00Z"), row("2026-11-05T10:00:00Z"), row("2026-11-04T10:00:00Z")), zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4)), days.map { it.date })
        assertTrue(days.all { it.rows.size == 1 })
    }

    private companion object {
        const val MIN_SENTENCE = 15
    }
}
