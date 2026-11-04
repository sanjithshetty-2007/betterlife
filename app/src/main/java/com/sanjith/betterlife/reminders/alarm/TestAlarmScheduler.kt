package com.sanjith.betterlife.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.domain.alarm.AlarmKind

/** Result of asking Android for an exact alarm. */
public enum class ScheduleResult { SCHEDULED, EXACT_ALARMS_DENIED }

/**
 * Schedules the single Prompt 4 test alarm. One fixed request code means scheduling again replaces
 * the previous alarm instead of adding a second one. Prompt 7 replaces this with per-reminder ids.
 */
public class TestAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    public fun schedule(triggerAtMillis: Long, kind: AlarmKind): ScheduleResult {
        // Checked at the moment of use: the permission can change at any time.
        if (!alarmManager.canScheduleExactAlarms()) return ScheduleResult.EXACT_ALARMS_DENIED
        val operation = alarmIntent(kind)
        when (kind) {
            AlarmKind.EXACT_ALLOW_WHILE_IDLE ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            AlarmKind.ALARM_CLOCK ->
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showAppIntent()), operation)
        }
        return ScheduleResult.SCHEDULED
    }

    public fun cancel() {
        // The kind extra is not part of PendingIntent identity, so either kind cancels the same alarm.
        alarmManager.cancel(alarmIntent(AlarmKind.EXACT_ALLOW_WHILE_IDLE))
    }

    private fun alarmIntent(kind: AlarmKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_TEST_ALARM,
        Intent(context, TestAlarmReceiver::class.java)
            .setAction(TestAlarmReceiver.ACTION_FIRE)
            .putExtra(TestAlarmReceiver.EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Tapping the alarm-clock icon in the status bar opens the app.
    private fun showAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_SHOW_APP,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE_TEST_ALARM = 4_001
        const val REQUEST_CODE_SHOW_APP = 4_002
    }
}
