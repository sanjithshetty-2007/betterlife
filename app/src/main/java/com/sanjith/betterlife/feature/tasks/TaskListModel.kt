package com.sanjith.betterlife.feature.tasks

import com.sanjith.betterlife.data.db.entity.ProjectEntity
import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.data.repository.due
import com.sanjith.betterlife.data.repository.facts
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.domain.task.TaskBucket
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskRules
import java.time.LocalDate
import java.time.ZonedDateTime

public enum class TaskTab { TODAY, UPCOMING, OVERDUE, INBOX }

/** One row of a task list. */
public data class TaskRowUi(
    val id: String,
    val title: String,
    val due: TaskDue,
    val displayDate: LocalDate?,
    val priority: Priority,
    val projectName: String?,
    val subtasksDone: Int,
    val subtasksTotal: Int,
    val repeats: Boolean,
    val isTop: Boolean,
)

public data class TaskTabs(
    val today: List<TaskRowUi> = emptyList(),
    val upcoming: List<TaskRowUi> = emptyList(),
    val overdue: List<TaskRowUi> = emptyList(),
    val inbox: List<TaskRowUi> = emptyList(),
) {
    public fun rows(tab: TaskTab): List<TaskRowUi> = when (tab) {
        TaskTab.TODAY -> today
        TaskTab.UPCOMING -> upcoming
        TaskTab.OVERDUE -> overdue
        TaskTab.INBOX -> inbox
    }
}

/**
 * Sorts open top-level tasks into the four tabs. Subtasks are shown as a count on their parent, not
 * as rows. Today: the morning check-in's top 3 first, then priority, then time. Overdue: priority, then
 * oldest first. Upcoming: soonest first. Inbox: newest first.
 */
public fun buildTaskTabs(
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    topIds: Set<String>,
    now: ZonedDateTime,
): TaskTabs {
    val projectNames = projects.associate { it.id to it.name }
    val children = tasks.filter { it.parentTaskId != null }.groupBy { it.parentTaskId }
    val rows = tasks.filter { it.parentTaskId == null }.mapNotNull { task ->
        val facts = task.facts
        val bucket = TaskRules.bucket(facts, now)
        if (bucket == TaskBucket.DONE || bucket == TaskBucket.PROJECT_NO_DATE) return@mapNotNull null
        val subtasks = children[task.id].orEmpty()
        bucket to (task to TaskRowUi(
            id = task.id,
            title = task.title,
            due = task.due,
            displayDate = TaskRules.displayDate(facts, now.toLocalDate(), now.zone),
            priority = task.priority,
            projectName = task.projectId?.let(projectNames::get),
            subtasksDone = subtasks.count { it.completedAt != null },
            subtasksTotal = subtasks.size,
            repeats = facts.recurrence != null,
            isTop = task.id in topIds,
        ))
    }.groupBy({ it.first }, { it.second })

    fun dueKey(task: TaskEntity): Long = task.dueAtMillis ?: task.dueDate?.atStartOfDay(now.zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
    fun rank(p: Priority) = TaskRules.PRIORITY_ORDER.indexOf(p)

    return TaskTabs(
        today = rows[TaskBucket.TODAY].orEmpty()
            .sortedWith(compareBy({ !it.second.isTop }, { rank(it.first.priority) }, { dueKey(it.first) })).map { it.second },
        upcoming = rows[TaskBucket.UPCOMING].orEmpty().sortedBy { dueKey(it.first) }.map { it.second },
        overdue = rows[TaskBucket.OVERDUE].orEmpty()
            .sortedWith(compareBy({ rank(it.first.priority) }, { dueKey(it.first) })).map { it.second },
        inbox = rows[TaskBucket.INBOX].orEmpty().sortedByDescending { it.first.createdAt }.map { it.second },
    )
}
