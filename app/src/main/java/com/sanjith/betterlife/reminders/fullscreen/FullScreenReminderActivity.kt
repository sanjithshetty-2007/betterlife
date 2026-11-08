package com.sanjith.betterlife.reminders.fullscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.reminders.popup.CardAction
import com.sanjith.betterlife.reminders.popup.PopupHost
import com.sanjith.betterlife.ui.theme.BetterLifeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The pop-up on the lock screen (Path B). Opened by the full-screen notification, which Android only
 * launches full screen when the phone is locked or the screen is off. Shows the same shared stack as
 * the overlay and closes itself when the stack is empty.
 */
public class FullScreenReminderActivity : ComponentActivity() {

    private val container by lazy { (application as BetterLifeApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also set in the manifest; set here too because some OEM builds only honour one of them.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // The whole phone stays lit at full brightness while the reminder is up (owner, 2026-11-08).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val top = container.popupCoordinator.state.value.cards.lastOrNull() ?: return finish()
                    container.popupCoordinator.act(top.key, CardAction.Dismiss)
                }
            },
        )
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        container.debugLog.add("Path B: full-screen Activity shown")
        setContent { BetterLifeTheme { PopupHost(container, onScreenLock = locked) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.popupCoordinator.state.first { it.isEmpty }
                finish()
            }
        }
    }

    public companion object {
        public fun intent(context: Context): Intent =
            Intent(context, FullScreenReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
