package com.sanjith.betterlife.reminders.popup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.settings.AppSettings

/**
 * The ring of a pop-up or alarm (owner's change, 2026-11-08): the owner's alarm sound in a loop and
 * strong repeating vibration until answered. It rings on the alarm stream, raised to full volume
 * while ringing and put back afterwards, so silent mode does not hide it. With "ring on silent" off
 * a pop-up (not an ALARM) follows the ringer instead; the vibration stays.
 */
public class AndroidSoundPlayer(
    private val context: Context,
    private val settings: suspend () -> AppSettings,
) : SoundPlayer {

    private var ringtone: Ringtone? = null
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var volumeBefore: Int? = null

    override suspend fun play(level: Importance, volume: Float, loop: Boolean) {
        val s = settings()
        stop()
        val onAlarmStream = level == Importance.ALARM || s.popupsRingOnSilent
        vibrate()
        val tone = RingtoneManager.getRingtone(context, soundUri(s)) ?: run {
            Log.w(TAG, "No sound to play for $level")
            return
        }
        if (onAlarmStream) raiseAlarmVolume()
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(if (onAlarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        tone.volume = volume
        tone.isLooping = loop
        tone.play()
        ringtone = tone
    }

    override fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        restoreAlarmVolume()
    }

    /** The owner's sound if it can still be opened (a file may have been deleted), else the phone's alarm sound. */
    private fun soundUri(s: AppSettings): Uri {
        val chosen = s.alarmSoundUri?.let(Uri::parse)
        if (chosen != null && canOpen(chosen)) return chosen
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private fun canOpen(uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** Alarm usage so the vibration is not dropped when the phone is on silent or the screen is off. */
    private fun vibrate() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, VIBRATION_REPEAT_FROM)
        v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    private fun raiseAlarmVolume() {
        runCatching {
            if (volumeBefore == null) volumeBefore = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }.onFailure { Log.w(TAG, "Could not raise the alarm volume", it) }
    }

    private fun restoreAlarmVolume() {
        val before = volumeBefore ?: return
        volumeBefore = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, before, 0) }
    }

    private companion object {
        const val TAG = "PopupSound"

        // One long full-strength buzz, a short pause, repeated from the buzz.
        val VIBRATION_TIMINGS = longArrayOf(0, 1_200, 400)
        val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0)
        const val VIBRATION_REPEAT_FROM = 1
    }
}
