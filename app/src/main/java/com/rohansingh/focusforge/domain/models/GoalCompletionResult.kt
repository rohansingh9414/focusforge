package com.rohansingh.focusforge.domain.models

/**
 * Domain result representing the outcome of a goal completion, including credits, XP, and streak.
 */
data class GoalCompletionResult(
    val creditsEarned: Double,
    val xpEarned: Long,
    val currentStreak: Int,
    val streakBonusPercent: Int
)
