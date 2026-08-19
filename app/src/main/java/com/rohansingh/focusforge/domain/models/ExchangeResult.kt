package com.rohansingh.focusforge.domain.models

/**
 * Result of executing an exchange operation.
 * Defined in ECONOMY.md §6 and §9.
 */
sealed interface ExchangeResult {
    /**
     * Exchange completed successfully.
     */
    data class Success(
        val direction: ExchangeDirection,
        val inputAmount: Double,
        val feeAmount: Double,
        val netAmount: Double
    ) : ExchangeResult

    /**
     * Failed due to insufficient balance.
     */
    data class InsufficientBalance(
        val requiredAmount: Double,
        val availableAmount: Double,
        val currencyUnit: String
    ) : ExchangeResult

    /**
     * Failed due to invalid input amount (e.g. <= 0).
     */
    data class InvalidAmount(
        val reason: String
    ) : ExchangeResult

    /**
     * Failed due to invalid exchange configuration (e.g. rate <= 0 or invalid fee).
     */
    data class InvalidConfig(
        val reason: String
    ) : ExchangeResult
}
