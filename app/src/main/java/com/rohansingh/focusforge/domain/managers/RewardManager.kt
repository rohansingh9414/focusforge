package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.PricingMode
import com.rohansingh.focusforge.domain.models.RewardType

/**
 * Domain manager executing reward pricing calculations,
 * redemption validation, credit deductions, and screen-time increments.
 * Defined in ECONOMY.md §5, §8, §9 and ROADMAP.md Phase 4.
 */
class RewardManager(
    private val rewardRepository: RewardRepository,
    private val walletRepository: WalletRepository,
    private val exchangeConfigRepository: ExchangeConfigRepository
) {

    /**
     * Calculates the effective credit price per unit for a reward.
     * AUTO: rupeeCost * creditsPerRupee
     * MANUAL: creditRate
     */
    fun calculateEffectiveCreditRate(reward: RewardTemplate, creditsPerRupee: Double): Double {
        return if (reward.pricingMode == PricingMode.AUTO) {
            reward.rupeeCost * creditsPerRupee
        } else {
            reward.creditRate
        }
    }

    /**
     * Executes the redemption of a reward for the specified number of units.
     * Validates balance, deducts credits, grants screen-time if applicable, and writes RedemptionLog.
     */
    suspend fun redeem(reward: RewardTemplate, units: Double): Result<Double> {
        if (units <= 0.0) {
            return Result.failure(IllegalArgumentException("Redemption units must be greater than 0"))
        }

        val exchangeConfig = exchangeConfigRepository.getExchangeConfigOnce()
        val unitCreditRate = calculateEffectiveCreditRate(reward, exchangeConfig.creditsPerRupee)
        val totalCreditCost = units * unitCreditRate

        val currentWallet = walletRepository.getWalletOnce() ?: Wallet()

        if (currentWallet.creditBalance < totalCreditCost) {
            return Result.failure(
                IllegalStateException(
                    "Insufficient credits. Required: ${String.format("%.1f", totalCreditCost)}, Available: ${String.format("%.1f", currentWallet.creditBalance)}"
                )
            )
        }

        // Deduct credits from wallet and increment screen time if rewardType is SCREEN_TIME
        val newCreditBalance = currentWallet.creditBalance - totalCreditCost
        val newScreenTimeMinutes = if (reward.rewardType == RewardType.SCREEN_TIME) {
            currentWallet.screenTimeMinutes + units.toInt()
        } else {
            currentWallet.screenTimeMinutes
        }

        walletRepository.updateWallet(
            currentWallet.copy(
                creditBalance = newCreditBalance,
                screenTimeMinutes = newScreenTimeMinutes
            )
        )

        // Write redemption log
        val log = RedemptionLog(
            rewardTemplateId = reward.id,
            unitsRedeemed = units,
            creditsSpent = totalCreditCost,
            redeemedAt = System.currentTimeMillis()
        )
        rewardRepository.insertRedemptionLog(log)

        return Result.success(totalCreditCost)
    }
}
