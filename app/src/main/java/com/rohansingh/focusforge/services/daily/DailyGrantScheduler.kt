package com.rohansingh.focusforge.services.daily

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduler responsible for enqueuing unique WorkManager work for daily automation.
 * Defined in ROADMAP.md Phase 5 and ARCHITECTURE.md.
 */
object DailyGrantScheduler {

    private const val TAG = "DailyGrantScheduler"

    /**
     * Schedules unique periodic daily work to execute the daily grant every 24 hours.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] to prevent duplicate periodic jobs across app starts.
     */
    fun scheduleDailyGrant(context: Context) {
        val initialDelayMillis = calculateInitialDelayUntilNextMidnight()

        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyGrantWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyGrantWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )

        Log.d(TAG, "Scheduled unique periodic daily grant work with initial delay: ${initialDelayMillis / 1000}s")
    }

    /**
     * Helper to trigger a one-time immediate execution of the daily grant worker (for testing / manual runs).
     */
    fun runImmediateDailyGrant(context: Context) {
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DailyGrantWorker>()
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DailyGrantWorker.WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
        Log.d(TAG, "Triggered immediate one-time daily grant work")
    }

    private fun calculateInitialDelayUntilNextMidnight(): Long {
        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return nextMidnight.timeInMillis - now.timeInMillis
    }
}
