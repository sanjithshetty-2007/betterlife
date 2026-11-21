package com.sanjith.betterlife.data.protein

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.ProteinDayEntity
import com.sanjith.betterlife.domain.protein.ProteinDay
import com.sanjith.betterlife.domain.protein.ProteinRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What happened to a save. */
public enum class ProteinSaveResult { SAVED, NO_GOAL, NOT_EDITABLE, INVALID }

/** Protein days in Room. Local only; amounts are never logged (agent.md: no private content in logs). */
public class ProteinRepository(
    private val db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao get() = db.proteinDao()

    public fun observeDays(): Flow<List<ProteinDay>> = dao.observeAll().map { rows -> rows.map { it.toDay() } }

    public fun observeDay(date: LocalDate): Flow<ProteinDay?> = dao.observeDay(date).map { it?.takeIf { row -> row.deletedAt == null }?.toDay() }

    public suspend fun day(date: LocalDate): ProteinDay? = dao.getAnyByDate(date)?.takeIf { it.deletedAt == null }?.toDay()

    public suspend fun isLogged(date: LocalDate): Boolean = day(date) != null

    /**
     * Saves [grams] for [date] (today or yesterday only). Today uses the current [goal]; yesterday keeps
     * the target it was saved with, or the current goal if it had no entry.
     */
    public suspend fun save(date: LocalDate, grams: Int, today: LocalDate, goal: Int?): ProteinSaveResult {
        if (grams !in 0..ProteinRules.MAX_GRAMS) return ProteinSaveResult.INVALID
        if (!ProteinRules.canEdit(date, today)) return ProteinSaveResult.NOT_EDITABLE
        val target = (if (date == today) goal else day(date)?.targetGrams ?: goal) ?: return ProteinSaveResult.NO_GOAL
        dao.upsertDay(date, grams, target, nowMillis(), newId)
        return ProteinSaveResult.SAVED
    }

    /** A new goal applies to today's entry too (never to yesterday or older days). */
    public suspend fun retarget(today: LocalDate, targetGrams: Int) {
        dao.retarget(today, targetGrams, nowMillis())
    }

    private fun ProteinDayEntity.toDay() = ProteinDay(localDate, grams, targetGrams)
}
