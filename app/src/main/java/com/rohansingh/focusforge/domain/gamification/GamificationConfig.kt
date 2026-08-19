package com.rohansingh.focusforge.domain.gamification

/**
 * Configuration values and thresholds for Phase 8 Gamification.
 * Defined in ROADMAP.md Phase 8.
 */
object GamificationConfig {
    const val XP_PER_CREDIT = 10.0

    // Config-driven cumulative thresholds
    val DEFAULT_LEVEL_THRESHOLDS = listOf(
        100L,   // Level 2 at 100 XP
        250L,   // Level 3 at 250 XP
        500L,   // Level 4 at 500 XP
        1000L,  // Level 5 at 1000 XP
        2000L,  // Level 6 at 2000 XP
        3500L,  // Level 7 at 3500 XP
        5000L,  // Level 8 at 5000 XP
        7500L,  // Level 9 at 7500 XP
        10000L  // Level 10 at 10000 XP
    )

    const val POST_MAX_THRESHOLD_STEP = 2500L
    const val STREAK_BONUS_PER_DAY = 0.02 // +2% per existing consecutive streak day
    const val MAX_STREAK_BONUS = 0.20     // max +20%
}
