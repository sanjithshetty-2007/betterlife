package com.sanjith.betterlife.data.protein

import java.time.LocalDate
import java.time.LocalTime

/**
 * Protein actions for the screens and the pop-up. Every change re-plans the daily reminder, so a
 * logged day stops that day's reminders wherever it was logged. [today] is the app's protein day.
 */
public class ProteinService(
    private val repository: ProteinRepository,
    private val store: ProteinSettingsStore,
    private val today: suspend () -> LocalDate,
    private val replanReminder: suspend () -> Unit,
) {
    public suspend fun save(date: LocalDate, grams: Int): ProteinSaveResult {
        val result = repository.save(date, grams, today(), store.current().targetGrams)
        if (result == ProteinSaveResult.SAVED) replanReminder()
        return result
    }

    public suspend fun setTarget(grams: Int) {
        val day = today()
        store.setTarget(grams, day)
        repository.retarget(day, grams)
        replanReminder()
    }

    public suspend fun setReminder(on: Boolean, time: LocalTime) {
        store.setReminder(on, time)
        replanReminder()
    }
}
