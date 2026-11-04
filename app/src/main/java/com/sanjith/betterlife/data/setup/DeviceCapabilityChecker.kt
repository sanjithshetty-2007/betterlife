package com.sanjith.betterlife.data.setup

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sanjith.betterlife.domain.setup.DeviceChecks

/** Reads the live permission state from Android. Cheap calls, safe on the main thread, but callers use IO anyway. */
public class DeviceCapabilityChecker(private val context: Context) {

    public fun read(): DeviceChecks {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        return DeviceChecks(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarmsAllowed = alarmManager.canScheduleExactAlarms(),
            canDrawOverlays = Settings.canDrawOverlays(context),
            // Android 14 (minSdk) added this check, so no version branch is needed.
            canUseFullScreenIntent = notificationManager.canUseFullScreenIntent(),
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    /** Vivo-only rows are shown only on Vivo phones (context.md section 2). */
    public val isVivo: Boolean
        get() = Build.MANUFACTURER.equals(VIVO_MANUFACTURER, ignoreCase = true)

    private companion object {
        const val VIVO_MANUFACTURER = "vivo"
    }
}
