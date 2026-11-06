package com.sanjith.betterlife.domain.habit

import java.time.LocalDate

/** Builders for the habit rule tests. All dates are November 2026 unless a test says otherwise. */
internal object HabitTestData {
    val TODAY: LocalDate = nov(20)

    fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)

    fun novDays(from: Int, to: Int): List<LocalDate> = (from..to).map(::nov)

    fun history(
        start: LocalDate = nov(1),
        done: List<LocalDate> = emptyList(),
        kept: List<LocalDate> = emptyList(),
        paused: List<LocalDate> = emptyList(),
        pauses: List<PausePeriod> = emptyList(),
    ): HabitHistory = HabitHistory(
        start,
        done.associateWith { HabitLogStatus.DONE } + kept.associateWith { HabitLogStatus.KEPT } +
            paused.associateWith { HabitLogStatus.PAUSED },
        pauses,
    )
}
