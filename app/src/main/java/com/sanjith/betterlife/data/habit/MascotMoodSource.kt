package com.sanjith.betterlife.data.habit

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.habit.HabitLogStatus
import com.sanjith.betterlife.domain.habit.PausePeriod
import com.sanjith.betterlife.domain.mascot.DayActivity
import com.sanjith.betterlife.domain.mascot.MascotMoodRules
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.domain.mascot.MascotStages
import com.sanjith.betterlife.domain.mascot.MoodInputs
import com.sanjith.betterlife.domain.mascot.MoodResult
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.millis
import com.sanjith.betterlife.domain.time.zonedNow
import java.time.LocalDate

/** Reads the last 72 hours from the database for Blip's mood, and the stage from all history (Prompt 16). */
public class MascotMoodSource(
    private val db: BetterLifeDatabase,
    private val clock: Clock,
    private val habitToday: suspend () -> LocalDate,
) {
    public suspend fun mood(): MoodResult = MascotMoodRules.pick(inputs())

    public suspend fun stage(): MascotStage = MascotStages.forConsistentDays(db.habitLogDao().consistentDays())

    public suspend fun inputs(): MoodInputs {
        val today = habitToday()
        val habits = db.habitDao().getActive()
        val logs = db.habitLogDao().getActiveBetween(today.minusDays(DAYS - 1L), today)
        val pauses = db.pauseRangeDao().getActive().map { PausePeriod(it.startDate, it.endDate) }
        val days = (0 until DAYS).map { back ->
            val day = today.minusDays(back.toLong())
            val ids = habits.filter { !it.startDate.isAfter(day) }.map { it.id }.toSet()
            val dayLogs = logs.filter { it.localDate == day && it.habitId in ids }
            val pausedLogs = dayLogs.count { it.status == HabitLogStatus.PAUSED }
            DayActivity(
                date = day,
                possible = ids.size,
                done = dayLogs.count { it.status == HabitLogStatus.DONE || it.status == HabitLogStatus.KEPT },
                paused = pauses.any { it.contains(day) } || (ids.isNotEmpty() && pausedLogs == ids.size),
            )
        }
        return MoodInputs(
            hour = clock.zonedNow().hour,
            days = days,
            tasksDoneLast24h = db.taskDao().countCompletedSince(clock.millis() - DAY_MILLIS),
        )
    }

    private companion object {
        const val DAYS = 3
        const val DAY_MILLIS = 86_400_000L
    }
}
