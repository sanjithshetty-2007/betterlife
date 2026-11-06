package com.sanjith.betterlife.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.domain.sync.SyncError
import java.util.concurrent.TimeUnit

/**
 * Runs one sync (Prompt S2). WorkManager waits for a network, retries with exponential backoff and
 * runs off the main thread; reminders never wait for it.
 */
public class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        val engine = container.syncEngine ?: return Result.success()
        val result = engine.sync()
        container.debugLog.add("Sync: pushed ${result.pushed}, pulled ${result.pulled}, rejected ${result.rejected}, ${result.error ?: "ok"}")
        return when (result.error) {
            // Worth trying again later; WorkManager backs off.
            SyncError.OFFLINE, SyncError.SERVER_PAUSED, SyncError.UNKNOWN -> Result.retry()
            // Nothing a retry fixes: signed out, expired sign-in, or rows the server refused (reported).
            else -> Result.success()
        }
    }
}

/** Starts sync runs: soon after a change (debounced), on app open, on "Sync now", and every few hours. */
public class SyncScheduler(private val context: Context) {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * One run after [delaySeconds]. A run already in progress is never cancelled (that would waste the
     * upload it was doing): the new run is queued after it, so changes made meanwhile still go up.
     * Bursts of edits are debounced before this is called (AppContainer.startSync).
     */
    public fun requestSoon(delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** The periodic pull (also keeps a free Supabase project from pausing while the phone is used). */
    public fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    public fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    public companion object {
        public const val DEBOUNCE_SECONDS: Long = 5
        private const val BACKOFF_SECONDS = 30L
        private const val PERIODIC_HOURS = 6L
        private const val ONE_TIME = "sync-soon"
        private const val PERIODIC = "sync-periodic"
    }
}
