package com.rohansingh.focusforge.domain.models

/**
 * Calculated preview of an exchange operation before execution.
 * Defined in ECONOMY.md §6 and ROADMAP.md Phase 6.
 */
data class ExchangePreview(
    val direction: ExchangeDirection,
    val inputAmount: Double,
    val grossAmount: Double,
    val feePercent: Double,
    val feeAmount: Double,
    val netAmount: Double,
    val fromUnit: String,
    val toUnit: String
)
