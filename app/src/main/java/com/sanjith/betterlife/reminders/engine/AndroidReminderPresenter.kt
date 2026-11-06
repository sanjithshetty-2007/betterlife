package com.sanjith.betterlife.reminders.engine

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.text.format.DateFormat
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.popup.PopupCoordinator
import com.sanjith.betterlife.reminders.popup.ReminderPopupService
import java.time.Instant
import java.util.Date

/** [ReminderPresenter] that adds a card to the shared pop-up stack, or posts a notification. */
public class AndroidReminderPresenter(
    private val context: Context,
    private val debugLog: DebugLog,
    private val coordinator: () -> PopupCoordinator,
    private val clock: Clock,
) : ReminderPresenter {

    private val notifier = ReminderNotifier(context)

    override fun showPopup(context: FireContext, level: Importance) {
        coordinator().show(context.toCard(level))
        try {
            ReminderPopupService.show(this.context)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // The exact alarm should allow this start; if Android refuses anyway, the lock-screen path
            // still shows the same stack (the card is already in the coordinator).
            debugLog.add("Pop-up service refused (${e.javaClass.simpleName}); full-screen notification instead")
            notifier.postPopupStack(context.title, level, hideTitle = false)
        }
    }

    override fun postNotification(reminderId: String, title: String, level: Importance) {
        notifier.postPlain(ReminderNotifier.idFor(reminderId), title, timeNow(), level)
    }

    override fun postMissedSummary(count: Int) {
        notifier.postMissedSummary(count)
    }

    override fun postDigest(titles: List<String>) {
        notifier.postDigest(titles)
    }

    override fun postCalmNotice(until: Instant) {
        notifier.postCalmNotice(DateFormat.getTimeFormat(context).format(Date.from(until)))
    }

    private fun FireContext.toCard(level: Importance) = PopupCard(
        key = reminderId,
        reminderId = reminderId,
        title = title,
        notesPreview = notes?.take(NOTES_PREVIEW_CHARS),
        importance = level,
        itemType = itemType,
        itemId = itemId,
        itemTime = itemTime,
        itemAllDay = itemAllDay,
        offsetMinutes = offsetMinutes,
        eventEnd = eventEnd,
        snoozeCount = snoozeCount,
        shownAt = clock.now(),
    )

    private fun timeNow(): String = DateFormat.getTimeFormat(context).format(Date.from(clock.now()))

    private companion object {
        const val NOTES_PREVIEW_CHARS = 140
    }
}
