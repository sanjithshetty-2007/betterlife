package com.sanjith.betterlife.feature.more

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.settings.AppSettings
import com.sanjith.betterlife.feature.calm.Stepper
import com.sanjith.betterlife.feature.events.SwitchRow
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import com.sanjith.betterlife.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * The owner's alarm (2026-11-08): the sound, how long it rings, how soon it comes back, and whether
 * pop-ups ring on silent. Lives on More until Settings exists (Prompt 27).
 */
@Composable
internal fun AlarmSettingsSection(s: AppSettings) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val scope = rememberCoroutineScope()
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { container.settingsRepository.update(change) }
    }
    val current = s.alarmSoundUri?.let(Uri::parse)

    // The phone's own sound picker, with its previews.
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            update { it.copy(alarmSoundUri = picked?.toString()) }
        }
    }
    // Any audio file on the phone; the permission is kept so it still plays after a restart.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            update { it.copy(alarmSoundUri = uri.toString()) }
        }
    }

    SettingsGroup(stringResource(R.string.alarm_title)) {
        Text(
            soundName(context, current) ?: stringResource(R.string.alarm_sound_default),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(
            onClick = { pickSound.launch(pickerIntent(current, context.getString(R.string.alarm_title))) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.alarm_choose_sound)) }
        OutlinedButton(
            onClick = { pickFile.launch(arrayOf(AUDIO_FILES)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.alarm_choose_file)) }
        Stepper(
            title = stringResource(R.string.alarm_ring_for),
            detail = stringResource(R.string.alarm_ring_for_detail),
            value = stringResource(R.string.quiet_minutes, s.popupTimeoutMinutes),
            canDecrease = s.popupTimeoutMinutes > 1,
            canIncrease = s.popupTimeoutMinutes < AppSettings.MAX_RING_MINUTES,
            sidePadding = 0.dp,
        ) { delta -> update { it.copy(popupTimeoutMinutes = (it.popupTimeoutMinutes + delta).coerceIn(1, AppSettings.MAX_RING_MINUTES)) } }
        Stepper(
            title = stringResource(R.string.alarm_snooze_for),
            detail = stringResource(R.string.alarm_snooze_for_detail),
            value = stringResource(R.string.quiet_minutes, s.autoSnoozeMinutes),
            canDecrease = s.autoSnoozeMinutes > 1,
            canIncrease = s.autoSnoozeMinutes < AppSettings.MAX_AUTO_SNOOZE_MINUTES,
            sidePadding = 0.dp,
        ) { delta -> update { it.copy(autoSnoozeMinutes = (it.autoSnoozeMinutes + delta).coerceIn(1, AppSettings.MAX_AUTO_SNOOZE_MINUTES)) } }
        SwitchRow(stringResource(R.string.alarm_ring_on_silent), s.popupsRingOnSilent) { on -> update { it.copy(popupsRingOnSilent = on) } }
        SwitchRow(stringResource(R.string.alarm_normal_rings), s.normalRemindersRing) { on ->
            scope.launch {
                container.settingsRepository.update { it.copy(normalRemindersRing = on) }
                // The alarm kind depends on it.
                container.reminderEngine.rescheduleAll(RescheduleTrigger.MANUAL)
            }
        }
    }
}

private const val AUDIO_FILES = "audio/*"

private fun pickerIntent(current: Uri?, title: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

@Composable
private fun soundName(context: Context, uri: Uri?): String? = remember(uri) {
    uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
}
