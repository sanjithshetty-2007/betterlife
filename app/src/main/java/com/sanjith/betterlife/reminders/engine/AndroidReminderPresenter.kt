package com.sanjith.betterlife.reminders.engine

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.text.format.DateFormat
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.popup.ReminderPopupService
import java.util.Date

/** [ReminderPresenter] using the Prompt 3/4 pop-up service and notifications. */
public class AndroidReminderPresenter(
    private val context: Context,
    private val debugLog: DebugLog,
) : ReminderPresenter {

    private val notifier = ReminderNotifier(context)

    override fun showPopup(reminderId: String, title: String, level: Importance) {
        val notificationId = ReminderNotifier.idFor(reminderId)
        try {
            ReminderPopupService.start(context, delayMillis = 0L, title = title, notificationId = notificationId, level = level)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // The exact alarm should allow this start; if Android refuses anyway, the lock-screen path still works.
            debugLog.add("Pop-up service refused (${e.javaClass.simpleName}); full-screen notification instead")
            notifier.postFullScreen(notificationId, title, timeNow(), level)
        }
    }

    override fun postNotification(reminderId: String, title: String, level: Importance) {
        notifier.postPlain(ReminderNotifier.idFor(reminderId), title, timeNow(), level)
    }

    override fun postMissedSummary(count: Int) {
        notifier.postMissedSummary(count)
    }

    private fun timeNow(): String = DateFormat.getTimeFormat(context).format(Date())
}
