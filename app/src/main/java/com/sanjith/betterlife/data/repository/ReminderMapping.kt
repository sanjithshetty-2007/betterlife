package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.SyncState
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderState

/** A new reminder row for an item, scheduled and waiting to be pushed to sync. */
internal fun ReminderDraft.toEntity(id: String, itemType: ReminderItemType, itemId: String?, now: Long) = ReminderEntity(
    id = id,
    itemType = itemType,
    itemId = itemId,
    offsetMinutes = offsetMinutes,
    importance = importance,
    state = ReminderState.SCHEDULED,
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
    syncState = SyncState.DIRTY,
    serverUpdatedAt = null,
)
