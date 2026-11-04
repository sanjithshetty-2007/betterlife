package com.sanjith.betterlife.feature.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sanjith.betterlife.domain.setup.SetupCheck

/** Opens the system settings page for one check, falling back to the app's own App info page. */
internal object SystemSettingsLauncher {

    private const val TAG = "SystemSettings"
    private const val PACKAGE_SCHEME = "package"

    /** Recents lock has no settings page; it is done from the recent-apps screen. */
    fun hasSettingsPage(check: SetupCheck): Boolean = check != SetupCheck.VIVO_RECENTS_LOCK

    /**
     * [context] must be the Activity: launching without FLAG_ACTIVITY_NEW_TASK keeps the settings page
     * in this app's task, so Back returns to the checklist instead of into the Settings app.
     */
    fun open(context: Context, check: SetupCheck) {
        try {
            context.startActivity(intentFor(context, check))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No screen for $check, opening App info instead", e)
            context.startActivity(appDetails(context))
        } catch (e: SecurityException) {
            Log.w(TAG, "Screen for $check refused, opening App info instead", e)
            context.startActivity(appDetails(context))
        }
    }

    private fun intentFor(context: Context, check: SetupCheck): Intent = when (check) {
        SetupCheck.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        SetupCheck.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
        SetupCheck.DRAW_OVER_APPS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))
        SetupCheck.FULL_SCREEN_INTENT ->
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
        // Vivo's battery and per-app permission switches all live under App info; the row text gives the path.
        SetupCheck.BATTERY_UNRESTRICTED,
        SetupCheck.VIVO_AUTOSTART,
        SetupCheck.VIVO_BACKGROUND_POPUPS,
        SetupCheck.VIVO_LOCK_SCREEN,
        SetupCheck.VIVO_RECENTS_LOCK,
        -> appDetails(context)
    }

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)
}
