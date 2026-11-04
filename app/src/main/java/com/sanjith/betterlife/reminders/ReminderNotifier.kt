package com.sanjith.betterlife.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.reminders.fullscreen.FullScreenReminderActivity

/** Posts reminder notifications: the lock-screen pop-up (Path B), plain ones, or the missed summary. */
public class ReminderNotifier(private val context: Context) {

    /**
     * The one full-screen notification for the whole pop-up stack; it opens [FullScreenReminderActivity]
     * over the lock screen. Silent: the pop-up plays its own escalating sound. With [hideTitle] the lock
     * screen shows only "Reminder". Returns false when Android refused to post.
     */
    public fun postPopupStack(title: String, level: Importance, hideTitle: Boolean): Boolean {
        val pendingIntent = PendingIntent.getActivity(
            context,
            POPUP_STACK_ID,
            FullScreenReminderActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hidden = context.getString(R.string.popup_hidden_title)
        val builder = NotificationCompat.Builder(context, NotificationChannels.forLevel(level))
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(if (hideTitle) hidden else title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOngoing(true)
        if (hideTitle) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, NotificationChannels.forLevel(level))
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle(hidden)
                        .build(),
                )
        }
        return notify(POPUP_STACK_ID, builder.build())
    }

    public fun cancelPopupStack() {
        context.getSystemService(NotificationManager::class.java).cancel(POPUP_STACK_ID)
    }

    public fun postPlain(notificationId: Int, title: String, text: String, level: Importance = Importance.NORMAL): Boolean {
        val notification = NotificationCompat.Builder(context, NotificationChannels.forLevel(level))
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        return notify(notificationId, notification)
    }

    /** One digest for everything missed while the phone was off. A count only, so nothing private shows. */
    public fun postMissedSummary(count: Int): Boolean {
        val notification = NotificationCompat.Builder(context, NotificationChannels.DIGEST)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_summary_title, count, count))
            .setContentText(context.getString(R.string.missed_summary_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        return notify(MISSED_SUMMARY_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(notificationId: Int, notification: Notification): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
            false
        }
    }

    public companion object {
        private const val MISSED_SUMMARY_ID = 9_100
        private const val POPUP_STACK_ID = 9_200

        /** Stable notification id for a reminder, so a re-post replaces instead of stacking. */
        public fun idFor(reminderId: String): Int = reminderId.hashCode()
    }
}
