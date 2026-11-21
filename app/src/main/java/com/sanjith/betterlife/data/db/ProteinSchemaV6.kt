package com.sanjith.betterlife.data.db

/**
 * The SQL of migration 5 -> 6 (protein tracking), matching the exported schema 6.json so the
 * migrated table is exactly what Room expects. Never edit it: a new change needs a new migration.
 */
internal object ProteinSchemaV6 {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `protein_days` (`id` TEXT NOT NULL, `localDate` TEXT NOT NULL, `grams` INTEGER NOT NULL, `targetGrams` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_protein_days_localDate` ON `protein_days` (`localDate`)",
    )
}
