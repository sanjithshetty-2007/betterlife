package com.sanjith.betterlife.reminders.fullscreen

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.reminders.popup.ReminderPopup
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import java.util.Date

/**
 * The pop-up on the lock screen (Path B). Opened by a notification's full-screen intent, which Android
 * only launches full screen when the phone is locked or the screen is off.
 */
public class FullScreenReminderActivity : ComponentActivity() {

    private val debugLog by lazy { (application as BetterLifeApp).container.debugLog }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also set in the manifest; set here too because some OEM builds only honour one of them.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close(OUTCOME_BACK)
            },
        )

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val timeText = DateFormat.getTimeFormat(this).format(Date())
        debugLog.add("Path B: full-screen Activity shown")
        setContent {
            BetterLifeTheme {
                ReminderPopup(
                    title = title,
                    timeText = timeText,
                    reduceMotion = isReducedMotion(),
                    onAction = { action -> close(action.name) },
                    onTapIgnored = { debugLog.add("Path B: tap ignored (tap guard)") },
                )
            }
        }
    }

    private fun close(outcome: String) {
        debugLog.add("Path B: dismissed with $outcome")
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)
        if (notificationId != NO_NOTIFICATION) {
            getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
        finish()
    }

    public companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val NO_NOTIFICATION = -1
        private const val OUTCOME_BACK = "BACK"

        public fun intent(context: Context, title: String, notificationId: Int): Intent =
            Intent(context, FullScreenReminderActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
