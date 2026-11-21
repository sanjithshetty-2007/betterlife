package com.sanjith.betterlife.reminders.popup

import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.tracker.DailyAmount
import com.sanjith.betterlife.domain.tracker.TrackerKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Watches a card's task, event or standalone item in Room, so the pop-up closes or updates when it changes elsewhere. */
public class RoomItemWatcher(
    private val db: BetterLifeDatabase,
    private val trackerDay: (TrackerKind, LocalDate) -> Flow<DailyAmount?>,
) : ItemWatcher {
    override fun watch(card: PopupCard): Flow<WatchedItem?> {
        // A tracker card closes when its day is logged anywhere (the dashboard, another card).
        card.ask?.let { ask ->
            return trackerDay(ask.kind, ask.date).map { day -> if (day == null) WatchedItem(card.title) else null }
        }
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
