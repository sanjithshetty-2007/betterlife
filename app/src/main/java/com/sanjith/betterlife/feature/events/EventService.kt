package com.sanjith.betterlife.feature.events

import com.sanjith.betterlife.data.db.entity.EventEntity
import com.sanjith.betterlife.data.db.entity.StandaloneReminderEntity
import com.sanjith.betterlife.data.repository.EventDraft
import com.sanjith.betterlife.data.repository.EventRepository
import com.sanjith.betterlife.data.repository.ReminderDraft
import com.sanjith.betterlife.data.repository.StandaloneDraft
import com.sanjith.betterlife.data.repository.StandaloneReminderRepository

/**
 * Every event and standalone-reminder action the screens offer, each followed by an alarm
 * reschedule, so editing, rescheduling, completing or deleting an item cancels or re-creates its
 * pending reminders (Prompt 10). Same pattern as TaskService.
 */
public class EventService(
    private val events: EventRepository,
    private val standalone: StandaloneReminderRepository,
    private val reschedule: suspend () -> Unit,
) {

    public suspend fun createEvent(draft: EventDraft, reminders: List<ReminderDraft>): String {
        val id = events.create(draft, reminders)
        reschedule()
        return id
    }

    public suspend fun saveEvent(event: EventEntity, reminders: List<ReminderDraft>) {
        events.updateWithReminders(event, reminders)
        reschedule()
    }

    public suspend fun deleteEvent(id: String) {
        events.softDelete(id)
        reschedule()
    }

    public suspend fun undoDeleteEvent(id: String) {
        events.restore(id)
        reschedule()
    }

    public suspend fun createReminder(draft: StandaloneDraft, reminders: List<ReminderDraft>): String {
        val id = standalone.create(draft, reminders)
        reschedule()
        return id
    }

    public suspend fun saveReminder(item: StandaloneReminderEntity, reminders: List<ReminderDraft>) {
        standalone.updateWithReminders(item, reminders)
        reschedule()
    }

    public suspend fun markReminderDone(id: String) {
        standalone.markDone(id)
        reschedule()
    }

    public suspend fun undoReminderDone(id: String) {
        standalone.markNotDone(id)
        reschedule()
    }

    public suspend fun deleteReminder(id: String) {
        standalone.softDelete(id)
        reschedule()
    }

    public suspend fun undoDeleteReminder(id: String) {
        standalone.restore(id)
        reschedule()
    }
}
