package com.sanjith.betterlife.domain.backup

import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import java.util.Properties

/**
 * What a backup file says about itself (context.md section 11): format, app and schema versions,
 * when it was made, row counts for the preview, size and a SHA-256 checksum of the database inside.
 */
public data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Instant,
    val counts: Map<String, Int>,
    val databaseBytes: Long,
    val sha256: String,
) {
    public companion object {
        /** Bump only when the file layout changes, not for schema changes (those have schemaVersion). */
        public const val FORMAT_VERSION: Int = 1

        private const val KEY_FORMAT = "formatVersion"
        private const val KEY_APP = "appVersion"
        private const val KEY_SCHEMA = "schemaVersion"
        private const val KEY_CREATED = "createdAt"
        private const val KEY_BYTES = "databaseBytes"
        private const val KEY_SHA = "sha256"
        private const val COUNT_PREFIX = "count."

        /** Plain key=value text (java.util.Properties), readable in any text editor. */
        public fun encode(m: BackupManifest): String {
            val props = Properties()
            props[KEY_FORMAT] = m.formatVersion.toString()
            props[KEY_APP] = m.appVersion
            props[KEY_SCHEMA] = m.schemaVersion.toString()
            props[KEY_CREATED] = m.createdAt.toString()
            props[KEY_BYTES] = m.databaseBytes.toString()
            props[KEY_SHA] = m.sha256
            m.counts.forEach { (table, n) -> props["$COUNT_PREFIX$table"] = n.toString() }
            return StringWriter().also { props.store(it, null) }.toString()
        }

        /** Null when a required field is missing or unreadable: the file is not a BetterLife backup. */
        public fun decode(text: String): BackupManifest? {
            val props = Properties().apply { runCatching { load(StringReader(text)) }.getOrElse { return null } }
            fun get(key: String): String? = props.getProperty(key)
            return BackupManifest(
                formatVersion = get(KEY_FORMAT)?.toIntOrNull() ?: return null,
                appVersion = get(KEY_APP) ?: return null,
                schemaVersion = get(KEY_SCHEMA)?.toIntOrNull() ?: return null,
                createdAt = get(KEY_CREATED)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null,
                counts = props.stringPropertyNames().filter { it.startsWith(COUNT_PREFIX) }
                    .associate { it.removePrefix(COUNT_PREFIX) to (get(it)?.toIntOrNull() ?: return null) },
                databaseBytes = get(KEY_BYTES)?.toLongOrNull() ?: return null,
                sha256 = get(KEY_SHA) ?: return null,
            )
        }
    }
}

/** Why a backup cannot be restored. Each has a plain sentence in the UI. */
public enum class RestoreProblem { NOT_A_BACKUP, NEWER_FORMAT, NEWER_SCHEMA, CHECKSUM_MISMATCH, DATABASE_UNREADABLE }

public object BackupRules {

    /**
     * Never restore blindly (context.md 11): the format must be known, the schema not newer than the
     * app's (an older one is migrated forward on open), and the database must match its checksum.
     * Example: a schema 5 backup on an app at schema 4 -> NEWER_SCHEMA.
     */
    public fun problem(manifest: BackupManifest?, appSchema: Int, actualSha256: String?): RestoreProblem? = when {
        manifest == null -> RestoreProblem.NOT_A_BACKUP
        manifest.formatVersion > BackupManifest.FORMAT_VERSION -> RestoreProblem.NEWER_FORMAT
        manifest.schemaVersion > appSchema -> RestoreProblem.NEWER_SCHEMA
        actualSha256 == null || !actualSha256.equals(manifest.sha256, ignoreCase = true) -> RestoreProblem.CHECKSUM_MISMATCH
        else -> null
    }

    /** "betterlife-2026-11-06-1612.zip": sorts by time, safe on every file system. */
    public fun fileName(at: java.time.ZonedDateTime): String =
        // ROOT locale: digits must stay 0-9 whatever the phone's language.
        "betterlife-%04d-%02d-%02d-%02d%02d.zip".format(java.util.Locale.ROOT, at.year, at.monthValue, at.dayOfMonth, at.hour, at.minute)
}
