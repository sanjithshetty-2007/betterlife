package com.sanjith.betterlife.feature.account

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.sync.AuthState
import com.sanjith.betterlife.domain.sync.SyncError
import com.sanjith.betterlife.domain.sync.SyncIndicator
import com.sanjith.betterlife.domain.sync.SyncStatusRules
import com.sanjith.betterlife.sync.SyncStatus
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

/** The live sync state for the indicator and the Account screen. */
public data class SyncUi(val indicator: SyncIndicator, val pending: Int, val status: SyncStatus)

@Composable
internal fun rememberSyncUi(): SyncUi {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val configured = container.supabase != null
    val auth by container.accountRepository.state.collectAsStateWithLifecycle()
    val pending by container.database.syncDao().observePendingCount().collectAsStateWithLifecycle(0)
    val status by container.syncStatusStore.status.collectAsStateWithLifecycle(SyncStatus())
    val signedIn = auth is AuthState.SignedIn || (auth as? AuthState.Failed)?.signedInAs != null
    return SyncUi(SyncStatusRules.indicator(configured, signedIn, pending, status.lastError), pending, status)
}

/** The small sync line on Home (Today in Prompt 16): icon + words, tap for details. Never blocks anything. */
@Composable
public fun SyncIndicatorLine(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ui = rememberSyncUi()
    if (ui.indicator == SyncIndicator.OFF) return
    val (icon, color) = when (ui.indicator) {
        SyncIndicator.SYNCED -> R.drawable.ic_check_circle to BetterLifeColors.StatusGood
        SyncIndicator.PENDING -> R.drawable.ic_refresh to MaterialTheme.colorScheme.onSurfaceVariant
        SyncIndicator.OFFLINE, SyncIndicator.SERVER_PAUSED, SyncIndicator.SIGNED_OUT -> R.drawable.ic_pause_circle to MaterialTheme.colorScheme.onSurfaceVariant
        SyncIndicator.ERROR, SyncIndicator.OFF -> R.drawable.ic_error to BetterLifeColors.StatusWarn
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Dimens.smallIconSize))
        Text(indicatorText(ui), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun indicatorText(ui: SyncUi): String = when (ui.indicator) {
    SyncIndicator.SYNCED -> ui.status.lastSuccessAt?.let { stringResource(R.string.sync_indicator_synced_at, relative(it)) }
        ?: stringResource(R.string.sync_indicator_synced)
    SyncIndicator.PENDING -> pluralStringResource(R.plurals.sync_indicator_pending, ui.pending, ui.pending)
    SyncIndicator.OFFLINE -> stringResource(R.string.sync_indicator_offline)
    SyncIndicator.SIGNED_OUT -> stringResource(R.string.sync_indicator_signed_out)
    SyncIndicator.SERVER_PAUSED -> stringResource(R.string.sync_indicator_paused)
    SyncIndicator.ERROR, SyncIndicator.OFF -> stringResource(R.string.sync_indicator_error)
}

/** Account & sync, signed in: last sync, pending, Sync now, the last error in plain words, Delete my cloud data. */
@Composable
internal fun SyncSection(onDeleted: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val ui = rememberSyncUi()
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<Int?>(null) }
    var step by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.sync_section), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            ui.status.lastSuccessAt?.let { stringResource(R.string.sync_last, relative(it)) } ?: stringResource(R.string.sync_never),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(pluralStringResource(R.plurals.sync_pending, ui.pending, ui.pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ui.status.lastError?.let { Text(stringResource(errorText(it)), style = MaterialTheme.typography.bodyMedium, color = BetterLifeColors.StatusWarn) }
        Button(
            onClick = { container.syncScheduler.requestSoon(0); note = R.string.sync_requested },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
        ) { Text(stringResource(R.string.sync_now)) }
        note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { step = 1 }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
            Text(stringResource(R.string.sync_delete_cloud), color = MaterialTheme.colorScheme.error)
        }
    }
    // Asks twice (context.md 11a), then deletes only the server rows and signs out.
    when (step) {
        1 -> ConfirmDialog(R.string.sync_delete_title_1, R.string.sync_delete_body_1, R.string.sync_delete_continue, MaterialTheme.colorScheme.onSurface,
            onYes = { step = 2 }, onNo = { step = 0 })
        2 -> ConfirmDialog(R.string.sync_delete_title_2, R.string.sync_delete_body_2, R.string.sync_delete_confirm, MaterialTheme.colorScheme.error,
            onYes = {
                step = 0
                scope.launch {
                    note = try {
                        container.syncEngine?.deleteCloudData()
                        container.accountRepository.signOut()
                        onDeleted()
                        R.string.sync_deleted
                    } catch (e: com.sanjith.betterlife.sync.SyncFailure) {
                        R.string.sync_delete_failed
                    } catch (e: IOException) {
                        R.string.sync_delete_failed
                    }
                }
            },
            onNo = { step = 0 })
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, yes: Int, yesColor: Color, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(yes), color = yesColor) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun errorText(error: SyncError): Int = when (error) {
    SyncError.OFFLINE -> R.string.sync_error_offline
    SyncError.SIGNED_OUT -> R.string.sync_error_signed_out
    SyncError.SESSION_EXPIRED -> R.string.sync_error_session
    SyncError.SERVER_PAUSED -> R.string.sync_error_paused
    SyncError.PARTLY_FAILED -> R.string.sync_error_partly
    SyncError.UNKNOWN -> R.string.sync_error_unknown
}

@Composable
private fun relative(at: Instant): String {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    return DateUtils.getRelativeTimeSpanString(at.toEpochMilli(), container.realClock.now().toEpochMilli(), DateUtils.MINUTE_IN_MILLIS).toString()
}
