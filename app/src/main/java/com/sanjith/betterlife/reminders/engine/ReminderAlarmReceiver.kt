package com.sanjith.betterlife.reminders.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Receives a reminder's exact alarm. Minimal work (agent.md section 5): hand the reminder id and fire
 * time to the engine, which reads the database, runs the pipeline, shows it and writes the log.
 */
public class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DIGEST) {
            onDigest(context)
            return
        }
        if (intent.action != ACTION_FIRE) return
        val reminderId = AndroidAlarmScheduler.reminderIdFrom(intent.data) ?: return
        val fireAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_FIRE_AT, 0L))
        val container = (context.applicationContext as BetterLifeApp).container
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                val outcome = container.reminderEngine.onAlarm(reminderId, fireAt)
                container.debugLog.add("Reminder alarm ${reminderId.take(ID_PREVIEW)}: $outcome")
                container.debugLog.awaitWrites()
            } finally {
                pendingResult.finish()
            }
        }
    }

    // Pause or Quiet Hours ended (or should have): the engine re-checks and sends the one digest.
    private fun onDigest(context: Context) {
        val container = (context.applicationContext as BetterLifeApp).container
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                container.reminderEngine.deliverDigest()
                container.debugLog.add("Digest alarm handled")
                container.debugLog.awaitWrites()
            } finally {
                pendingResult.finish()
            }
        }
    }

    public companion object {
        public const val ACTION_DIGEST: String = "com.sanjith.betterlife.action.REMINDER_DIGEST"
        public const val ACTION_FIRE: String = "com.sanjith.betterlife.action.REMINDER_ALARM"
        public const val EXTRA_FIRE_AT: String = "fire_at"
        private const val ID_PREVIEW = 8
    }
}
