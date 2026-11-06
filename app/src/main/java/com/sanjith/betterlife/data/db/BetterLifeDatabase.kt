package com.sanjith.betterlife.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanjith.betterlife.data.db.dao.CelebrationMessageDao
import com.sanjith.betterlife.data.db.dao.EventDao
import com.sanjith.betterlife.data.db.dao.HabitDao
import com.sanjith.betterlife.data.db.dao.HabitLogDao
import com.sanjith.betterlife.data.db.dao.IntegrityDao
import com.sanjith.betterlife.data.db.dao.MilestoneLedgerDao
import com.sanjith.betterlife.data.db.dao.PauseRangeDao
import com.sanjith.betterlife.data.db.dao.ProjectDao
import com.sanjith.betterlife.data.db.dao.ReminderDao
import com.sanjith.betterlife.data.db.dao.ReminderLogDao
import com.sanjith.betterlife.data.db.dao.StandaloneReminderDao
import com.sanjith.betterlife.data.db.dao.SyncDao
import com.sanjith.betterlife.data.db.dao.SyncCursorDao
import com.sanjith.betterlife.data.db.dao.TaskDao
import com.sanjith.betterlife.data.db.entity.CelebrationMessageEntity
import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.HabitEntity
import com.sanjith.betterlife.data.db.entity.HabitLogEntity
import com.sanjith.betterlife.data.db.entity.MilestoneLedgerEntity
import com.sanjith.betterlife.data.db.entity.PauseRangeEntity
import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.db.entity.SyncCursorEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity

/**
 * The single source of truth. Changing the schema means: bump [version], write a Migration and add it
 * to [ALL_MIGRATIONS], commit the exported schema file, add a migration test (docs/DB_MIGRATIONS.md).
 * Destructive migration is never enabled: a missing migration crashes loudly instead of wiping data.
 */
@Database(
    version = BetterLifeDatabase.VERSION,
    exportSchema = true,
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        EventEntity::class,
        ReminderEntity::class,
        ReminderLogEntity::class,
        SyncCursorEntity::class,
        StandaloneReminderEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        MilestoneLedgerEntity::class,
        PauseRangeEntity::class,
        CelebrationMessageEntity::class,
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
    public abstract fun standaloneReminderDao(): StandaloneReminderDao
    public abstract fun integrityDao(): IntegrityDao
    public abstract fun syncDao(): SyncDao
    public abstract fun habitDao(): HabitDao
    public abstract fun habitLogDao(): HabitLogDao
    public abstract fun milestoneLedgerDao(): MilestoneLedgerDao
    public abstract fun pauseRangeDao(): PauseRangeDao
    public abstract fun celebrationMessageDao(): CelebrationMessageDao

    public companion object {
        public const val FILE_NAME: String = "betterlife.db"

        /** The current schema version; backups from a newer one are refused (Prompt 13). */
        public const val VERSION: Int = 5

        /** Version 2 (Prompt 7): the reminder engine's local schedule columns. Both start empty. */
        public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN scheduledFireAt INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lastFiredFireAt INTEGER")
            }
        }

        /** Version 3 (Prompt 8): snooze state for the pop-up. Existing reminders start never snoozed. */
        public val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedUntil INTEGER")
            }
        }

        /** Version 4 (Prompt 10): standalone "remind me" items. A new, empty table; nothing else changes. */
        public val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `standalone_reminders` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`notes` TEXT, `atMillis` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `doneAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                        "`syncState` TEXT NOT NULL DEFAULT 'DIRTY', `serverUpdatedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_standalone_reminders_deletedAt` ON `standalone_reminders` (`deletedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_standalone_reminders_syncState` ON `standalone_reminders` (`syncState`)")
            }
        }

        /**
         * Version 5 (Prompt 14): habits, habit logs, the milestone ledger, pause ranges and celebration
         * messages. Five new, empty tables; nothing existing changes. The starter habits are added by
         * the app afterwards (StarterHabitSeeder), because a migration has no clock for their start date.
         */
        public val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                HabitSchemaV5.STATEMENTS.forEach(db::execSQL)
            }
        }

        /** Every migration ever written, in order. */
        public val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        public fun build(context: Context): BetterLifeDatabase =
            Room.databaseBuilder(context, BetterLifeDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
