package com.sanjith.betterlife.reminders.popup

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.delivery.PresentationPath
import com.sanjith.betterlife.domain.delivery.decidePresentation
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.popup.PopupCard
import com.sanjith.betterlife.reminders.NotificationChannels
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.engine.readPresentationInputs
import com.sanjith.betterlife.reminders.overlay.OverlayController
import com.sanjith.betterlife.reminders.overlay.OverlayShowResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Keeps the app alive while the pop-up stack is showing and puts the stack on screen: our own overlay
 * window while the phone is in use, or a full-screen notification opening [FullScreenReminderActivity]
 * when locked. The cards themselves live in [PopupCoordinator].
 *
 * Why a foreground service and not the receiver itself (Prompt 4 decision): a receiver's process may be
 * killed seconds after onReceive returns, which would take the overlay window with it. The
 * "systemExempted" type is allowed because the app holds USE_EXACT_ALARM and has no time limit.
 * Starting it from the background is allowed because an exact alarm just fired.
 */
public class ReminderPopupService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as BetterLifeApp).container }
    private val log by lazy { container.debugLog }
    private val overlay by lazy { OverlayController(this) }
    private var lockScreenPosted = false
    private var watchingStack = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        watchStackUntilEmpty()
        val testDelay = intent?.getLongExtra(EXTRA_TEST_DELAY_MILLIS, NO_TEST) ?: NO_TEST
        scope.launch {
            if (testDelay != NO_TEST) {
                log.add("Scheduled: test pop-up in ${testDelay / MILLIS_PER_SECOND} s")
                delay(testDelay)
                container.popupCoordinator.show(testCard())
            }
            // Wait until the card the caller added has reached the stack, then put the stack on screen.
            container.popupCoordinator.state.first { !it.isEmpty }
            present()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException / ForegroundServiceTypeNotAllowedException both extend it.
        log.add("Could not enter the foreground (${e.javaClass.simpleName}); lock-screen path only")
        ReminderNotifier(this).postPopupStack(getString(R.string.popup_hidden_title), Importance.FULL_SCREEN, hideTitle = true)
        false
    }

    /** Re-decided for every new reminder: the screen may have been locked since the overlay appeared. */
    private suspend fun present() {
        val inputs = readPresentationInputs(this)
        val decision = decidePresentation(inputs)
        log.add("Decision: ${decision.path} (${decision.reason}) from $inputs")
        when (decision.path) {
            PresentationPath.OVERLAY -> showOverlay()
            PresentationPath.FULL_SCREEN_INTENT -> postLockScreen()
            PresentationPath.NOTIFICATION_ONLY, PresentationPath.NONE -> fallBackToNotifications()
        }
    }

    private suspend fun showOverlay() {
        if (overlay.isShowing) return
        val result = overlay.show(onBack = ::dismissTopCard) { PopupHost(container, onScreenLock = false) }
        when (result) {
            OverlayShowResult.Shown -> log.add("Path A: overlay shown")
            OverlayShowResult.AlreadyShowing -> Unit
            is OverlayShowResult.Failed -> {
                log.add("Path A failed (${result.cause}), falling back to notifications")
                fallBackToNotifications()
            }
        }
    }

    private suspend fun postLockScreen() {
        if (lockScreenPosted) return
        val top = container.popupCoordinator.state.value.cards.lastOrNull() ?: return
        val hide = container.settingsRepository.current().hideTitleOnLockScreen
        lockScreenPosted = ReminderNotifier(this).postPopupStack(top.title, top.importance, hide)
        log.add(if (lockScreenPosted) "Path B: full-screen notification posted" else "Path B: posting refused")
    }

    private suspend fun fallBackToNotifications() {
        val notifier = ReminderNotifier(this)
        container.popupCoordinator.drain().forEach { card ->
            notifier.postPlain(card.reminderId?.let(ReminderNotifier::idFor) ?: TEST_NOTIFICATION_ID, card.title, "", Importance.NORMAL)
        }
    }

    private fun dismissTopCard() {
        val top = container.popupCoordinator.state.value.cards.lastOrNull() ?: return
        container.popupCoordinator.act(top.key, CardAction.Dismiss)
    }

    /** When the last card is answered: remove the window, clear the lock-screen notification, stop. */
    private fun watchStackUntilEmpty() {
        if (watchingStack) return
        watchingStack = true
        scope.launch {
            container.popupCoordinator.state.drop(1).first { it.isEmpty }
            log.add("Pop-up stack empty")
            stopSelf()
        }
    }

    private fun testCard(): PopupCard = PopupCard(
        key = "test-${UUID.randomUUID()}",
        reminderId = null,
        title = getString(R.string.popup_test_title),
        notesPreview = null,
        importance = Importance.FULL_SCREEN,
        itemType = null,
        itemId = null,
        itemTime = null,
        itemAllDay = false,
        offsetMinutes = 0,
        eventEnd = null,
        snoozeCount = 0,
        shownAt = container.realClock.now(),
    )

    private fun serviceNotification() = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(getString(R.string.popup_service_notification))
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        overlay.remove()
        ReminderNotifier(this).cancelPopupStack()
        scope.cancel()
        log.add("Pop-up service stopped")
        super.onDestroy()
    }

    public companion object {
        private const val EXTRA_TEST_DELAY_MILLIS = "test_delay_millis"
        private const val NO_TEST = -1L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SERVICE_NOTIFICATION_ID = 9_001
        private const val TEST_NOTIFICATION_ID = 9_002

        /**
         * Puts the current pop-up stack on screen (call after adding a card to the coordinator).
         * Throws ForegroundServiceStartNotAllowedException if Android refuses a background start.
         */
        public fun show(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReminderPopupService::class.java))
        }

        /** Debug: a test pop-up after [delayMillis] (Prompt 3 and 4 test screens). */
        public fun showTest(context: Context, delayMillis: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReminderPopupService::class.java).putExtra(EXTRA_TEST_DELAY_MILLIS, delayMillis),
            )
        }
    }
}
