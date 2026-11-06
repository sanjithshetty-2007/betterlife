package com.sanjith.betterlife.data.db

/**
 * The SQL of migration 4 -> 5 (Prompt 14), copied from the exported schema 5.json so the migrated
 * tables are exactly what Room expects. Never edit it: a new change needs a new migration.
 */
internal object HabitSchemaV5 {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `avatar` TEXT, `category` TEXT NOT NULL, `essential` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `reminderTime` TEXT, `sortOrder` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL DEFAULT 'DIRTY', `serverUpdatedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_habits_deletedAt` ON `habits` (`deletedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_habits_syncState` ON `habits` (`syncState`)",
        "CREATE TABLE IF NOT EXISTS `habit_logs` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `localDate` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL DEFAULT 'DIRTY', `serverUpdatedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_localDate` ON `habit_logs` (`habitId`, `localDate`)",
        "CREATE INDEX IF NOT EXISTS `index_habit_logs_localDate` ON `habit_logs` (`localDate`)",
        "CREATE INDEX IF NOT EXISTS `index_habit_logs_syncState` ON `habit_logs` (`syncState`)",
        "CREATE TABLE IF NOT EXISTS `milestone_ledger` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `streakStartDate` TEXT NOT NULL, `milestone` INTEGER NOT NULL, `quiet` INTEGER NOT NULL, `celebratedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_milestone_ledger_habitId_streakStartDate_milestone` ON `milestone_ledger` (`habitId`, `streakStartDate`, `milestone`)",
        "CREATE TABLE IF NOT EXISTS `pause_ranges` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `reason` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL DEFAULT 'DIRTY', `serverUpdatedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_pause_ranges_deletedAt` ON `pause_ranges` (`deletedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_pause_ranges_syncState` ON `pause_ranges` (`syncState`)",
        "CREATE TABLE IF NOT EXISTS `celebration_messages` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `milestone` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_celebration_messages_deletedAt` ON `celebration_messages` (`deletedAt`)",
    )
}
