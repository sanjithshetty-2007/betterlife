package com.sanjith.betterlife.data.backup

import android.database.sqlite.SQLiteDatabase
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.backup.BackupManifest
import com.sanjith.betterlife.domain.backup.BackupRules
import com.sanjith.betterlife.domain.backup.RestoreProblem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A backup read from a file and checked, waiting for the owner to confirm the restore. */
public data class StagedBackup(val manifest: BackupManifest, val databaseFile: File)

/** A backup that cannot be restored, with the reason the UI explains. */
public class BackupException(public val problem: RestoreProblem) : Exception(problem.name)

/**
 * Manual export and import of the whole database (Prompt 13, context.md 11). A backup is a zip with
 * manifest.properties and a consistent copy of the database made with VACUUM INTO (safe while the
 * app keeps writing). Restore never runs blindly: checksum and schema are checked first, a safety
 * copy of today's data is kept, and the restored file then REPLACES the live one; the caller restarts
 * the app so Room reopens it (and migrates an older schema) and every alarm is rescheduled.
 */
public class BackupEngine(
    private val database: () -> BetterLifeDatabase,
    private val databaseFile: File,
    private val workDir: File,
    private val safetyDir: File,
    private val appVersion: String,
    private val nowMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Writes a backup to [out]; returns its manifest. */
    public suspend fun export(out: OutputStream): BackupManifest = withContext(ioDispatcher) {
        val copy = File(workDir, EXPORT_COPY).also { it.delete() }
        try {
            vacuumInto(copy)
            val manifest = BackupManifest(
                formatVersion = BackupManifest.FORMAT_VERSION,
                appVersion = appVersion,
                schemaVersion = schemaOf(copy),
                createdAt = Instant.ofEpochMilli(nowMillis()),
                counts = countsOf(copy),
                databaseBytes = copy.length(),
                sha256 = sha256(copy),
            )
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(BackupManifest.encode(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                copy.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            manifest
        } finally {
            copy.delete()
        }
    }

    /** Reads and checks a backup; throws [BackupException] when it must not be restored. */
    public suspend fun stage(input: InputStream): StagedBackup = withContext(ioDispatcher) {
        val staged = File(workDir, STAGED_COPY).also { it.delete() }
        var manifestText: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    MANIFEST_ENTRY -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                    DATABASE_ENTRY -> staged.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        val manifest = manifestText?.let(BackupManifest::decode)
        val sha = staged.takeIf { it.exists() }?.let(::sha256)
        BackupRules.problem(manifest, BetterLifeDatabase.VERSION, sha)?.let { throw BackupException(it) }
        // The checksum matched; the file must also really be a database of that schema.
        val readable = runCatching { schemaOf(staged) == manifest!!.schemaVersion }.getOrDefault(false)
        if (!readable) throw BackupException(RestoreProblem.DATABASE_UNREADABLE)
        StagedBackup(manifest!!, staged)
    }

    /**
     * Keeps a safety copy of the current data, then replaces the live database with [staged].
     * Every synced row is marked for a full re-sync. Room is closed; the caller must restart the app.
     * Returns the safety copy.
     */
    public suspend fun restore(staged: StagedBackup): File = withContext(ioDispatcher) {
        safetyDir.mkdirs()
        val safety = File(safetyDir, "safety-${nowMillis()}.db")
        vacuumInto(safety)
        pruneSafetyCopies()
        markForResync(staged.databaseFile)
        database().close()
        listOf(WAL_SUFFIX, SHM_SUFFIX, JOURNAL_SUFFIX).forEach { File(databaseFile.path + it).delete() }
        staged.databaseFile.copyTo(databaseFile, overwrite = true)
        staged.databaseFile.delete()
        safety
    }

    private fun vacuumInto(target: File) {
        target.delete()
        database().openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.path))
    }

    // Restored data must be pushed again in full once sync exists (context.md 11, docs/PLAN_CHANGES.md Prompt 29).
    private fun markForResync(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            SYNCED_TABLES.filter { db.hasTable(it) }.forEach { db.execSQL("UPDATE $it SET syncState = 'DIRTY'") }
            if (db.hasTable(SYNC_CURSORS)) db.execSQL("DELETE FROM $SYNC_CURSORS")
        }
    }

    // Never delete the last good copies: keep the newest few safety copies.
    private fun pruneSafetyCopies() {
        safetyDir.listFiles { f -> f.name.startsWith("safety-") }
            ?.sortedByDescending { it.name.removePrefix("safety-").removeSuffix(".db").toLongOrNull() ?: 0L }
            ?.drop(SAFETY_COPIES_KEPT)
            ?.forEach { it.delete() }
    }

    private fun schemaOf(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }

    private fun countsOf(file: File): Map<String, Int> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            COUNTED.filterKeys { db.hasTable(it) }.mapValues { (table, onlyLive) ->
                val where = if (onlyLive) " WHERE deletedAt IS NULL" else ""
                db.rawQuery("SELECT COUNT(*) FROM $table$where", null).use { c -> c.moveToFirst(); c.getInt(0) }
            }
        }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    public companion object {
        private const val MANIFEST_ENTRY = "manifest.properties"
        private const val DATABASE_ENTRY = "betterlife.db"
        private const val EXPORT_COPY = "export-copy.db"
        private const val STAGED_COPY = "import-staged.db"
        private const val WAL_SUFFIX = "-wal"
        private const val SHM_SUFFIX = "-shm"
        private const val JOURNAL_SUFFIX = "-journal"
        private const val SYNC_CURSORS = "sync_cursors"
        private const val SAFETY_COPIES_KEPT = 5

        /** Tables whose live (not deleted) rows are counted for the preview; the log counts every row. */
        public val COUNTED: Map<String, Boolean> = linkedMapOf(
            "tasks" to true,
            "events" to true,
            "standalone_reminders" to true,
            "reminders" to true,
            "projects" to true,
            "reminder_log" to false,
        )

        // hasTable() skips the habit tables when restoring a backup made before database version 5.
        private val SYNCED_TABLES = listOf(
            "tasks", "projects", "events", "reminders", "standalone_reminders", "habits", "habit_logs", "pause_ranges",
        )

        public fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private const val BUFFER = 64 * 1024
    }
}
