package com.sanjith.betterlife.reminders.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.domain.reminder.AlarmKind
import java.time.Instant

/**
 * [AlarmScheduler] on AlarmManager. Each reminder's alarm is identified by a URI built from its id
 * (betterlife://reminder/<id>), so the same reminder always maps to the same alarm: setting it again
 * replaces it, and ids can never collide the way hashed request codes could.
 */
public class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun set(reminderId: String, fireAt: Instant, kind: AlarmKind) {
        val operation = fireIntent(reminderId, fireAt)
        val at = fireAt.toEpochMilli()
        try {
            when {
                // Degrade rather than crash (the reason is logged as EXACT_ALARM_DENIED by the engine).
                !alarmManager.canScheduleExactAlarms() ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                kind == AlarmKind.ALARM_CLOCK ->
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showAppIntent()), operation)
                else -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using an inexact one", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    override fun cancel(reminderId: String) {
        // Extras are not part of a PendingIntent's identity, so any fire time matches the same alarm.
        val operation = fireIntent(reminderId, Instant.EPOCH)
        alarmManager.cancel(operation)
        operation.cancel()
    }

    // The digest is a notification, so an exact alarm that works in Doze is enough (no alarm clock).
    override fun setDigest(at: Instant) {
        val operation = digestIntent()
        val millis = at.toEpochMilli()
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact digest alarm refused, using an inexact one", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    override fun cancelDigest() {
        val operation = digestIntent()
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun digestIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_DIGEST)
            .setData(Uri.Builder().scheme(SCHEME).authority(DIGEST_HOST).build()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun fireIntent(reminderId: String, fireAt: Instant): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE)
            .setData(reminderUri(reminderId))
            .putExtra(ReminderAlarmReceiver.EXTRA_FIRE_AT, fireAt.toEpochMilli()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Tapping the alarm-clock icon in the status bar opens the app.
    private fun showAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    public companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE = 0
        private const val SCHEME = "betterlife"
        private const val HOST = "reminder"
        private const val DIGEST_HOST = "digest"

        public fun reminderUri(reminderId: String): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(reminderId).build()

        public fun reminderIdFrom(uri: Uri?): String? = uri?.takeIf { it.scheme == SCHEME && it.host == HOST }?.lastPathSegment
    }
}
