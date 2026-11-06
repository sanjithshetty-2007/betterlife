package com.sanjith.betterlife.sync

import androidx.room.withTransaction
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.dao.SyncMetaRow
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import com.sanjith.betterlife.domain.sync.RowMeta
import kotlinx.serialization.json.JsonObject

/** [LocalSyncStore] on Room. Server rows are written straight to the tables (no reference checks: the server copy was valid when pushed). */
public class RoomSyncStore(private val db: BetterLifeDatabase) : LocalSyncStore {

    private val dao = db.syncDao()
    private val cursors = db.syncCursorDao()

    override suspend fun dirty(table: String, limit: Int): List<JsonObject> = when (table) {
        SyncTables.PROJECTS -> dao.dirtyProjects(limit).map(EntityJson::project)
        SyncTables.TASKS -> dao.dirtyTasks(limit).map(EntityJson::task)
        SyncTables.EVENTS -> dao.dirtyEvents(limit).map(EntityJson::event)
        SyncTables.STANDALONE -> dao.dirtyStandalone(limit).map(EntityJson::standalone)
        SyncTables.REMINDERS -> dao.dirtyReminders(limit).map(EntityJson::reminder)
        else -> error("Unknown synced table $table")
    }

    override suspend fun markClean(table: String, id: String, updatedAt: Long): Boolean = when (table) {
        SyncTables.PROJECTS -> dao.cleanProject(id, updatedAt)
        SyncTables.TASKS -> dao.cleanTask(id, updatedAt)
        SyncTables.EVENTS -> dao.cleanEvent(id, updatedAt)
        SyncTables.STANDALONE -> dao.cleanStandalone(id, updatedAt)
        SyncTables.REMINDERS -> dao.cleanReminder(id, updatedAt)
        else -> error("Unknown synced table $table")
    } > 0

    override suspend fun metas(table: String, ids: List<String>): Map<String, RowMeta> =
        ids.chunked(SQL_CHUNK).flatMap { chunk ->
            when (table) {
                SyncTables.PROJECTS -> dao.projectMetas(chunk)
                SyncTables.TASKS -> dao.taskMetas(chunk)
                SyncTables.EVENTS -> dao.eventMetas(chunk)
                SyncTables.STANDALONE -> dao.standaloneMetas(chunk)
                SyncTables.REMINDERS -> dao.reminderMetas(chunk)
                else -> error("Unknown synced table $table")
            }
        }.associate { it.id to it.toMeta() }

    override suspend fun applyRemote(table: String, rows: List<JsonObject>) {
        db.withTransaction {
            when (table) {
                SyncTables.PROJECTS -> dao.upsertProjects(rows.map(EntityJson::toProject))
                SyncTables.TASKS -> dao.upsertTasks(rows.map(EntityJson::toTask))
                SyncTables.EVENTS -> dao.upsertEvents(rows.map(EntityJson::toEvent))
                SyncTables.STANDALONE -> dao.upsertStandalone(rows.map(EntityJson::toStandalone))
                SyncTables.REMINDERS -> {
                    val existing = rows.map { EntityJson.meta(it).id }.chunked(SQL_CHUNK)
                        .flatMap { dao.remindersByIds(it) }.associateBy { it.id }
                    dao.upsertReminders(rows.map { EntityJson.toReminder(it, existing[EntityJson.meta(it).id]) })
                }
                else -> error("Unknown synced table $table")
            }
        }
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
            dao.dirtyAllReminders()
            dao.clearCursors()
        }
    }

    private fun SyncMetaRow.toMeta() = RowMeta(id, updatedAt, deletedAt)

    private companion object {
        const val SQL_CHUNK = 500
    }
}
