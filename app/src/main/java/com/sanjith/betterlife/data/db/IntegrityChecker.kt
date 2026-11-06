package com.sanjith.betterlife.data.db

import androidx.room.withTransaction

/** What one integrity check found: the broken row ids (a count only for the log, which is history). */
public enum class IntegrityIssue { ORPHAN_REMINDER, REMINDER_OF_DELETED_ITEM, MISSING_PROJECT, MISSING_PARENT_TASK }

public data class IntegrityReport(val issues: Map<IntegrityIssue, List<String>>, val logRowsWithoutReminder: Int) {
    /** True when nothing needs fixing (log rows of removed reminders are fine: the log is history). */
    val clean: Boolean get() = issues.values.all { it.isEmpty() }
}

/**
 * The debug "database integrity check" (Prompt 13): finds references to rows that do not exist (or
 * were deleted) and repairs them the same way the app would have: reminders of a missing or deleted
 * item are soft-deleted (so their alarms are cancelled at the next reschedule), a missing project or
 * parent is cleared. Links and attachments join these checks when their tables exist (Prompts 21-23).
 */
public class IntegrityChecker(private val db: BetterLifeDatabase, private val nowMillis: () -> Long) {

    private val dao = db.integrityDao()

    public suspend fun check(): IntegrityReport = IntegrityReport(
        issues = mapOf(
            IntegrityIssue.ORPHAN_REMINDER to dao.orphanReminderIds(),
            IntegrityIssue.REMINDER_OF_DELETED_ITEM to dao.remindersOfDeletedItemIds(),
            IntegrityIssue.MISSING_PROJECT to dao.tasksWithMissingProjectIds(),
            IntegrityIssue.MISSING_PARENT_TASK to dao.tasksWithMissingParentIds(),
        ),
        logRowsWithoutReminder = dao.logRowsWithoutReminder(),
    )

    /** Repairs everything [check] finds, in one transaction; returns the report from before the fix. */
    public suspend fun fix(): IntegrityReport = db.withTransaction {
        val report = check()
        val now = nowMillis()
        val reminders = report.issues.getValue(IntegrityIssue.ORPHAN_REMINDER) + report.issues.getValue(IntegrityIssue.REMINDER_OF_DELETED_ITEM)
        reminders.chunked(SQL_CHUNK).forEach { dao.softDeleteReminders(it, now) }
        report.issues.getValue(IntegrityIssue.MISSING_PROJECT).chunked(SQL_CHUNK).forEach { dao.clearProject(it, now) }
        report.issues.getValue(IntegrityIssue.MISSING_PARENT_TASK).chunked(SQL_CHUNK).forEach { dao.clearParent(it, now) }
        report
    }

    private companion object {
        const val SQL_CHUNK = 500
    }
}
