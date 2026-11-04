package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.entity.TaskEntity
import com.sanjith.betterlife.domain.recurrence.RecurrenceRuleText
import com.sanjith.betterlife.domain.recurrence.RepeatMode
import com.sanjith.betterlife.domain.task.TaskDue
import com.sanjith.betterlife.domain.task.TaskFacts
import java.time.Instant

/** The task's due as the pure task rules see it. */
public val TaskEntity.due: TaskDue
    get() = when {
        dueAtMillis != null && dueZoneId != null -> TaskDue.At(Instant.ofEpochMilli(dueAtMillis), dueZoneId)
        dueDate != null -> TaskDue.OnDay(dueDate)
        else -> TaskDue.None
    }

public val TaskEntity.facts: TaskFacts
    get() = TaskFacts(due, projectId != null, completedAt != null, RecurrenceRuleText.decode(recurrenceRule))

public val TaskEntity.repeatMode: RepeatMode
    get() = if (repeatFromCompletion) RepeatMode.FROM_COMPLETION else RepeatMode.FROM_DUE_DATE

/** Copies [due] into the entity's three due columns. */
public fun TaskEntity.withDue(due: TaskDue): TaskEntity = when (due) {
    is TaskDue.At -> copy(dueAtMillis = due.instant.toEpochMilli(), dueZoneId = due.zone, dueDate = null)
    is TaskDue.OnDay -> copy(dueAtMillis = null, dueZoneId = null, dueDate = due.date)
    TaskDue.None -> copy(dueAtMillis = null, dueZoneId = null, dueDate = null)
}
