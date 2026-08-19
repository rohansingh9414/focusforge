package com.rohansingh.focusforge.domain.models

/**
 * Represents the outcome of attempting to apply a daily grant.
 * Defined in ECONOMY.md §7 and ROADMAP.md Phase 5.
 */
sealed interface DailyGrantResult {
    /**
     * The daily grant was successfully applied for [date].
     */
    data class Applied(
        val rupeeGranted: Double = 50.0,
        val screenTimeGranted: Int = 60,
        val date: String
    ) : DailyGrantResult

    /**
     * The daily grant had already been applied on [date], so no changes were made.
     */
    data class AlreadyApplied(
        val date: String
    ) : DailyGrantResult
}
