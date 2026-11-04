package com.sanjith.betterlife.reminders.popup

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.domain.delivery.PresentationPath
import com.sanjith.betterlife.domain.delivery.decidePresentation
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.reminders.NotificationChannels
import com.sanjith.betterlife.reminders.ReminderNotifier
import com.sanjith.betterlife.reminders.engine.readPresentationInputs
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
 * Shows a reminder pop-up after an optional delay, by the path [decidePresentation] picks at that
 * moment (screen and lock state can change between the alarm and the pop-up).
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
        val request = PopupRequest.from(intent, getString(R.string.popup_test_title))
        if (!enterForeground(request)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pending?.isActive == true || overlay.isShowing) {
            // Stacking several reminders in one window comes in Prompt 8; until then, don't lose this one.
            log.add("A pop-up is already showing; this reminder goes to a notification")
            ReminderNotifier(this).postPlain(request.notificationId, request.title, timeNow(), Importance.NORMAL)
            return START_NOT_STICKY
        }
        if (request.delayMillis > 0) log.add("Scheduled: pop-up in ${request.delayMillis / MILLIS_PER_SECOND} s")
        pending = scope.launch {
            delay(request.delayMillis)
            present(request)
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(request: PopupRequest): Boolean = try {
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException / ForegroundServiceTypeNotAllowedException both extend it.
        log.add("Could not enter the foreground (${e.javaClass.simpleName}); posting a full-screen notification")
        ReminderNotifier(this).postFullScreen(request.notificationId, request.title, timeNow(), request.level)
        false
    }

    private fun present(request: PopupRequest) {
        val inputs = readPresentationInputs(this)
        val decision = decidePresentation(inputs)
        log.add("Decision: ${decision.path} (${decision.reason}) from $inputs")
        val notifier = ReminderNotifier(this)
        val timeText = timeNow()
        when (decision.path) {
            PresentationPath.OVERLAY -> showOverlay(request, timeText)
            PresentationPath.FULL_SCREEN_INTENT -> {
                val posted = notifier.postFullScreen(request.notificationId, request.title, timeText, request.level)
                log.add(if (posted) "Path B: full-screen notification posted" else "Path B: posting refused")
                stopSelf()
            }
            PresentationPath.NOTIFICATION_ONLY -> {
                val posted = notifier.postPlain(request.notificationId, request.title, timeText)
                log.add(if (posted) "Fallback: plain notification posted" else "Fallback: posting refused")
                stopSelf()
            }
            PresentationPath.NONE -> {
                log.add("Nothing shown: notifications are off and no overlay is possible")
                stopSelf()
            }
        }
    }

    private fun showOverlay(request: PopupRequest, timeText: String) {
        val result = overlay.show(onDismiss = ::onOverlayDismissed) { dismiss ->
            ReminderPopup(
                title = request.title,
                timeText = timeText,
                reduceMotion = isReducedMotion(),
                onAction = { action -> dismiss(OverlayDismissReason.valueOf(action.name)) },
                onTapIgnored = { log.add("Path A: tap ignored (tap guard)") },
            )
        }
        when (result) {
            OverlayShowResult.Shown -> {
                log.add("Path A: overlay shown")
                // Until Prompt 8 adds the ringing pop-up, the ALARM channel's notification provides the sound.
                if (request.level == Importance.ALARM) {
                    ReminderNotifier(this).postPlain(request.notificationId, request.title, timeText, Importance.ALARM)
                }
            }
            OverlayShowResult.AlreadyShowing -> log.add("Path A: overlay already showing, not stacked")
            is OverlayShowResult.Failed -> {
                log.add("Path A failed (${result.cause}), falling back to a notification")
                ReminderNotifier(this).postPlain(request.notificationId, request.title, timeText)
                stopSelf()
            }
        }
    }

    private fun onOverlayDismissed(reason: OverlayDismissReason) {
        log.add("Path A: overlay dismissed with $reason")
        stopSelf()
    }

    private fun serviceNotification() = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(getString(R.string.popup_service_notification))
        .setOngoing(true)
        .build()

    private fun timeNow(): String = DateFormat.getTimeFormat(this).format(Date())

    override fun onDestroy() {
        overlay.remove()
        scope.cancel()
        log.add("Pop-up service stopped")
        super.onDestroy()
    }

    /** What to show, read from the start intent. */
    private data class PopupRequest(val title: String, val notificationId: Int, val level: Importance, val delayMillis: Long) {
        companion object {
            fun from(intent: Intent?, defaultTitle: String) = PopupRequest(
                title = intent?.getStringExtra(EXTRA_TITLE) ?: defaultTitle,
                notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, TEST_NOTIFICATION_ID) ?: TEST_NOTIFICATION_ID,
                level = intent?.getStringExtra(EXTRA_LEVEL)?.let { name -> Importance.entries.firstOrNull { it.name == name } }
                    ?: Importance.FULL_SCREEN,
                delayMillis = intent?.getLongExtra(EXTRA_DELAY_MILLIS, 0L) ?: 0L,
            )
        }
    }

    public companion object {
        private const val EXTRA_DELAY_MILLIS = "delay_millis"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_LEVEL = "level"
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SERVICE_NOTIFICATION_ID = 9_001
        private const val TEST_NOTIFICATION_ID = 9_002

        /**
         * Starts the pop-up. With no [title] it is the debug test pop-up.
         * Throws ForegroundServiceStartNotAllowedException if Android refuses a background start.
         */
        public fun start(
            context: Context,
            delayMillis: Long,
            title: String? = null,
            notificationId: Int = TEST_NOTIFICATION_ID,
            level: Importance = Importance.FULL_SCREEN,
        ) {
            val intent = Intent(context, ReminderPopupService::class.java)
                .putExtra(EXTRA_DELAY_MILLIS, delayMillis)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_LEVEL, level.name)
            title?.let { intent.putExtra(EXTRA_TITLE, it) }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
