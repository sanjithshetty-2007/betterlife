package com.sanjith.betterlife.data.repository

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.data.db.entity.ReminderEntity
import com.sanjith.betterlife.data.db.entity.ReminderLogEntity
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReasonCode
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.model.ReminderLogStatus
import com.sanjith.betterlife.domain.model.ReminderState
import kotlinx.coroutines.flow.Flow

/** Reminder rows. Creating and deleting them happens through the task and event repositories. */
public class ReminderRepository(
    db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
) {
    private val dao = db.reminderDao()

    public fun observeForItem(itemType: ReminderItemType, itemId: String): Flow<List<ReminderEntity>> =
        dao.observeForItem(itemType, itemId)

    public fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    /** Fire state is local only, so this does not mark the row for sync. */
    public suspend fun setState(id: String, state: ReminderState) {
        dao.setState(id, state, nowMillis())
    }
}

/** Append-only reminder history for the Reminder Center. */
public class ReminderLogRepository(
    db: BetterLifeDatabase,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
) {
    private val dao = db.reminderLogDao()

    public fun observeRecent(limit: Int): Flow<List<ReminderLogEntity>> = dao.observeRecent(limit)

    public fun observeCount(): Flow<Int> = dao.observeCount()

    /** [detail] is technical text for debugging; never put note text or other private content in it. */
    public suspend fun append(
        reminderId: String,
        status: ReminderLogStatus,
        level: Importance,
        reasonCode: ReasonCode? = null,
        detail: String? = null,
        firedAt: Long = nowMillis(),
    ) {
        val now = nowMillis()
        dao.insert(
            ReminderLogEntity(
                id = newId(),
                reminderId = reminderId,
                firedAt = firedAt,
                status = status,
                reasonCode = reasonCode,
                level = level,
                detail = detail,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }
}
