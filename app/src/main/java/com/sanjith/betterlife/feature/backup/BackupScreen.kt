package com.sanjith.betterlife.feature.backup

import android.app.Activity
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.backup.BackupStatus
import com.sanjith.betterlife.data.backup.StagedBackup
import com.sanjith.betterlife.domain.backup.BackupRules
import com.sanjith.betterlife.domain.backup.RestoreProblem
import com.sanjith.betterlife.domain.time.zonedNow
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import java.time.Instant
import java.util.Date
import kotlin.system.exitProcess

/**
 * Manual backup and restore (Prompt 13, design "backup"). Auto-backup, encryption and CSV arrive in
 * Prompt 29. The owner picks where the file goes (Storage Access Framework), so no storage permission.
 */
@Composable
public fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val staged by viewModel.staged.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restored by viewModel.restored.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri ->
        uri?.let { viewModel.export(context.contentResolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.stage(context.contentResolver, it) }
    }

    val messageText = message?.let { messageText(it) }
    LaunchedEffect(message) {
        messageText?.let { snackbar.showSnackbar(it) }
        viewModel.message.value = null
    }
    // Room was closed under the app: start fresh so it reopens the restored file and sets every alarm.
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        val activity = context as? Activity
        activity?.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        exitProcess(0)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            StatusCard(
                status = status,
                busy = busy,
                onExport = { exportLauncher.launch(BackupRules.fileName(container.realClock.zonedNow())) },
                onImport = { importLauncher.launch(arrayOf(ZIP_TYPE, ANY_TYPE)) },
            )
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            staged?.let { PreviewCard(it, busy, onRestore = { confirm = true }, onCancel = viewModel::cancelPreview) }
        }
    }
    if (confirm) {
        staged?.let { backup ->
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.backup_confirm_title)) },
                text = { Text(stringResource(R.string.backup_confirm_body, dateTime(backup.manifest.createdAt))) },
                confirmButton = {
                    TextButton(onClick = { confirm = false; viewModel.restore() }) {
                        Text(stringResource(R.string.backup_confirm_yes), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

private const val ZIP_TYPE = "application/zip"
private const val ANY_TYPE = "*/*"

@Composable
private fun StatusCard(status: BackupStatus, busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    val failedLatest = status.lastFailureAt != null && (status.lastSuccessAt == null || status.lastFailureAt > status.lastSuccessAt)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                val ok = status.lastSuccessAt != null && !failedLatest
                Icon(
                    painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error),
                    contentDescription = null,
                    tint = if (ok) BetterLifeColors.StatusGood else BetterLifeColors.StatusWarn,
                    modifier = Modifier.size(Dimens.iconSize),
                )
                Column {
                    Text(
                        status.lastSuccessAt?.let { stringResource(R.string.backup_last, dateTime(Instant.ofEpochMilli(it))) } ?: stringResource(R.string.backup_never),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (failedLatest) {
                        Text(
                            stringResource(R.string.backup_last_failed, dateTime(Instant.ofEpochMilli(status.lastFailureAt!!))),
                            style = MaterialTheme.typography.bodySmall,
                            color = BetterLifeColors.StatusBad,
                        )
                    }
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.backup_now))
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.backup_restore))
                }
            }
        }
    }
}

/** Counts from the backup's manifest, the safety-copy note and the REPLACES warning (context.md 11). */
@Composable
private fun PreviewCard(backup: StagedBackup, busy: Boolean, onRestore: () -> Unit, onCancel: () -> Unit) {
    val counts = backup.manifest.counts
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.backup_preview).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.backup_from, dateTime(backup.manifest.createdAt)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    R.string.backup_count_tasks to "tasks",
                    R.string.backup_count_events to "events",
                    R.string.backup_count_reminders to "reminders",
                    R.string.backup_count_remind_me to "standalone_reminders",
                    R.string.backup_count_projects to "projects",
                ).forEach { (label, table) ->
                    counts[table]?.let { n -> SuggestionChip(onClick = {}, label = { Text(stringResource(label, n)) }) }
                }
            }
            Text(stringResource(R.string.backup_safety_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Button(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
            ) { Text(stringResource(R.string.backup_restore_this)) }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) {
                Text(stringResource(R.string.backup_cancel_preview))
            }
        }
    }
}

@Composable
private fun messageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Saved -> stringResource(
        R.string.backup_saved,
        message.manifest.counts["tasks"] ?: 0,
        message.manifest.counts["events"] ?: 0,
        message.manifest.counts["reminders"] ?: 0,
    )
    BackupMessage.WriteFailed -> stringResource(R.string.backup_failed)
    is BackupMessage.CannotRestore -> stringResource(
        when (message.problem) {
            RestoreProblem.NOT_A_BACKUP -> R.string.backup_problem_not_a_backup
            RestoreProblem.NEWER_FORMAT -> R.string.backup_problem_newer_format
            RestoreProblem.NEWER_SCHEMA -> R.string.backup_problem_newer_schema
            RestoreProblem.CHECKSUM_MISMATCH -> R.string.backup_problem_checksum
            RestoreProblem.DATABASE_UNREADABLE -> R.string.backup_problem_unreadable
            null -> R.string.backup_problem_io
        },
    )
}

@Composable
private fun dateTime(at: Instant): String {
    val context = LocalContext.current
    val date = Date.from(at)
    return "${DateFormat.getMediumDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
}
