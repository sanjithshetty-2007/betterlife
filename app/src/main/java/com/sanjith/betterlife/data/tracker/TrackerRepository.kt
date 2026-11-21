package com.sanjith.betterlife.data.tracker

import com.sanjith.betterlife.domain.tracker.DailyAmount
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerRules
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What happened to a save. */
public enum class TrackerSaveResult { SAVED, NO_GOAL, NOT_EDITABLE, INVALID }

/** One tracker's days in Room. Local only; amounts are never logged (agent.md: no private content in logs). */
public class TrackerRepository(
    private val kind: TrackerKind,
    private val table: TrackerDayTable,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    public fun observeDays(): Flow<List<DailyAmount>> = table.observeAll()

    public fun observeDay(date: LocalDate): Flow<DailyAmount?> = table.observeDay(date)

    public suspend fun day(date: LocalDate): DailyAmount? = table.day(date)

    public suspend fun isLogged(date: LocalDate): Boolean = day(date) != null

    /**
     * Saves [amount] for [date] (the newest loggable day [latest] or the one before only). The newest
     * day uses the current [goal]; the day before keeps the target it was saved with, or the current
     * goal if it had no entry.
     */
    public suspend fun save(date: LocalDate, amount: Int, latest: LocalDate, goal: Int?): TrackerSaveResult {
        if (!TrackerRules.validAmount(kind, amount)) return TrackerSaveResult.INVALID
        if (!TrackerRules.canEdit(date, latest)) return TrackerSaveResult.NOT_EDITABLE
        val target = (if (date == latest) goal else day(date)?.target ?: goal) ?: return TrackerSaveResult.NO_GOAL
        table.upsert(date, amount, target, nowMillis(), newId)
        return TrackerSaveResult.SAVED
    }

    /** A new goal applies to the newest day's entry too (never to older days). */
    public suspend fun retarget(latest: LocalDate, target: Int) {
        table.retarget(latest, target, nowMillis())
    }
}
