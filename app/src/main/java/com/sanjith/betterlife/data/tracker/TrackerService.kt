package com.sanjith.betterlife.data.tracker

import java.time.LocalDate
import java.time.LocalTime

/**
 * One tracker's actions for the screens and the pop-up. Every change re-plans its daily reminder, so
 * a logged day stops that day's reminders wherever it was logged. [latest] is the newest loggable day
 * (today for protein, last night for sleep).
 */
public class TrackerService(
    private val repository: TrackerRepository,
    private val store: TrackerSettingsStore,
    private val latest: suspend () -> LocalDate,
    private val replanReminder: suspend () -> Unit,
) {
    public suspend fun save(date: LocalDate, amount: Int): TrackerSaveResult {
        val result = repository.save(date, amount, latest(), store.current().target)
        if (result == TrackerSaveResult.SAVED) replanReminder()
        return result
    }

    public suspend fun setTarget(target: Int) {
        val day = latest()
        store.setTarget(target, day)
        repository.retarget(day, target)
        replanReminder()
    }

    public suspend fun setReminder(on: Boolean, time: LocalTime) {
        store.setReminder(on, time)
        replanReminder()
    }
}
