package com.sanjith.betterlife.reminders.popup

import android.content.Context
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.reminders.engine.ReminderEngine
import java.time.Instant

/** Sends pop-up answers to the reminder engine; Reschedule also opens the item's editor. */
public class AndroidPopupActions(
    private val context: Context,
    private val engine: () -> ReminderEngine,
) : PopupActions {

    override suspend fun done(reminderId: String) {
        engine().complete(reminderId)
    }

    override suspend fun snooze(reminderId: String, until: Instant) {
        engine().snooze(reminderId, until)
    }

    override suspend fun dismiss(reminderId: String) {
        engine().dismiss(reminderId)
    }

    override suspend fun reschedule(card: PopupCard) {
        card.reminderId?.let { engine().openedEditor(it) }
        // Allowed from the background: the overlay window (or lock-screen activity) is on screen.
        context.startActivity(MainActivity.openItemIntent(context, card.itemType, card.itemId))
    }

    override suspend fun timedOut(reminderId: String, timeoutMinutes: Int) {
        engine().timedOut(reminderId, timeoutMinutes)
    }
}
