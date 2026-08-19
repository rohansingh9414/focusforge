package com.rohansingh.focusforge.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.domain.managers.RewardManager
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import com.rohansingh.focusforge.domain.models.PricingMode
import com.rohansingh.focusforge.domain.models.RewardType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RewardUiItem(
    val template: RewardTemplate,
    val effectiveCreditRate: Double
)

data class RewardDialogState(
    val isAddEditOpen: Boolean = false,
    val editingReward: RewardTemplate? = null,
    val isRedeemOpen: Boolean = false,
    val redeemingReward: RewardTemplate? = null
)

data class RewardsUiState(
    val rewards: List<RewardUiItem> = emptyList(),
    val exchangeConfig: ExchangeConfig = ExchangeConfig(),
    val isAddEditRewardDialogOpen: Boolean = false,
    val editingReward: RewardTemplate? = null,
    val isRedeemDialogOpen: Boolean = false,
    val redeemingReward: RewardTemplate? = null,
    val isLoading: Boolean = false
)

class RewardsViewModel(
    private val rewardRepository: RewardRepository,
    private val rewardManager: RewardManager,
    private val exchangeConfigRepository: ExchangeConfigRepository
) : ViewModel() {

    private val _dialogState = MutableStateFlow(RewardDialogState())

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<RewardsUiState> = combine(
        rewardRepository.allRewardTemplates,
        exchangeConfigRepository.exchangeConfig,
        _dialogState
    ) { rewards, config, dialogState ->
        val uiItems = rewards.map { template ->
            RewardUiItem(
                template = template,
                effectiveCreditRate = rewardManager.calculateEffectiveCreditRate(
                    template,
                    config.creditsPerRupee
                )
            )
        }
        RewardsUiState(
            rewards = uiItems,
            exchangeConfig = config,
            isAddEditRewardDialogOpen = dialogState.isAddEditOpen,
            editingReward = dialogState.editingReward,
            isRedeemDialogOpen = dialogState.isRedeemOpen,
            redeemingReward = dialogState.redeemingReward,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RewardsUiState(isLoading = true)
    )

    fun openAddRewardDialog() {
        _dialogState.value = RewardDialogState(isAddEditOpen = true, editingReward = null)
    }

    fun openEditRewardDialog(reward: RewardTemplate) {
        _dialogState.value = RewardDialogState(isAddEditOpen = true, editingReward = reward)
    }

    fun closeAddEditRewardDialog() {
        _dialogState.value = _dialogState.value.copy(isAddEditOpen = false, editingReward = null)
    }

    fun openRedeemDialog(reward: RewardTemplate) {
        _dialogState.value = RewardDialogState(isRedeemOpen = true, redeemingReward = reward)
    }

    fun closeRedeemDialog() {
        _dialogState.value = _dialogState.value.copy(isRedeemOpen = false, redeemingReward = null)
    }

    fun saveReward(
        id: Long = 0,
        title: String,
        unit: String,
        rewardType: RewardType,
        pricingMode: PricingMode,
        rupeeCost: Double,
        creditRate: Double,
        isActive: Boolean = true
    ) {
        viewModelScope.launch {
            if (title.isBlank() || unit.isBlank()) {
                _snackbarMessage.emit("Please enter valid title and unit")
                return@launch
            }

            if (pricingMode == PricingMode.AUTO && rupeeCost <= 0.0) {
                _snackbarMessage.emit("Auto pricing requires positive rupee cost")
                return@launch
            }

            if (pricingMode == PricingMode.MANUAL && creditRate <= 0.0) {
                _snackbarMessage.emit("Manual pricing requires positive credit rate")
                return@launch
            }

            val reward = RewardTemplate(
                id = id,
                title = title.trim(),
                unit = unit.trim(),
                rewardType = rewardType,
                pricingMode = pricingMode,
                rupeeCost = if (pricingMode == PricingMode.AUTO) rupeeCost else 0.0,
                creditRate = if (pricingMode == PricingMode.MANUAL) creditRate else 0.0,
                isActive = isActive
            )

            if (id == 0L) {
                rewardRepository.insertRewardTemplate(reward)
                _snackbarMessage.emit("Reward created")
            } else {
                rewardRepository.updateRewardTemplate(reward)
                _snackbarMessage.emit("Reward updated")
            }
            closeAddEditRewardDialog()
        }
    }

    fun deleteReward(reward: RewardTemplate) {
        viewModelScope.launch {
            rewardRepository.deleteRewardTemplate(reward)
            _snackbarMessage.emit("Reward deleted")
        }
    }

    fun redeemReward(reward: RewardTemplate, units: Double) {
        viewModelScope.launch {
            val result = rewardManager.redeem(reward, units)
            result.onSuccess { cost ->
                _snackbarMessage.emit("Redeemed! Spent ${String.format("%.1f", cost)} credits.")
                closeRedeemDialog()
            }.onFailure { error ->
                _snackbarMessage.emit(error.message ?: "Redemption failed")
            }
        }
    }

    class Factory(
        private val rewardRepository: RewardRepository,
        private val rewardManager: RewardManager,
        private val exchangeConfigRepository: ExchangeConfigRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RewardsViewModel::class.java)) {
                return RewardsViewModel(rewardRepository, rewardManager, exchangeConfigRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
