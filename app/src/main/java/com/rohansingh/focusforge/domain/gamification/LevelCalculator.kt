package com.rohansingh.focusforge.domain.gamification

import com.rohansingh.focusforge.domain.models.LevelInfo

/**
 * Pure domain calculator for converting total cumulative XP into level and progress metrics.
 */
object LevelCalculator {

    fun calculateLevel(
        totalXp: Long,
        thresholds: List<Long> = GamificationConfig.DEFAULT_LEVEL_THRESHOLDS
    ): LevelInfo {
        val xp = totalXp.coerceAtLeast(0L)

        // Below first threshold (< 100 XP) -> Level 1
        if (thresholds.isEmpty() || xp < thresholds[0]) {
            val next = thresholds.firstOrNull() ?: 100L
            val progress = (xp.toFloat() / next.toFloat()).coerceIn(0f, 1f)
            return LevelInfo(
                currentLevel = 1,
                currentLevelMinXp = 0L,
                nextLevelXp = next,
                currentLevelXpProgress = xp,
                xpRequiredForNextLevel = next - xp,
                progressPercent = progress
            )
        }

        // Check configured cumulative thresholds
        for (i in 0 until thresholds.size - 1) {
            val minXp = thresholds[i]
            val maxXp = thresholds[i + 1]
            if (xp in minXp until maxXp) {
                val level = i + 2
                val levelSpan = maxXp - minXp
                val progressInLevel = xp - minXp
                val progressPercent = if (levelSpan > 0) {
                    (progressInLevel.toFloat() / levelSpan.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                return LevelInfo(
                    currentLevel = level,
                    currentLevelMinXp = minXp,
                    nextLevelXp = maxXp,
                    currentLevelXpProgress = progressInLevel,
                    xpRequiredForNextLevel = maxXp - xp,
                    progressPercent = progressPercent
                )
            }
        }

        // At or above the highest defined threshold
        val lastThreshold = thresholds.last()
        val baseLevel = thresholds.size + 1 // e.g., 9 thresholds -> Level 10
        val excessXp = xp - lastThreshold
        val extraLevels = (excessXp / GamificationConfig.POST_MAX_THRESHOLD_STEP).toInt()
        val currentLevel = baseLevel + extraLevels
        val currentLevelMinXp = lastThreshold + (extraLevels * GamificationConfig.POST_MAX_THRESHOLD_STEP)
        val nextLevelXp = currentLevelMinXp + GamificationConfig.POST_MAX_THRESHOLD_STEP
        val progressInLevel = xp - currentLevelMinXp
        val progressPercent = (progressInLevel.toFloat() / GamificationConfig.POST_MAX_THRESHOLD_STEP.toFloat()).coerceIn(0f, 1f)

        return LevelInfo(
            currentLevel = currentLevel,
            currentLevelMinXp = currentLevelMinXp,
            nextLevelXp = nextLevelXp,
            currentLevelXpProgress = progressInLevel,
            xpRequiredForNextLevel = nextLevelXp - xp,
            progressPercent = progressPercent
        )
    }
}
