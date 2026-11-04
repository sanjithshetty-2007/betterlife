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

/** An item to open on launch, from a pop-up's Reschedule button. */
public data class ItemRef(val type: ReminderItemType, val id: String)

/** The single Activity. Every screen is a Composable inside it. */
public class MainActivity : ComponentActivity() {

    private val itemToOpen = mutableStateOf<ItemRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced from Android 15; screens handle insets through Scaffold.
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            onAppLaunch()
            itemToOpen.value = itemRefFrom(intent)
        }
        setContent {
            BetterLifeTheme {
                AppNavHost(itemToOpen = itemToOpen.value, onItemOpened = { itemToOpen.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        itemRefFrom(intent)?.let { itemToOpen.value = it }
    }

    // Opening the app repairs the alarm schedule (agent.md section 5), off the main thread.
    private fun onAppLaunch() {
        val container = (application as BetterLifeApp).container
        container.appScope.launch { container.reminderEngine.rescheduleAll(RescheduleTrigger.APP_LAUNCH) }
        DailyTopUpWorker.enqueue(this)
    }

    private fun itemRefFrom(intent: Intent?): ItemRef? {
        val type = intent?.getStringExtra(EXTRA_ITEM_TYPE)?.let { name -> ReminderItemType.entries.firstOrNull { it.name == name } }
        val id = intent?.getStringExtra(EXTRA_ITEM_ID)
        return if (type != null && id != null) ItemRef(type, id) else null
    }

    public companion object {
        private const val EXTRA_ITEM_TYPE = "open_item_type"
        private const val EXTRA_ITEM_ID = "open_item_id"

        /** Opens the app on an item's editor (or just the app when there is no item). */
        public fun openItemIntent(context: Context, type: ReminderItemType?, id: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply {
                    if (type != null && id != null) {
                        putExtra(EXTRA_ITEM_TYPE, type.name)
                        putExtra(EXTRA_ITEM_ID, id)
                    }
                }
    }
}
