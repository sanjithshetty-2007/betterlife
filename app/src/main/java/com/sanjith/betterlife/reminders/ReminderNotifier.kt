package com.sanjith.betterlife.reminders

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sanjith.betterlife.R
import com.sanjith.betterlife.reminders.fullscreen.FullScreenReminderActivity

/** Posts reminder notifications: with a full-screen intent (Path B) or plain (the fallback). */
public class ReminderNotifier(private val context: Context) {

    /** Returns false when Android refused to post (notifications off or permission revoked). */
    public fun postFullScreen(notificationId: Int, title: String, text: String): Boolean {
        val activityIntent = FullScreenReminderActivity.intent(context, title, notificationId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.FULL_SCREEN)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return notify(notificationId, notification)
    }

    public fun postPlain(notificationId: Int, title: String, text: String): Boolean {
        val notification = NotificationCompat.Builder(context, NotificationChannels.NORMAL)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        return notify(notificationId, notification)
    }

    private fun notify(notificationId: Int, notification: android.app.Notification): Boolean {
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
}
