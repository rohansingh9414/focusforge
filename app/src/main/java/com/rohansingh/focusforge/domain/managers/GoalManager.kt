package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Domain manager executing goal business logic, credit calculations,
 * daily-cap enforcement, and wallet credit increments.
 * Defined in ECONOMY.md §4 and ROADMAP.md Phase 3.
 */
class GoalManager(
    private val goalRepository: GoalRepository,
    private val walletRepository: WalletRepository
) {

    /**
     * Completes a goal for a given amount, enforces dailyCap, writes a GoalLog,
     * updates the user's Wallet.creditBalance, and returns the credits earned.
     */
    suspend fun completeGoal(goal: GoalTemplate, amount: Double): Result<Double> {
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Completion amount must be greater than 0"))
        }

        val potentialCredits = amount * goal.creditRate

        val actualCreditsEarned: Double = if (goal.dailyCap > 0.0) {
            val (startOfDay, endOfDay) = getTodayTimeBounds()
            val alreadyEarnedToday = goalRepository.getCreditsEarnedToday(goal.id, startOfDay, endOfDay)
            val remainingCap = max(0.0, goal.dailyCap - alreadyEarnedToday)
            min(potentialCredits, remainingCap)
        } else {
            potentialCredits
        }

        // Record goal completion log
        val goalLog = GoalLog(
            goalTemplateId = goal.id,
            amountCompleted = amount,
            creditsEarned = actualCreditsEarned,
            completedAt = System.currentTimeMillis()
        )
        goalRepository.insertGoalLog(goalLog)

        // Increment wallet credit balance if any credits were earned
        if (actualCreditsEarned > 0.0) {
            val currentWallet = walletRepository.getWalletOnce() ?: Wallet()
            val updatedWallet = currentWallet.copy(
                creditBalance = currentWallet.creditBalance + actualCreditsEarned
            )
            walletRepository.updateWallet(updatedWallet)
        }

        return Result.success(actualCreditsEarned)
    }

    private fun getTodayTimeBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis - 1
        return Pair(startOfDay, endOfDay)
    }
}
