package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import com.rohansingh.focusforge.domain.models.ExchangeDirection
import com.rohansingh.focusforge.domain.models.ExchangePreview
import com.rohansingh.focusforge.domain.models.ExchangeResult

/**
 * Domain manager executing barter / currency exchange business rules, conversion math,
 * fee deductions, balance validations, and wallet balance updates.
 * Defined in ECONOMY.md §6, §9 and ROADMAP.md Phase 6.
 */
class BarterManager(
    private val walletRepository: WalletRepository,
    private val exchangeConfigRepository: ExchangeConfigRepository
) {

    /**
     * Computes a live preview of the exchange for the given [direction], [amount], and [config].
     */
    fun calculatePreview(
        direction: ExchangeDirection,
        amount: Double,
        config: ExchangeConfig
    ): ExchangePreview {
        val safeAmount = if (amount > 0.0) amount else 0.0
        val safeFeePercent = config.exchangeFeePercent.coerceIn(0.0, 100.0)

        return when (direction) {
            ExchangeDirection.RUPEES_TO_CREDITS -> {
                val grossCredits = safeAmount * config.creditsPerRupee
                val feeCredits = grossCredits * (safeFeePercent / 100.0)
                val netCredits = grossCredits - feeCredits
                ExchangePreview(
                    direction = direction,
                    inputAmount = safeAmount,
                    grossAmount = grossCredits,
                    feePercent = safeFeePercent,
                    feeAmount = feeCredits,
                    netAmount = netCredits,
                    fromUnit = "₹",
                    toUnit = "credits"
                )
            }
            ExchangeDirection.CREDITS_TO_RUPEES -> {
                val grossRupees = if (config.creditsPerRupee > 0.0) {
                    safeAmount / config.creditsPerRupee
                } else {
                    0.0
                }
                val feeRupees = grossRupees * (safeFeePercent / 100.0)
                val netRupees = grossRupees - feeRupees
                ExchangePreview(
                    direction = direction,
                    inputAmount = safeAmount,
                    grossAmount = grossRupees,
                    feePercent = safeFeePercent,
                    feeAmount = feeRupees,
                    netAmount = netRupees,
                    fromUnit = "credits",
                    toUnit = "₹"
                )
            }
        }
    }

    /**
     * Executes the currency exchange between rupees and credits.
     * Validates input amount, configuration, and user's current wallet balance.
     * Updates the wallet atomically upon success.
     */
    suspend fun executeExchange(
        direction: ExchangeDirection,
        amount: Double
    ): Result<ExchangeResult> {
        if (amount <= 0.0) {
            return Result.success(ExchangeResult.InvalidAmount("Amount must be greater than 0"))
        }

        return try {
            val config = exchangeConfigRepository.getExchangeConfigOnce()
            if (config.creditsPerRupee <= 0.0) {
                return Result.success(ExchangeResult.InvalidConfig("Credits per rupee must be greater than 0"))
            }
            if (config.exchangeFeePercent < 0.0 || config.exchangeFeePercent > 100.0) {
                return Result.success(ExchangeResult.InvalidConfig("Exchange fee must be between 0% and 100%"))
            }

            walletRepository.ensureWalletInitialized()
            val currentWallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)

            when (direction) {
                ExchangeDirection.RUPEES_TO_CREDITS -> {
                    if (currentWallet.rupeeBalance < amount) {
                        return Result.success(
                            ExchangeResult.InsufficientBalance(
                                requiredAmount = amount,
                                availableAmount = currentWallet.rupeeBalance,
                                currencyUnit = "₹"
                            )
                        )
                    }

                    val preview = calculatePreview(direction, amount, config)
                    val updatedWallet = currentWallet.copy(
                        rupeeBalance = currentWallet.rupeeBalance - amount,
                        creditBalance = currentWallet.creditBalance + preview.netAmount
                    )
                    walletRepository.updateWallet(updatedWallet)

                    Result.success(
                        ExchangeResult.Success(
                            direction = direction,
                            inputAmount = amount,
                            feeAmount = preview.feeAmount,
                            netAmount = preview.netAmount
                        )
                    )
                }

                ExchangeDirection.CREDITS_TO_RUPEES -> {
                    if (currentWallet.creditBalance < amount) {
                        return Result.success(
                            ExchangeResult.InsufficientBalance(
                                requiredAmount = amount,
                                availableAmount = currentWallet.creditBalance,
                                currencyUnit = "credits"
                            )
                        )
                    }

                    val preview = calculatePreview(direction, amount, config)
                    val updatedWallet = currentWallet.copy(
                        creditBalance = currentWallet.creditBalance - amount,
                        rupeeBalance = currentWallet.rupeeBalance + preview.netAmount
                    )
                    walletRepository.updateWallet(updatedWallet)

                    Result.success(
                        ExchangeResult.Success(
                            direction = direction,
                            inputAmount = amount,
                            feeAmount = preview.feeAmount,
                            netAmount = preview.netAmount
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
