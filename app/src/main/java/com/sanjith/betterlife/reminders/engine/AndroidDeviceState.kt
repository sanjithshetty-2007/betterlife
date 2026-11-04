package com.sanjith.betterlife.reminders.engine

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sanjith.betterlife.domain.delivery.PresentationInputs

/** Reads the phone state the delivery pipeline needs, at the moment of firing. */
public class AndroidDeviceState(private val context: Context) : DeviceStateReader {

    override fun presentationInputs(): PresentationInputs = readPresentationInputs(context)

    /** In a call, or one is ringing, read from AudioManager: no phone permission needed (context.md 5.1). */
    override fun callActive(): Boolean = context.getSystemService(AudioManager::class.java).mode in CALL_MODES

    private companion object {
        val CALL_MODES = setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)
    }
}

/** Screen, lock and permission state; re-read on every use because each can change at any time. */
public fun readPresentationInputs(context: Context): PresentationInputs = PresentationInputs(
    isInteractive = context.getSystemService(PowerManager::class.java).isInteractive,
    isKeyguardLocked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
    canDrawOverlays = Settings.canDrawOverlays(context),
    canUseFullScreenIntent = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
    notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
)
