package com.sanjith.betterlife.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance

/**
 * Notification channels, one per reminder level plus nudges and digests (Prompt 7). The app cannot
 * change a channel's settings after creating it, so a changed importance or sound needs a new id
 * (agent.md section 5): bump the _v suffix and delete the old channel.
 */
public object NotificationChannels {
    public const val NORMAL: String = "reminder_normal_v1"
    public const val FULL_SCREEN: String = "reminder_full_screen_v1"
    public const val ALARM: String = "reminder_alarm_v1"
    public const val NUDGES: String = "nudges_v1"
    public const val DIGEST: String = "digest_v1"
    public const val SERVICE: String = "background_work_v1"

    public fun forLevel(level: Importance): String = when (level) {
        Importance.NORMAL -> NORMAL
        Importance.FULL_SCREEN -> FULL_SCREEN
        Importance.ALARM -> ALARM
    }

    public fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                channel(context, NORMAL, R.string.channel_normal, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, FULL_SCREEN, R.string.channel_full_screen, NotificationManager.IMPORTANCE_HIGH),
                alarmChannel(context),
                channel(context, NUDGES, R.string.channel_nudges, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, DIGEST, R.string.channel_digest, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, SERVICE, R.string.channel_service, NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    private fun channel(context: Context, id: String, name: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance)

    // Plays on the alarm stream, so it is heard even when the ringer is on silent (context.md 5.1).
    private fun alarmChannel(context: Context) =
        channel(context, ALARM, R.string.channel_alarm, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
}
