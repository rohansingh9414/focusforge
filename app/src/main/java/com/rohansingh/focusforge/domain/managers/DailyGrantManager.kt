package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.DailyGrantResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Domain manager executing daily grant business logic and idempotency enforcement.
 * Defined in ECONOMY.md §7 and ROADMAP.md Phase 5.
 */
class DailyGrantManager(
    private val walletRepository: WalletRepository
) {

    companion object {
        const val DAILY_GRANT_RUPEES: Double = 50.0
        const val DAILY_GRANT_SCREEN_TIME_MINUTES: Int = 60

        /**
         * Returns the current device local date in "yyyy-MM-dd" format.
         */
        fun getTodayDateString(date: Date = Date()): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return formatter.format(date)
        }
    }

    /**
     * Applies the daily grant for [targetDate] if it hasn't already been granted today.
     * Idempotent by design: checks `wallet.lastDailyGrantDate` against [targetDate].
     *
     * @param targetDate The date string (defaulting to today's local date).
     * @return [Result] containing [DailyGrantResult.Applied] if granted, or [DailyGrantResult.AlreadyApplied] if skipped.
     */
    suspend fun applyDailyGrant(targetDate: String = getTodayDateString()): Result<DailyGrantResult> {
        return try {
            walletRepository.ensureWalletInitialized()
            val currentWallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)

            if (currentWallet.lastDailyGrantDate == targetDate) {
                // Already granted for this calendar date; do nothing
                Result.success(DailyGrantResult.AlreadyApplied(date = targetDate))
            } else {
                // Apply daily grant: +₹50 rupee balance, +60 screen-time minutes
                val updatedWallet = currentWallet.copy(
                    rupeeBalance = currentWallet.rupeeBalance + DAILY_GRANT_RUPEES,
                    screenTimeMinutes = currentWallet.screenTimeMinutes + DAILY_GRANT_SCREEN_TIME_MINUTES,
                    lastDailyGrantDate = targetDate
                )
                walletRepository.updateWallet(updatedWallet)

                Result.success(
                    DailyGrantResult.Applied(
                        rupeeGranted = DAILY_GRANT_RUPEES,
                        screenTimeGranted = DAILY_GRANT_SCREEN_TIME_MINUTES,
                        date = targetDate
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
