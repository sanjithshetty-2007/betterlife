package com.sanjith.betterlife.reminders.silent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.core.DebugLog
import com.sanjith.betterlife.domain.silent.SilentReminder
import com.sanjith.betterlife.domain.silent.SilentRules
import com.sanjith.betterlife.domain.silent.VibrationPattern
import com.sanjith.betterlife.domain.silent.Waveform
import com.sanjith.betterlife.domain.time.Clock
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Plays a silent reminder's vibration. Alarm usage, so it is felt on silent and with the screen off;
 * no sound, no pop-up, no notification. The vibration ends by itself.
 */
public class SilentBuzzer(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    public fun play(waveform: Waveform) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || waveform.timings.isEmpty()) return
        v.vibrate(VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, NO_REPEAT), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    /** "Try it" in the editor: two seconds of [pattern]. */
    public fun preview(pattern: VibrationPattern) {
        play(SilentRules.waveform(pattern, SilentRules.PREVIEW_MILLIS))
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}

/**
 * The silent reminders' ONE alarm (owner's feature, 2026-11-21), at the next buzz of any enabled
 * reminder; setting it again replaces it. Re-planned on boot, app update, time and time-zone changes,
 * app launch and every change to a silent reminder. When it goes off it vibrates (unless a phone call
 * is on) and plans the next one; it never repeats a buzz. [clock] is the real clock, like every alarm.
 */
public class SilentReminders(
    private val context: Context,
    private val reminders: suspend () -> List<SilentReminder>,
    private val clock: Clock,
    private val callActive: () -> Boolean,
    private val buzzer: SilentBuzzer,
    private val log: DebugLog,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    public suspend fun replan() {
        plan(after = clock.now())
    }

    /** The alarm for [at] went off: buzz for every reminder due then, and plan the next buzz after it. */
    public suspend fun fire(at: Instant) {
        val all = reminders()
        val due = SilentRules.dueAt(at, clock.zone, all)
        val waveform = SilentRules.waveformFor(due)
        when {
            waveform == null -> Unit
            callActive() -> log.add("Silent reminder: skipped during a call")
            else -> {
                buzzer.play(waveform)
                log.add("Silent reminder: buzzed (${due.size})")
            }
        }
        plan(after = maxOf(at, clock.now()))
    }

    private suspend fun plan(after: Instant) {
        val next = SilentRules.nextBuzz(after, clock.zone, reminders())
        if (next == null) {
            alarmManager.cancel(operation(null))
            return
        }
        val millis = next.toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation(next))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation(next))
        }
    }

    /** Extras are not part of a PendingIntent's identity, so every [at] replaces the same alarm. */
    private fun operation(at: Instant?): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SilentReminderReceiver::class.java).setAction(ACTION).apply { at?.let { putExtra(EXTRA_AT, it.toEpochMilli()) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val ACTION = "com.sanjith.betterlife.action.SILENT_REMINDER"
        const val EXTRA_AT = "at"
        const val REQUEST_CODE = 9_500
    }
}

/** The silent alarm's target. Not exported: only our own PendingIntent reaches it. */
public class SilentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SilentReminders.ACTION) return
        val at = intent.getLongExtra(SilentReminders.EXTRA_AT, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli)
        val container = (context.applicationContext as BetterLifeApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (at != null) container.silentReminders.fire(at) else container.silentReminders.replan()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val NONE = -1L
    }
}
