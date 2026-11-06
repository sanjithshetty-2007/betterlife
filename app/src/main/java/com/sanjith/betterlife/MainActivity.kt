package com.sanjith.betterlife

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.reminders.engine.DailyTopUpWorker
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import kotlinx.coroutines.launch

/** The single Activity. Every screen is a Composable inside it. */
public class MainActivity : ComponentActivity() {

    /** A screen another part of the app asked to open (a pop-up's Reschedule, a nudge). */
    private val routeToOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced from Android 15; screens handle insets through Scaffold.
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            onAppLaunch()
            routeToOpen.value = intent?.getStringExtra(EXTRA_ROUTE)
        }
        setContent {
            BetterLifeTheme {
                AppNavHost(routeToOpen = routeToOpen.value, onRouteOpened = { routeToOpen.value = null })
            }
        }
    }

    // Reminder Health is re-checked on every launch and every return to the app, for example from a
    // system settings page, so the Home banner and the health rows are never stale (Prompt 11).
    override fun onResume() {
        super.onResume()
        val container = (application as BetterLifeApp).container
        container.appScope.launch { container.setupRepository.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { routeToOpen.value = it }
    }

    // Opening the app repairs the alarm schedule (agent.md section 5), off the main thread.
    private fun onAppLaunch() {
        val container = (application as BetterLifeApp).container
        container.appScope.launch {
            container.reminderEngine.rescheduleAll(RescheduleTrigger.APP_LAUNCH)
            container.dailyPrompts.rescheduleAll()
        }
        // Pull on app open and keep sync running; it never blocks anything (Prompt S2).
        container.startSync()
        DailyTopUpWorker.enqueue(this)
    }

    public companion object {
        private const val EXTRA_ROUTE = "open_route"
        public const val ROUTE_CHECK_INS: String = Routes.CHECK_INS
        public const val ROUTE_CENTER: String = Routes.CENTER
        public const val ROUTE_QUIET: String = Routes.QUIET

        public fun openRouteIntent(context: Context, route: String): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTE, route)

        /** Opens the app on an item's editor (or just the app when there is no item). */
        public fun openItemIntent(context: Context, type: ReminderItemType?, id: String?): Intent {
            val route = when {
                id == null || type == null -> Routes.HOME
                type == ReminderItemType.TASK -> Routes.taskEditor(id)
                type == ReminderItemType.EVENT -> Routes.eventEditor(id)
                else -> Routes.reminderEditor(id)
            }
            return openRouteIntent(context, route)
        }
    }
}
