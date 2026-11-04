package com.sanjith.betterlife.reminders.daily

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.settings.SettingsRepository
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.reminders.NotificationChannels
import kotlinx.coroutines.launch
import java.time.LocalTime

/** The two daily nudges of Prompt 9; both off by default. */
public enum class DailyPrompt { MORNING_CHECK_IN, EVENING_WRAP_UP }

/**
 * Keeps one alarm per enabled daily prompt, at its next time. Uses the same AlarmManager approach as
 * reminders (exact, works in Doze); re-armed on boot, app launch and whenever the settings change.
 * [clock] is the real clock.
 */
public class DailyPrompts(
    private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    public suspend fun rescheduleAll() {
        val s = settings.current()
        apply(DailyPrompt.MORNING_CHECK_IN, s.morningCheckInEnabled, s.morningCheckInTime)
        apply(DailyPrompt.EVENING_WRAP_UP, s.eveningWrapUpEnabled, s.eveningWrapUpTime)
    }

    private fun apply(prompt: DailyPrompt, enabled: Boolean, time: LocalTime) {
        val operation = intent(prompt)
        if (!enabled) {
            alarmManager.cancel(operation)
            return
        }
        val now = clock.zonedNow()
        val today = now.with(time).withSecond(0).withNano(0)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        val at = next.toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun intent(prompt: DailyPrompt): PendingIntent = PendingIntent.getBroadcast(
        context,
        prompt.ordinal,
        Intent(context, DailyPromptReceiver::class.java).setAction(ACTION).putExtra(EXTRA_PROMPT, prompt.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val ACTION = "com.sanjith.betterlife.action.DAILY_PROMPT"
        const val EXTRA_PROMPT = "prompt"
    }
}

/** Posts the nudge (opening the check-in screen) and arms tomorrow's. */
public class DailyPromptReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyPrompts.ACTION) return
        val prompt = intent.getStringExtra(DailyPrompts.EXTRA_PROMPT)?.let { name -> DailyPrompt.entries.firstOrNull { it.name == name } } ?: return
        val (title, text) = when (prompt) {
            DailyPrompt.MORNING_CHECK_IN -> R.string.nudge_morning_title to R.string.nudge_morning_text
            DailyPrompt.EVENING_WRAP_UP -> R.string.nudge_evening_title to R.string.nudge_evening_text
        }
        val open = PendingIntent.getActivity(
            context,
            prompt.ordinal,
            MainActivity.openRouteIntent(context, MainActivity.ROUTE_CHECK_INS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.NUDGES)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(NOTIFICATION_BASE_ID + prompt.ordinal, notification)
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call: skip today's nudge.
            }
        }
        val container = (context.applicationContext as BetterLifeApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.dailyPrompts.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val NOTIFICATION_BASE_ID = 9_300
    }
}
