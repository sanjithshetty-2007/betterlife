package com.sanjith.betterlife.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.IntegrityChecker
import com.sanjith.betterlife.data.db.IntegrityIssue
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.TaskDraft
import com.sanjith.betterlife.data.repository.TaskRepository
import com.sanjith.betterlife.data.repository.toEntity
import com.sanjith.betterlife.domain.backup.BackupManifest
import com.sanjith.betterlife.domain.backup.RestoreProblem
import com.sanjith.betterlife.domain.model.EventType
import com.sanjith.betterlife.domain.model.ReminderItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Export / import round trip on a real database file, refusals, and the integrity check (Prompt 13). */
@RunWith(AndroidJUnit4::class)
public class BackupAndIntegrityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "backup-test.db"
    private lateinit var db: BetterLifeDatabase
    private lateinit var work: File
    private var idCounter = 0
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun open(): BetterLifeDatabase =
        Room.databaseBuilder(context, BetterLifeDatabase::class.java, name).addMigrations(*BetterLifeDatabase.ALL_MIGRATIONS).build()

    private fun engine() = BackupEngine({ db }, context.getDatabasePath(name), work, File(work, "safety"), "test", { 1_800_000_000_000L }, Dispatchers.IO)

    @Before
    public fun setUp() {
        context.deleteDatabase(name)
        work = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        db = open()
    }

    @After
    public fun tearDown() {
        if (db.isOpen) db.close()
        context.deleteDatabase(name)
        work.deleteRecursively()
    }

    private suspend fun seed(): Pair<TaskRepository, String> {
        val tasks = TaskRepository(db, { 1L }, { "t-${++idCounter}" })
        val events = EventRepository(db, { 1L }, { "e-${++idCounter}" })
        val keep = tasks.create(TaskDraft("Pay the bill", dueAtMillis = 1_800_000_000_000L, dueZoneId = zone), listOf(ReminderDraft(0), ReminderDraft(60)))
        tasks.create(TaskDraft("Call mom"))
        events.create(EventDraft("Asha", type = EventType.BIRTHDAY, allDay = true, startDate = LocalDate.of(2000, 2, 29)), listOf(ReminderDraft(1_440)))
        db.openHelper.writableDatabase.execSQL("UPDATE tasks SET syncState = 'CLEAN'")
        return tasks to keep
    }

    private suspend fun snapshot(d: BetterLifeDatabase) = Triple(
        d.taskDao().observeActive().first().map { it.copy(syncState = SyncState.CLEAN) }.sortedBy { it.id },
        d.eventDao().observeActive().first().sortedBy { it.id }.map { it.copy(syncState = SyncState.CLEAN) },
        d.reminderDao().getSchedulable().map { it.id }.sorted(),
    )

    @Test
    public fun exportThenRestoreGivesBackIdenticalData() = runTest {
        val (tasks, keep) = seed()
        val before = snapshot(db)
        val file = ByteArrayOutputStream()
        val manifest = engine().export(file)
        assertEquals(mapOf("tasks" to 2, "events" to 1, "standalone_reminders" to 0, "reminders" to 3, "projects" to 0, "reminder_log" to 0), manifest.counts)
        assertEquals(BetterLifeDatabase.VERSION, manifest.schemaVersion)

        // Change things after the backup: they must be gone after the restore.
        tasks.create(TaskDraft("Made after the backup"))
        tasks.softDelete(keep)

        val staged = engine().stage(ByteArrayInputStream(file.toByteArray()))
        assertEquals(manifest, staged.manifest)
        val safety = engine().restore(staged)
        assertTrue(safety.exists())

        db = open()
        assertEquals(before, snapshot(db))
        // Restored rows are marked for a full re-sync.
        assertTrue(db.taskDao().observeActive().first().all { it.syncState == SyncState.DIRTY })
    }

    @Test
    public fun aBackupFromANewerSchemaIsRefused() = runTest {
        seed()
        val file = ByteArrayOutputStream().also { engine().export(it) }.toByteArray()
        val tampered = rewriteManifest(file) { it.copy(schemaVersion = BetterLifeDatabase.VERSION + 1) }
        assertProblem(RestoreProblem.NEWER_SCHEMA, tampered)
    }

    @Test
    public fun aDamagedOrForeignFileIsRefused() = runTest {
        seed()
        val file = ByteArrayOutputStream().also { engine().export(it) }.toByteArray()
        assertProblem(RestoreProblem.CHECKSUM_MISMATCH, rewriteManifest(file) { it.copy(sha256 = "00".repeat(32)) })
        assertProblem(RestoreProblem.NOT_A_BACKUP, "not a zip at all".toByteArray())
    }

    @Test
    public fun integrityCheckFindsAndFixesBrokenReferences() = runTest {
        val (tasks, keep) = seed()
        val checker = IntegrityChecker(db) { 5L }
        assertTrue(checker.check().clean)

        // Break things the way a bug or an interrupted write could.
        db.reminderDao().insertAll(listOf(ReminderDraft(0).toEntity("orphan", ReminderItemType.TASK, "no-such-task", 1L)))
        db.taskDao().softDelete(keep, 2L) // the task alone, leaving its two reminders live
        val child = tasks.create(TaskDraft("Child"))
        db.openHelper.writableDatabase.execSQL("UPDATE tasks SET projectId = 'gone', parentTaskId = 'gone-too' WHERE id = '$child'")

        val report = checker.check()
        assertEquals(listOf("orphan"), report.issues.getValue(IntegrityIssue.ORPHAN_REMINDER))
        assertEquals(2, report.issues.getValue(IntegrityIssue.REMINDER_OF_DELETED_ITEM).size)
        assertEquals(listOf(child), report.issues.getValue(IntegrityIssue.MISSING_PROJECT))
        assertEquals(listOf(child), report.issues.getValue(IntegrityIssue.MISSING_PARENT_TASK))

        checker.fix()
        assertTrue(checker.check().clean)
        assertEquals(1, db.reminderDao().getSchedulable().size) // only the birthday's reminder is left
    }

    private suspend fun assertProblem(expected: RestoreProblem, bytes: ByteArray) {
        try {
            engine().stage(ByteArrayInputStream(bytes))
            fail("Expected $expected")
        } catch (e: BackupException) {
            assertEquals(expected, e.problem)
        }
    }

    private fun rewriteManifest(zipBytes: ByteArray, change: (BackupManifest) -> BackupManifest): ByteArray {
        val out = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { input ->
            ZipOutputStream(out).use { output ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    val data = input.readBytes()
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(
                        if (entry.name == "manifest.properties") {
                            BackupManifest.encode(change(BackupManifest.decode(data.toString(Charsets.UTF_8))!!)).toByteArray()
                        } else {
                            data
                        },
                    )
                    output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
