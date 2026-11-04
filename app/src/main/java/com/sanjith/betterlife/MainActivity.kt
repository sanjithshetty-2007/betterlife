package com.sanjith.betterlife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sanjith.betterlife.reminders.engine.DailyTopUpWorker
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import kotlinx.coroutines.launch

/** The single Activity. Every screen is a Composable inside it. */
public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced from Android 15; screens handle insets through Scaffold.
        enableEdgeToEdge()
        if (savedInstanceState == null) onAppLaunch()
        setContent {
            BetterLifeTheme {
                AppNavHost()
            }
        }
    }

    // Opening the app repairs the alarm schedule (agent.md section 5), off the main thread.
    private fun onAppLaunch() {
        val container = (application as BetterLifeApp).container
        container.appScope.launch { container.reminderEngine.rescheduleAll(RescheduleTrigger.APP_LAUNCH) }
        DailyTopUpWorker.enqueue(this)
    }
}
