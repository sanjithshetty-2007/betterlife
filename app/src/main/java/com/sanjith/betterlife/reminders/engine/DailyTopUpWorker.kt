package com.sanjith.betterlife.reminders.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanjith.betterlife.BetterLifeApp
import java.util.concurrent.TimeUnit

/**
 * Once a day, top up the 30-day alarm window. Without it, a reminder 31+ days away would only get its
 * alarm when the app is next opened. OriginOS may delay this job; app launch also reschedules.
 */
public class DailyTopUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BetterLifeApp).container
        container.reminderEngine.rescheduleAll(RescheduleTrigger.DAILY)
        container.debugLog.add("Reminder engine rescheduled (DAILY)")
        return Result.success()
    }

    public companion object {
        private const val UNIQUE_NAME = "reminder-daily-top-up"
        private const val INTERVAL_HOURS = 24L

        /** Safe to call on every launch: KEEP leaves an existing schedule untouched. */
        public fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyTopUpWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build(),
            )
        }
    }
}
