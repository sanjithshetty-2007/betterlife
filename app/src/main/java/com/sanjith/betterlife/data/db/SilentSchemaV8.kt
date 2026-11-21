package com.sanjith.betterlife.data.db

/**
 * The SQL of migration 7 -> 8 (silent reminders), matching the exported schema 8.json so the migrated
 * table is exactly what Room expects. Never edit it: a new change needs a new migration.
 */
internal object SilentSchemaV8 {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `silent_reminders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `times` TEXT NOT NULL, `pattern` TEXT NOT NULL, `seconds` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_silent_reminders_deletedAt` ON `silent_reminders` (`deletedAt`)",
    )
}
