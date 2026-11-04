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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves stored data survives schema changes. When version 2 arrives (Prompt 14):
 *  1. keep [version1SchemaCreatesAndAcceptsRows] as it is;
 *  2. add a test that calls `helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)` after
 *     inserting version-1 rows, then checks the rows with SQL;
 *  3. [allMigrationsOpenOldDataWithRoom] needs no change: it already walks every migration.
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
    }
}
