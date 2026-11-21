package com.sanjith.betterlife.data.db

/**
 * The SQL of migration 6 -> 7 (sleep tracking), matching the exported schema 7.json so the migrated
 * table is exactly what Room expects. Never edit it: a new change needs a new migration.
 */
internal object SleepSchemaV7 {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `sleep_days` (`id` TEXT NOT NULL, `localDate` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `targetMinutes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_days_localDate` ON `sleep_days` (`localDate`)",
    )
}
