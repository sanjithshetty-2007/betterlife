package com.sanjith.betterlife.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import kotlinx.coroutines.launch

/**
 * Re-arms everything after events that wipe or shift alarms: reboot and app update (Android drops
 * alarms), and clock or time-zone changes (fire times move). Covers both the reminder engine and the
 * Prompt 4 test alarm.
 */
public class RearmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RescheduleTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleTrigger.APP_UPDATED
            Intent.ACTION_TIME_CHANGED -> RescheduleTrigger.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleTrigger.TIMEZONE_CHANGED
            else -> return
        }
        val container = (context.applicationContext as BetterLifeApp).container
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                container.reminderEngine.rescheduleAll(trigger)
                container.dailyPrompts.rescheduleAll()
                container.debugLog.add("Reminder engine rescheduled ($trigger)")
                if (trigger == RescheduleTrigger.BOOT || trigger == RescheduleTrigger.APP_UPDATED) {
                    container.testAlarmController.rearm(if (trigger == RescheduleTrigger.BOOT) "Boot" else "App updated")
                }
                container.debugLog.awaitWrites()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
