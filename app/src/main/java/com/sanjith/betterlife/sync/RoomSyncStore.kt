package com.sanjith.betterlife.sync

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.dao.SyncMetaRow
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import com.sanjith.betterlife.domain.sync.HabitDayKey
import com.sanjith.betterlife.domain.sync.HabitLogMerge
import com.sanjith.betterlife.domain.sync.RowMeta
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/**
 * [LocalSyncStore] on Room. Server rows are written straight to the tables (no reference checks: the
 * server copy was valid when pushed). Habit logs are matched by habit and day (Prompt S3), and
 * [onPulledHabitLogs] runs inside the same transaction so pulled runs record their milestones quietly.
 */
public class RoomSyncStore(
    private val db: BetterLifeDatabase,
    private val onPulledHabitLogs: suspend (List<HabitLogEntity>) -> Unit = {},
) : LocalSyncStore {

    private val dao = db.syncDao()
    private val cursors = db.syncCursorDao()

    override suspend fun dirty(table: String, limit: Int): List<JsonObject> = when (table) {
        SyncTables.PROJECTS -> dao.dirtyProjects(limit).map(EntityJson::project)
        SyncTables.TASKS -> dao.dirtyTasks(limit).map(EntityJson::task)
        SyncTables.EVENTS -> dao.dirtyEvents(limit).map(EntityJson::event)
        SyncTables.STANDALONE -> dao.dirtyStandalone(limit).map(EntityJson::standalone)
        SyncTables.HABITS -> dao.dirtyHabits(limit).map(EntityJson::habit)
        SyncTables.HABIT_LOGS -> dao.dirtyHabitLogs(limit).map(EntityJson::habitLog)
        SyncTables.PAUSE_RANGES -> dao.dirtyPauseRanges(limit).map(EntityJson::pauseRange)
        SyncTables.REMINDERS -> dao.dirtyReminders(limit).map(EntityJson::reminder)
        else -> error("Unknown synced table $table")
    }

    override suspend fun markClean(table: String, id: String, updatedAt: Long): Boolean = when (table) {
        SyncTables.PROJECTS -> dao.cleanProject(id, updatedAt)
        SyncTables.TASKS -> dao.cleanTask(id, updatedAt)
        SyncTables.EVENTS -> dao.cleanEvent(id, updatedAt)
        SyncTables.STANDALONE -> dao.cleanStandalone(id, updatedAt)
        SyncTables.HABITS -> dao.cleanHabit(id, updatedAt)
        SyncTables.HABIT_LOGS -> dao.cleanHabitLog(id, updatedAt)
        SyncTables.PAUSE_RANGES -> dao.cleanPauseRange(id, updatedAt)
        SyncTables.REMINDERS -> dao.cleanReminder(id, updatedAt)
        else -> error("Unknown synced table $table")
    } > 0

    override suspend fun localVersions(table: String, pulled: List<JsonObject>): Map<String, RowMeta> {
        if (table == SyncTables.HABIT_LOGS) return habitLogVersions(pulled)
        return pulled.map { EntityJson.meta(it).id }.chunked(SQL_CHUNK).flatMap { chunk ->
            when (table) {
                SyncTables.PROJECTS -> dao.projectMetas(chunk)
                SyncTables.TASKS -> dao.taskMetas(chunk)
                SyncTables.EVENTS -> dao.eventMetas(chunk)
                SyncTables.STANDALONE -> dao.standaloneMetas(chunk)
                SyncTables.HABITS -> dao.habitMetas(chunk)
                SyncTables.PAUSE_RANGES -> dao.pauseRangeMetas(chunk)
                SyncTables.REMINDERS -> dao.reminderMetas(chunk)
                else -> error("Unknown synced table $table")
            }
        }.associate { it.id to it.toMeta() }
    }

    // The phone's copy of each pulled habit day, whatever its id.
    private suspend fun habitLogVersions(pulled: List<JsonObject>): Map<String, RowMeta> =
        pulled.chunked(LOG_CHUNK).flatMap { chunk ->
            val keyed = chunk.map { EntityJson.habitDayKey(it) to EntityJson.meta(it).id }
            val habitIds = keyed.map { it.first.habitId }.distinct()
            val dates = keyed.map { LocalDate.parse(it.first.localDate) }.distinct()
            val local = dao.habitLogMetas(habitIds, dates)
                .associate { HabitDayKey(it.habitId, it.localDate.toString()) to RowMeta(it.id, it.updatedAt, it.deletedAt) }
            HabitLogMerge.localVersions(keyed, local).entries
        }.associate { it.key to it.value }

    override suspend fun applyRemote(table: String, rows: List<JsonObject>) {
        db.withTransaction {
            when (table) {
                SyncTables.PROJECTS -> dao.upsertProjects(rows.map(EntityJson::toProject))
                SyncTables.TASKS -> dao.upsertTasks(rows.map(EntityJson::toTask))
                SyncTables.EVENTS -> dao.upsertEvents(rows.map(EntityJson::toEvent))
                SyncTables.STANDALONE -> dao.upsertStandalone(rows.map(EntityJson::toStandalone))
                SyncTables.HABITS -> dao.upsertHabits(rows.map(EntityJson::toHabit))
                SyncTables.HABIT_LOGS -> applyHabitLogs(rows.map(EntityJson::toHabitLog))
                SyncTables.PAUSE_RANGES -> dao.upsertPauseRanges(rows.map(EntityJson::toPauseRange))
                SyncTables.REMINDERS -> {
                    val existing = rows.map { EntityJson.meta(it).id }.chunked(SQL_CHUNK)
                        .flatMap { dao.remindersByIds(it) }.associateBy { it.id }
                    dao.upsertReminders(rows.map { EntityJson.toReminder(it, existing[EntityJson.meta(it).id]) })
                }
                else -> error("Unknown synced table $table")
            }
        }
    }

    // Only winning rows reach here (the pull plan already compared versions by habit day).
    private suspend fun applyHabitLogs(logs: List<HabitLogEntity>) {
        logs.forEach { dao.removeOtherCopyOfDay(it.habitId, it.localDate, it.id) }
        dao.upsertHabitLogs(logs)
        onPulledHabitLogs(logs)
    }

    override suspend fun cursor(table: String): Long? = cursors.lastPulledAt(table)

    override suspend fun setCursor(table: String, micros: Long) {
        cursors.save(SyncCursorEntity(table, micros))
    }

    override suspend fun markEverythingDirtyAndResetCursors() {
        db.withTransaction {
            dao.dirtyAllProjects()
            dao.dirtyAllTasks()
            dao.dirtyAllEvents()
            dao.dirtyAllStandalone()
            dao.dirtyAllHabits()
            dao.dirtyAllHabitLogs()
            dao.dirtyAllPauseRanges()
            dao.dirtyAllReminders()
            dao.clearCursors()
        }
    }

    private fun SyncMetaRow.toMeta() = RowMeta(id, updatedAt, deletedAt)

    private companion object {
        const val SQL_CHUNK = 500

        // Two IN lists per query (habit ids and dates) must stay under SQLite's 999 parameters.
        const val LOG_CHUNK = 400
    }
}
