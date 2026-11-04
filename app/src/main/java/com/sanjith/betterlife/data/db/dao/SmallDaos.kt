package com.sanjith.betterlife.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProjectDao {

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND archived = 0 ORDER BY sortOrder, name")
    public fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    public suspend fun getAnyById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(project: ProjectEntity)

    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now, syncState = 'DIRTY' WHERE id = :id")
    public suspend fun softDelete(id: String, now: Long)
}

/** The reminder log is append-only: there is deliberately no update or delete here (purge comes in Prompt 11). */
@Dao
public interface ReminderLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insert(entry: ReminderLogEntity)

    @Query("SELECT * FROM reminder_log ORDER BY firedAt DESC LIMIT :limit")
    public fun observeRecent(limit: Int): Flow<List<ReminderLogEntity>>

    @Query("SELECT COUNT(*) FROM reminder_log")
    public fun observeCount(): Flow<Int>
}

@Dao
public interface SyncCursorDao {

    @Query("SELECT lastPulledAt FROM sync_cursors WHERE tableName = :tableName")
    public suspend fun lastPulledAt(tableName: String): Long?

    @Upsert
    public suspend fun save(cursor: SyncCursorEntity)
}
