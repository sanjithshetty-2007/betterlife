package com.sanjith.betterlife.reminders.alarm

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.popup.ReminderPopupService
import kotlinx.coroutines.launch

/**
 * Receives the exact alarm. Does the minimum (agent.md section 5): hand over to the pop-up service,
 * which decides the path and owns the overlay. The exact alarm is what allows this background
 * foreground-service start; if Android refuses anyway, a full-screen notification is posted directly.
 */
public class TestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val container = (context.applicationContext as BetterLifeApp).container
        val log = container.debugLog
        log.add("Alarm received (${intent.getStringExtra(EXTRA_KIND)})")

        try {
            ReminderPopupService.showTest(context, delayMillis = 0L)
            log.add("Pop-up service start requested from the receiver")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            log.add("Service start refused (${e.javaClass.simpleName}); posting a full-screen notification instead")
            ReminderNotifier(context).postPopupStack(
                context.getString(R.string.popup_test_title),
                Importance.FULL_SCREEN,
                hideTitle = false,
            )
        }

        val pendingResult = goAsync()
        container.appScope.launch {
            try {
                container.testAlarmController.markFired()
                log.awaitWrites()
            } finally {
                pendingResult.finish()
            }
        }
    }

    public companion object {
        public const val ACTION_FIRE: String = "com.sanjith.betterlife.action.TEST_ALARM"
        public const val EXTRA_KIND: String = "kind"
    }
}
