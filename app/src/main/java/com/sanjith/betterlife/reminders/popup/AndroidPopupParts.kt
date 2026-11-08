package com.sanjith.betterlife.reminders.popup

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.popup.PopupCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Watches a card's task, event or standalone item in Room, so the pop-up closes or updates when it changes elsewhere. */
public class RoomItemWatcher(private val db: BetterLifeDatabase) : ItemWatcher {
    override fun watch(card: PopupCard): Flow<WatchedItem?> {
        val id = card.itemId ?: return flowOf(WatchedItem(card.title))
        return when (card.itemType) {
            ReminderItemType.TASK -> db.taskDao().observeById(id).map { task ->
                task?.takeIf { it.completedAt == null }?.let { WatchedItem(it.title) }
            }
            ReminderItemType.EVENT -> db.eventDao().observeById(id).map { event -> event?.let { WatchedItem(it.title) } }
            ReminderItemType.STANDALONE -> db.standaloneReminderDao().observeById(id).map { item ->
                item?.takeIf { it.doneAt == null }?.let { WatchedItem(it.title) }
            }
            ReminderItemType.HABIT -> db.habitDao().observeById(id).map { habit ->
                habit?.takeIf { !it.archived }?.let { WatchedItem(it.name) }
            }
            null -> flowOf(WatchedItem(card.title))
        }
    }
}
