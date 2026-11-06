package com.sanjith.betterlife.feature.backup

import android.content.ContentResolver
import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.data.backup.BackupEngine
import com.sanjith.betterlife.data.backup.BackupException
import com.sanjith.betterlife.data.backup.BackupStatus
import com.sanjith.betterlife.data.backup.BackupStatusStore
import com.sanjith.betterlife.data.backup.StagedBackup
import com.sanjith.betterlife.domain.backup.BackupManifest
import com.sanjith.betterlife.domain.backup.RestoreProblem
import com.sanjith.betterlife.domain.time.Clock
import com.sanjith.betterlife.domain.time.millis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** One-off results the screen shows as a snackbar. Null problem with a manifest = saved. */
public sealed interface BackupMessage {
    public data class Saved(val manifest: BackupManifest) : BackupMessage
    public data object WriteFailed : BackupMessage
    public data class CannotRestore(val problem: RestoreProblem?) : BackupMessage
}

/** Manual backup and restore (Prompt 13). All file work runs in [BackupEngine] off the main thread. */
public class BackupViewModel(
    private val engine: BackupEngine,
    private val statusStore: BackupStatusStore,
    private val beforeRestore: suspend () -> Unit,
    private val realClock: Clock,
) : ViewModel() {

    public val status: StateFlow<BackupStatus> =
        statusStore.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BackupStatus())

    public val busy: MutableStateFlow<Boolean> = MutableStateFlow(false)
    public val staged: MutableStateFlow<StagedBackup?> = MutableStateFlow(null)
    public val message: MutableStateFlow<BackupMessage?> = MutableStateFlow(null)

    /** Becomes true after a restore; the screen then restarts the app. */
    public val restored: MutableStateFlow<Boolean> = MutableStateFlow(false)

    public fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                val stream = resolver.openOutputStream(uri, WRITE_TRUNCATE) ?: throw IOException("No output stream")
                val manifest = stream.use { engine.export(it) }
                statusStore.success(realClock.millis())
                BackupMessage.Saved(manifest)
            } catch (e: IOException) {
                Log.w(TAG, "Backup write failed", e)
                statusStore.failure(realClock.millis())
                BackupMessage.WriteFailed
            } catch (e: SecurityException) {
                Log.w(TAG, "Backup folder refused", e)
                statusStore.failure(realClock.millis())
                BackupMessage.WriteFailed
            } catch (e: SQLException) {
                Log.w(TAG, "Database copy failed", e)
                statusStore.failure(realClock.millis())
                BackupMessage.WriteFailed
            }
            busy.value = false
        }
    }

    public fun stage(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            try {
                val stream = resolver.openInputStream(uri) ?: throw IOException("No input stream")
                staged.value = stream.use { engine.stage(it) }
            } catch (e: BackupException) {
                message.value = BackupMessage.CannotRestore(e.problem)
            } catch (e: IOException) {
                Log.w(TAG, "Backup read failed", e)
                message.value = BackupMessage.CannotRestore(null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Backup file refused", e)
                message.value = BackupMessage.CannotRestore(null)
            }
            busy.value = false
        }
    }

    public fun cancelPreview() {
        staged.value?.databaseFile?.delete()
        staged.value = null
    }

    public fun restore() {
        val backup = staged.value ?: return
        viewModelScope.launch {
            busy.value = true
            try {
                beforeRestore()
                engine.restore(backup)
                restored.value = true
            } catch (e: IOException) {
                // The safety copy or the file copy failed; the app restarts anyway so alarms are set again.
                Log.w(TAG, "Restore failed", e)
                message.value = BackupMessage.CannotRestore(null)
                restored.value = true
            } catch (e: SQLException) {
                // Failed before the live database was touched (making the safety copy): nothing changed.
                Log.w(TAG, "Safety copy failed, nothing restored", e)
                message.value = BackupMessage.CannotRestore(null)
                busy.value = false
            }
        }
    }

    public companion object {
        private const val TAG = "Backup"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Truncate: writing over an existing file must not leave old bytes at the end. */
        private const val WRITE_TRUNCATE = "wt"

        public val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BetterLifeApp).container
                BackupViewModel(c.backupEngine, c.backupStatusStore, { c.reminderEngine.cancelAllAlarms() }, c.realClock)
            }
        }
    }
}
