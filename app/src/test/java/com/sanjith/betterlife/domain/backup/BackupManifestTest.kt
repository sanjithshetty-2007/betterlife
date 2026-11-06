package com.sanjith.betterlife.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

public class BackupManifestTest {

    private val manifest = BackupManifest(
        formatVersion = 1,
        appVersion = "0.1.0-debug",
        schemaVersion = 4,
        createdAt = Instant.parse("2026-11-06T10:42:00Z"),
        counts = mapOf("tasks" to 12, "events" to 3, "reminders" to 20, "reminder_log" to 140),
        databaseBytes = 98_304,
        sha256 = "ab".repeat(32),
    )

    @Test
    public fun manifestRoundTripsThroughItsText() {
        assertEquals(manifest, BackupManifest.decode(BackupManifest.encode(manifest)))
    }

    @Test
    public fun textThatIsNotAManifestIsRejected() {
        assertNull(BackupManifest.decode(""))
        assertNull(BackupManifest.decode("hello=world"))
        assertNull(BackupManifest.decode(BackupManifest.encode(manifest).replace("schemaVersion=4", "schemaVersion=four")))
        assertNull(BackupManifest.decode(BackupManifest.encode(manifest).replace("count.tasks=12", "count.tasks=lots")))
    }

    @Test
    public fun aGoodBackupFromTheSameOrAnOlderSchemaCanBeRestored() {
        assertNull(BackupRules.problem(manifest, appSchema = 4, actualSha256 = manifest.sha256))
        // Older schemas are migrated forward when Room opens them.
        assertNull(BackupRules.problem(manifest.copy(schemaVersion = 2), appSchema = 4, actualSha256 = manifest.sha256.uppercase()))
    }

    @Test
    public fun aBackupFromANewerSchemaIsRefused() {
        assertEquals(RestoreProblem.NEWER_SCHEMA, BackupRules.problem(manifest.copy(schemaVersion = 5), appSchema = 4, actualSha256 = manifest.sha256))
    }

    @Test
    public fun otherReasonsToRefuse() {
        assertEquals(RestoreProblem.NOT_A_BACKUP, BackupRules.problem(null, 4, manifest.sha256))
        assertEquals(RestoreProblem.NEWER_FORMAT, BackupRules.problem(manifest.copy(formatVersion = 2), 4, manifest.sha256))
        assertEquals(RestoreProblem.CHECKSUM_MISMATCH, BackupRules.problem(manifest, 4, "cd".repeat(32)))
        assertEquals(RestoreProblem.CHECKSUM_MISMATCH, BackupRules.problem(manifest, 4, null))
    }

    @Test
    public fun fileNamesSortByTimeAndUsePlainDigits() {
        val at = LocalDateTime.parse("2026-01-05T07:03").atZone(ZoneId.of("Asia/Kolkata"))
        assertEquals("betterlife-2026-01-05-0703.zip", BackupRules.fileName(at))
    }
}
