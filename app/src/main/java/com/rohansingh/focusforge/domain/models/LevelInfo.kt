package com.rohansingh.focusforge.domain.models

/**
 * Domain model representing the user's current level and progression towards the next level.
 */
data class LevelInfo(
    val currentLevel: Int,
    val currentLevelMinXp: Long,
    val nextLevelXp: Long,
    val currentLevelXpProgress: Long,
    val xpRequiredForNextLevel: Long,
    val progressPercent: Float
)
