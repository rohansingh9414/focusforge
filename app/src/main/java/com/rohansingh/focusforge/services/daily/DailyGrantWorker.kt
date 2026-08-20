package com.rohansingh.focusforge.services.daily

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.DailyGrantManager
import com.rohansingh.focusforge.domain.models.DailyGrantResult
import com.rohansingh.focusforge.services.notifications.FocusForgeNotificationManager

/**
 * Background WorkManager worker responsible for triggering the daily grant domain operation.
 * Defined in ROADMAP.md Phase 5 and ARCHITECTURE.md.
 */
class DailyGrantWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyGrantWorker"
        const val WORK_NAME = "FocusForgeDailyGrantWork"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "DailyGrantWorker started execution")

        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val walletRepository = WalletRepository(database.walletDao())
            val dailyGrantManager = DailyGrantManager(walletRepository)

            val grantResult = dailyGrantManager.applyDailyGrant()

            grantResult.fold(
                onSuccess = { result ->
                    when (result) {
                        is DailyGrantResult.Applied -> {
                            Log.i(TAG, "Daily grant applied successfully for ${result.date}: +₹${result.rupeeGranted}, +${result.screenTimeGranted} min screen time")
                            FocusForgeNotificationManager.showDailyGrantNotification(
                                context = applicationContext,
                                rupeesGranted = result.rupeeGranted,
                                screenTimeGranted = result.screenTimeGranted
                            )
                        }
                        is DailyGrantResult.AlreadyApplied -> {
                            Log.i(TAG, "Daily grant already applied for ${result.date}. Skipping.")
                        }
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to apply daily grant", error)
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in DailyGrantWorker", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
