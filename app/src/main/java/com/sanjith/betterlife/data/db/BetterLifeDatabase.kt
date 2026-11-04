package com.sanjith.betterlife.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanjith.betterlife.data.db.dao.EventDao
import com.sanjith.betterlife.data.db.dao.ProjectDao
import com.sanjith.betterlife.data.db.dao.ReminderDao
import com.sanjith.betterlife.data.db.dao.ReminderLogDao
import com.sanjith.betterlife.data.db.dao.SyncCursorDao
import com.sanjith.betterlife.data.db.dao.TaskDao
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity

/**
 * The single source of truth. Changing the schema means: bump [version], write a Migration and add it
 * to [ALL_MIGRATIONS], commit the exported schema file, add a migration test (docs/DB_MIGRATIONS.md).
 * Destructive migration is never enabled: a missing migration crashes loudly instead of wiping data.
 */
@Database(
    version = 2,
    exportSchema = true,
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        EventEntity::class,
        ReminderEntity::class,
        ReminderLogEntity::class,
        SyncCursorEntity::class,
    ],
)
@TypeConverters(Converters::class)
public abstract class BetterLifeDatabase : RoomDatabase() {
    public abstract fun projectDao(): ProjectDao
    public abstract fun taskDao(): TaskDao
    public abstract fun eventDao(): EventDao
    public abstract fun reminderDao(): ReminderDao
    public abstract fun reminderLogDao(): ReminderLogDao
    public abstract fun syncCursorDao(): SyncCursorDao

    public companion object {
        public const val FILE_NAME: String = "betterlife.db"

        /** Version 2 (Prompt 7): the reminder engine's local schedule columns. Both start empty. */
        public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN scheduledFireAt INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lastFiredFireAt INTEGER")
            }
        }

        /** Every migration ever written, in order. */
        public val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        public fun build(context: Context): BetterLifeDatabase =
            Room.databaseBuilder(context, BetterLifeDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
