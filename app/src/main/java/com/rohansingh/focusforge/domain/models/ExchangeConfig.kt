package com.rohansingh.focusforge.domain.models

/**
 * Model representing the rupee-to-credit exchange configuration.
 * Defined in ECONOMY.md §6.
 */
data class ExchangeConfig(
    val creditsPerRupee: Double = 1.0,
    val exchangeFeePercent: Double = 0.0
)
