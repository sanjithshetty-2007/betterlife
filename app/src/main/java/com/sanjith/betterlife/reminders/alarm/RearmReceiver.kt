package com.sanjith.betterlife.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import kotlinx.coroutines.launch

/** Re-arms the pending test alarm after a reboot or an app update; Android drops alarms on reboot. */
public class RearmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val why = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "App updated"
            else -> return
        }
        val container = (context.applicationContext as BetterLifeApp).container
        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                container.testAlarmController.rearm(why)
                container.debugLog.awaitWrites()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
