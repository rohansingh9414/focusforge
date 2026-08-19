package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.domain.gamification.GamificationConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Domain manager executing goal business logic, streak bonus calculation,
 * daily-cap enforcement, XP award, and atomic database transaction.
 * Defined in ECONOMY.md §4, §10 and ROADMAP.md Phase 3 & Phase 8.
 */
class GoalManager(
    private val goalRepository: GoalRepository,
    private val calendarProvider: () -> Calendar = { Calendar.getInstance() }
) {

    /**
     * Completes a goal for a given amount:
     * 1. Evaluates existing streak and calculates streak bonus multiplier.
     * 2. Calculates potential credits before dailyCap.
     * 3. Enforces dailyCap as the absolute credit ceiling.
     * 4. Calculates XP = round(final creditsEarned * 10).
     * 5. Determines new streak (starts at 1, increments on consecutive days, preserves on same day).
     * 6. Atomically commits GoalLog, XpLog, GoalStreak, and Wallet update.
     */
    suspend fun completeGoal(goal: GoalTemplate, amount: Double): Result<Double> {
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Completion amount must be greater than 0"))
        }

        val cal = calendarProvider()
        val todayDateStr = getTodayDateString(cal)
        val yesterdayDateStr = getYesterdayDateString(cal)

        // 1. Fetch existing streak for goal
        val existingStreak = goalRepository.getStreakForGoal(goal.id)

        // Determine existing streak continuity for bonus calculation
        val streakCountForBonus = when {
            existingStreak == null || existingStreak.currentStreak <= 0 -> 0
            existingStreak.lastCompletedDate == todayDateStr -> existingStreak.currentStreak
            existingStreak.lastCompletedDate == yesterdayDateStr -> existingStreak.currentStreak
            else -> 0 // Broken streak
        }

        // 2. Calculate streak bonus multiplier (+2% per day, max +20%)
        val streakBonus = min(
            streakCountForBonus * GamificationConfig.STREAK_BONUS_PER_DAY,
            GamificationConfig.MAX_STREAK_BONUS
        )
        val streakMultiplier = 1.0 + streakBonus

        // 3. Potential credits before dailyCap
        val effectiveRate = goal.creditRate * streakMultiplier
        val potentialCredits = amount * effectiveRate

        // 4. Enforce dailyCap
        val actualCreditsEarned: Double = if (goal.dailyCap > 0.0) {
            val (startOfDay, endOfDay) = getTodayTimeBounds(cal)
            val alreadyEarnedToday = goalRepository.getCreditsEarnedToday(goal.id, startOfDay, endOfDay)
            val remainingCap = max(0.0, goal.dailyCap - alreadyEarnedToday)
            min(potentialCredits, remainingCap)
        } else {
            potentialCredits
        }

        // 5. Calculate XP based on FINAL creditsEarned
        val actualXpEarned: Long = if (actualCreditsEarned > 0.0) {
            Math.round(actualCreditsEarned * GamificationConfig.XP_PER_CREDIT)
        } else {
            0L
        }

        // 6. Calculate updated streak
        val newCurrentStreak = when {
            existingStreak?.lastCompletedDate == todayDateStr -> existingStreak.currentStreak
            existingStreak?.lastCompletedDate == yesterdayDateStr -> existingStreak.currentStreak + 1
            else -> 1 // First day or reset after missed day
        }
        val newLongestStreak = max(existingStreak?.longestStreak ?: 0, newCurrentStreak)
        val updatedStreak = GoalStreak(
            goalTemplateId = goal.id,
            currentStreak = newCurrentStreak,
            longestStreak = newLongestStreak,
            lastCompletedDate = todayDateStr
        )

        // 7. GoalLog
        val goalLog = GoalLog(
            goalTemplateId = goal.id,
            amountCompleted = amount,
            creditsEarned = actualCreditsEarned,
            completedAt = cal.timeInMillis
        )

        // 8. Atomic transaction write
        goalRepository.recordGoalCompletionTransaction(
            goalLog = goalLog,
            xpEarned = actualXpEarned,
            updatedStreak = updatedStreak,
            creditsEarned = actualCreditsEarned
        )

        return Result.success(actualCreditsEarned)
    }

    private fun getTodayDateString(calendar: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun getYesterdayDateString(calendar: Calendar): String {
        val yesterdayCal = (calendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(yesterdayCal.time)
    }

    private fun getTodayTimeBounds(calendar: Calendar): Pair<Long, Long> {
        val cal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis - 1
        return Pair(startOfDay, endOfDay)
    }
}
