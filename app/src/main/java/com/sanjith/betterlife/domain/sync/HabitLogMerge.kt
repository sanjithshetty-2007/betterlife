package com.sanjith.betterlife.domain.sync

/** One habit day: what makes two habit logs the same logical row, whatever their ids (Prompt S3). */
public data class HabitDayKey(val habitId: String, val localDate: String)

/**
 * Habit logs are matched by habit and day, not by id: a second phone (or a reinstall) that ticked
 * the same day made its own id. The normal last-write-wins rule ([ConflictResolver]) then decides
 * between the two versions, so a later untick beats an earlier tick and the other way round.
 */
public object HabitLogMerge {

    /**
     * The phone's version of each pulled log's habit day, keyed by the PULLED row's id, so the pull
     * plan compares the two even when their ids differ.
     *
     * Example: pulled id B for (Creatine, 2026-11-02) and the phone has id A for that day
     * -> {B: the meta of A}. A day the phone does not have is left out (the pulled row is taken).
     */
    public fun localVersions(pulled: List<Pair<HabitDayKey, String>>, local: Map<HabitDayKey, RowMeta>): Map<String, RowMeta> =
        pulled.mapNotNull { (key, pulledId) -> local[key]?.let { pulledId to it } }.toMap()
}
