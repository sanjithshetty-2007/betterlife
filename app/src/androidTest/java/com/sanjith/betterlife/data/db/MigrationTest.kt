package com.sanjith.betterlife.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves stored data survives schema changes. For each new version N: keep the older tests, add a
 * test that inserts version N-1 rows, runs `helper.runMigrationsAndValidate(DB_NAME, N, true,
 * MIGRATION_(N-1)_N)` and checks the rows with SQL. [allMigrationsOpenOldDataWithRoom] needs no
 * change: it already walks every migration from version 1.
 */
@RunWith(AndroidJUnit4::class)
public class MigrationTest {

    @get:Rule
    public val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BetterLifeDatabase::class.java,
    )

    // The app stays installed after device tests, so remove the test file instead of leaving it behind.
    @Before
    @After
    public fun deleteTestDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        // The test helper's lock file is not removed by deleteDatabase.
        File(context.getDatabasePath(DB_NAME).path + LOCK_SUFFIX).delete()
    }

    @Test
    public fun version1SchemaCreatesAndAcceptsRows() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(INSERT_V1_TASK)
            db.query("SELECT title FROM tasks WHERE id = 'task-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Written by version 1", cursor.getString(0))
            }
        }
    }

    @Test
    public fun migration1To2KeepsRemindersAndAddsEmptyScheduleColumns() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(INSERT_V1_TASK)
            db.execSQL(INSERT_V1_REMINDER)
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, BetterLifeDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT offsetMinutes, importance, scheduledFireAt, lastFiredFireAt FROM reminders WHERE id = 'rem-1'").use { c ->
                c.moveToFirst()
                assertEquals(60, c.getInt(0))
                assertEquals("ALARM", c.getString(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM tasks").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    public fun migration2To3StartsEveryReminderUnsnoozed() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(INSERT_V1_TASK)
            db.execSQL(INSERT_V1_REMINDER)
            db.execSQL("UPDATE reminders SET scheduledFireAt = 1800000000000 WHERE id = 'rem-1'")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, BetterLifeDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT scheduledFireAt, snoozeCount, snoozedUntil FROM reminders WHERE id = 'rem-1'").use { c ->
                c.moveToFirst()
                assertEquals(1_800_000_000_000L, c.getLong(0))
                assertEquals(0, c.getInt(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    public fun migration3To4AddsAnEmptyStandaloneTableAndKeepsReminders() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(INSERT_V1_TASK)
            db.execSQL(INSERT_V1_REMINDER)
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, BetterLifeDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT COUNT(*) FROM standalone_reminders").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT offsetMinutes FROM reminders WHERE id = 'rem-1'").use { c ->
                c.moveToFirst()
                assertEquals(60, c.getInt(0))
            }
            // The new table accepts a row with the defaults Room expects.
            db.execSQL(
                "INSERT INTO standalone_reminders (id, title, notes, atMillis, zoneId, doneAt, createdAt, updatedAt, deletedAt, serverUpdatedAt) " +
                    "VALUES ('s-1', 'Call the bank', NULL, 1800000000000, 'Asia/Kolkata', NULL, 1, 1, NULL, NULL)",
            )
            db.query("SELECT syncState FROM standalone_reminders WHERE id = 's-1'").use { c ->
                c.moveToFirst()
                assertEquals("DIRTY", c.getString(0))
            }
        }
    }

    @Test
    public fun allMigrationsOpenOldDataWithRoom() = runTest {
        helper.createDatabase(DB_NAME, 1).use { db -> db.execSQL(INSERT_V1_TASK) }

        // Opens the newest schema through every migration. With no destructive fallback, a missing
        // migration makes this throw instead of silently wiping the row.
        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BetterLifeDatabase::class.java,
            DB_NAME,
        ).addMigrations(*BetterLifeDatabase.ALL_MIGRATIONS).build()
        try {
            val task = room.taskDao().observeById("task-1").first()
            assertEquals("Written by version 1", task?.title)
            helper.closeWhenFinished(room)
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val LOCK_SUFFIX = ".lck"
        const val INSERT_V1_TASK = """
            INSERT INTO tasks (id, title, notes, dueAtMillis, dueZoneId, dueDate, priority, projectId,
              parentTaskId, category, recurrenceRule, repeatFromCompletion, importance, completedAt,
              createdAt, updatedAt, deletedAt, syncState, serverUpdatedAt)
            VALUES ('task-1', 'Written by version 1', NULL, 1800000000000, 'Asia/Kolkata', NULL, 'HIGH', NULL,
              NULL, NULL, NULL, 0, 'NORMAL', NULL, 1, 1, NULL, 'DIRTY', NULL)
        """
        const val INSERT_V1_REMINDER = """
            INSERT INTO reminders (id, itemType, itemId, offsetMinutes, importance, state, createdAt, updatedAt,
              deletedAt, syncState, serverUpdatedAt)
            VALUES ('rem-1', 'TASK', 'task-1', 60, 'ALARM', 'SCHEDULED', 1, 1, NULL, 'DIRTY', NULL)
        """
    }
}
