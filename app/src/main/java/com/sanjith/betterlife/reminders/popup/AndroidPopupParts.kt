package com.sanjith.betterlife.reminders.popup

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.sanjith.betterlife.data.db.BetterLifeDatabase
import com.sanjith.betterlife.domain.model.Importance
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.domain.popup.PopupCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Plays the pop-up sound. FULL_SCREEN uses the notification sound and stream, so it respects silent
 * mode; ALARM uses the alarm sound on the alarm stream, so it rings even on silent (context.md 5.1),
 * and vibrates.
 */
public class AndroidSoundPlayer(private val context: Context) : SoundPlayer {

    private var ringtone: Ringtone? = null
    private val vibrator = context.getSystemService(Vibrator::class.java)

    override fun play(level: Importance, volume: Float, loop: Boolean) {
        stop()
        val alarm = level == Importance.ALARM
        val uri = RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
        val tone = RingtoneManager.getRingtone(context, uri) ?: run {
            Log.w(TAG, "No default sound for $level")
            return
        }
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        tone.volume = volume
        tone.isLooping = loop
        tone.play()
        ringtone = tone
        if (alarm) vibrator?.vibrate(VibrationEffect.createWaveform(ALARM_VIBRATION, VIBRATION_REPEAT_FROM))
    }

    override fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    private companion object {
        const val TAG = "PopupSound"
        val ALARM_VIBRATION = longArrayOf(0, 600, 400, 600, 1_200)
        const val VIBRATION_REPEAT_FROM = 1
    }
}

/** Watches a card's task or event in Room, so the pop-up closes or updates when it changes elsewhere. */
public class RoomItemWatcher(private val db: BetterLifeDatabase) : ItemWatcher {
    override fun watch(card: PopupCard): Flow<WatchedItem?> {
        val id = card.itemId ?: return flowOf(WatchedItem(card.title))
        return when (card.itemType) {
            ReminderItemType.TASK -> db.taskDao().observeById(id).map { task ->
                task?.takeIf { it.completedAt == null }?.let { WatchedItem(it.title) }
            }
            ReminderItemType.EVENT -> db.eventDao().observeById(id).map { event -> event?.let { WatchedItem(it.title) } }
            ReminderItemType.STANDALONE, null -> flowOf(WatchedItem(card.title))
        }
    }
}
