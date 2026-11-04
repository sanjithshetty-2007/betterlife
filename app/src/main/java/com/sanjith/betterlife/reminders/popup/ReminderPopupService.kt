package com.sanjith.betterlife.reminders.popup

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.domain.delivery.PresentationInputs
import com.sanjith.betterlife.domain.delivery.PresentationPath
import com.sanjith.betterlife.domain.delivery.decidePresentation
import com.sanjith.betterlife.reminders.NotificationChannels
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.overlay.OverlayController
import com.sanjith.betterlife.reminders.overlay.OverlayDismissReason
import com.sanjith.betterlife.reminders.overlay.OverlayShowResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Shows the test pop-up by the path [decidePresentation] picks, after an optional delay.
 *
 * Why a foreground service and not the receiver itself (Prompt 4 decision): a receiver's process may be
 * killed seconds after onReceive returns, which would take the overlay window with it. The
 * "systemExempted" type is allowed because the app holds USE_EXACT_ALARM; unlike "shortService" it has
 * no 3-minute limit, so the overlay can stay up until the owner answers. Starting it from the
 * background is allowed because an exact alarm just fired.
 */
public class ReminderPopupService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val log by lazy { (application as BetterLifeApp).container.debugLog }
    private val overlay by lazy { OverlayController(this) }
    private var pending: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pending?.isActive == true || overlay.isShowing) {
            log.add("Ignored: a pop-up is already pending or showing")
            return START_NOT_STICKY
        }
        val delayMillis = intent?.getLongExtra(EXTRA_DELAY_MILLIS, 0L) ?: 0L
        if (delayMillis > 0) log.add("Scheduled: pop-up in ${delayMillis / MILLIS_PER_SECOND} s")
        pending = scope.launch {
            delay(delayMillis)
            present()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        startForeground(
            SERVICE_NOTIFICATION_ID,
            serviceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException / ForegroundServiceTypeNotAllowedException both extend it.
        log.add("Could not enter the foreground (${e.javaClass.simpleName}); posting a full-screen notification")
        ReminderNotifier(this).postFullScreen(
            POPUP_NOTIFICATION_ID,
            getString(R.string.popup_test_title),
            getString(R.string.alarm_fallback_text),
        )
        false
    }

    private fun present() {
        val inputs = readInputs()
        val decision = decidePresentation(inputs)
        log.add("Decision: ${decision.path} (${decision.reason}) from $inputs")
        val title = getString(R.string.popup_test_title)
        val timeText = DateFormat.getTimeFormat(this).format(Date())
        when (decision.path) {
            PresentationPath.OVERLAY -> showOverlay(title, timeText)
            PresentationPath.FULL_SCREEN_INTENT -> {
                val posted = ReminderNotifier(this).postFullScreen(POPUP_NOTIFICATION_ID, title, timeText)
                log.add(if (posted) "Path B: full-screen notification posted" else "Path B: posting refused")
                stopSelf()
            }
            PresentationPath.NOTIFICATION_ONLY -> {
                val posted = ReminderNotifier(this).postPlain(POPUP_NOTIFICATION_ID, title, timeText)
                log.add(if (posted) "Fallback: plain notification posted" else "Fallback: posting refused")
                stopSelf()
            }
            PresentationPath.NONE -> {
                log.add("Nothing shown: notifications are off and no overlay is possible")
                stopSelf()
            }
        }
    }

    private fun showOverlay(title: String, timeText: String) {
        val result = overlay.show(
            onDismiss = { reason -> onOverlayDismissed(reason) },
        ) { dismiss ->
            ReminderPopup(
                title = title,
                timeText = timeText,
                reduceMotion = isReducedMotion(),
                onAction = { action -> dismiss(OverlayDismissReason.valueOf(action.name)) },
                onTapIgnored = { log.add("Path A: tap ignored (tap guard)") },
            )
        }
        when (result) {
            OverlayShowResult.Shown -> log.add("Path A: overlay shown")
            OverlayShowResult.AlreadyShowing -> log.add("Path A: overlay already showing, not stacked")
            is OverlayShowResult.Failed -> {
                log.add("Path A failed (${result.cause}), falling back to a notification")
                ReminderNotifier(this).postPlain(POPUP_NOTIFICATION_ID, title, timeText)
                stopSelf()
            }
        }
    }

    private fun onOverlayDismissed(reason: OverlayDismissReason) {
        log.add("Path A: overlay dismissed with $reason")
        stopSelf()
    }

    private fun readInputs(): PresentationInputs = PresentationInputs(
        isInteractive = getSystemService(PowerManager::class.java).isInteractive,
        isKeyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        canDrawOverlays = Settings.canDrawOverlays(this),
        canUseFullScreenIntent = getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
    )

    private fun serviceNotification() = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(getString(R.string.popup_service_notification))
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        overlay.remove()
        scope.cancel()
        log.add("Pop-up service stopped")
        super.onDestroy()
    }

    public companion object {
        private const val EXTRA_DELAY_MILLIS = "delay_millis"
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SERVICE_NOTIFICATION_ID = 9_001
        private const val POPUP_NOTIFICATION_ID = 9_002

        /** Throws ForegroundServiceStartNotAllowedException if Android refuses a background start. */
        public fun start(context: Context, delayMillis: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReminderPopupService::class.java).putExtra(EXTRA_DELAY_MILLIS, delayMillis),
            )
        }
    }
}
