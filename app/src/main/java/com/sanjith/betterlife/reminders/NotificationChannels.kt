package com.sanjith.betterlife.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.sanjith.betterlife.R

/**
 * Notification channels. The app cannot change a channel's settings after creating it, so a changed
 * importance needs a new id (agent.md section 5). Prompt 7 adds the full per-level set.
 */
public object NotificationChannels {
    public const val FULL_SCREEN: String = "reminder_full_screen_v1"
    public const val NORMAL: String = "reminder_normal_v1"
    public const val SERVICE: String = "background_work_v1"

    public fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    FULL_SCREEN,
                    context.getString(R.string.channel_full_screen),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    NORMAL,
                    context.getString(R.string.channel_normal),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    SERVICE,
                    context.getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }
}
