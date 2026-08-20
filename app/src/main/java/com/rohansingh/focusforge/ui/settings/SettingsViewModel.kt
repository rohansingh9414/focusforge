package com.rohansingh.focusforge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.ThemeRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.BarterManager
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import com.rohansingh.focusforge.domain.models.ExchangeDirection
import com.rohansingh.focusforge.domain.models.ExchangePreview
import com.rohansingh.focusforge.domain.models.ExchangeResult
import com.rohansingh.focusforge.domain.models.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wallet: Wallet = Wallet(),
    val exchangeConfig: ExchangeConfig = ExchangeConfig(),
    val direction: ExchangeDirection = ExchangeDirection.RUPEES_TO_CREDITS,
    val amountInput: String = "",
    val preview: ExchangePreview? = null,
    val creditsPerRupeeInput: String = "1.0",
    val exchangeFeePercentInput: String = "0.0",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val userMessage: String? = null
)

class SettingsViewModel(
    private val walletRepository: WalletRepository,
    private val exchangeConfigRepository: ExchangeConfigRepository,
    private val barterManager: BarterManager,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _direction = MutableStateFlow(ExchangeDirection.RUPEES_TO_CREDITS)
    private val _amountInput = MutableStateFlow("")
    private val _creditsPerRupeeInput = MutableStateFlow("1.0")
    private val _exchangeFeePercentInput = MutableStateFlow("0.0")
    private val _userMessage = MutableStateFlow<String?>(null)

    private var hasInitializedConfigInputs = false

    val uiState: StateFlow<SettingsUiState> = combine(
        walletRepository.wallet,
        exchangeConfigRepository.exchangeConfig,
        themeRepository.themeMode,
        _direction,
        _amountInput,
        _creditsPerRupeeInput,
        _exchangeFeePercentInput,
        _userMessage
    ) { flows ->
        val wallet = (flows[0] as? Wallet) ?: Wallet()
        val config = flows[1] as ExchangeConfig
        val themeMode = flows[2] as ThemeMode
        val direction = flows[3] as ExchangeDirection
        val amountInput = flows[4] as String
        val rateInput = flows[5] as String
        val feeInput = flows[6] as String
        val message = flows[7] as? String

        if (!hasInitializedConfigInputs) {
            _creditsPerRupeeInput.value = config.creditsPerRupee.toString()
            _exchangeFeePercentInput.value = config.exchangeFeePercent.toString()
            hasInitializedConfigInputs = true
        }

        val amount = amountInput.toDoubleOrNull() ?: 0.0
        val preview = if (amount > 0.0) {
            barterManager.calculatePreview(direction, amount, config)
        } else {
            null
        }

        SettingsUiState(
            wallet = wallet,
            exchangeConfig = config,
            direction = direction,
            amountInput = amountInput,
            preview = preview,
            creditsPerRupeeInput = rateInput,
            exchangeFeePercentInput = feeInput,
            themeMode = themeMode,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            walletRepository.ensureWalletInitialized()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.updateThemeMode(mode)
        }
    }

    fun setDirection(direction: ExchangeDirection) {
        _direction.value = direction
    }

    fun onAmountInputChanged(input: String) {
        _amountInput.value = input
    }

    fun onCreditsPerRupeeInputChanged(input: String) {
        _creditsPerRupeeInput.value = input
    }

    fun onExchangeFeePercentInputChanged(input: String) {
        _exchangeFeePercentInput.value = input
    }

    fun saveConfig() {
        val rate = _creditsPerRupeeInput.value.toDoubleOrNull()
        val fee = _exchangeFeePercentInput.value.toDoubleOrNull()

        if (rate == null || rate <= 0.0) {
            _userMessage.value = "Credits per Rupee must be greater than 0"
            return
        }
        if (fee == null || fee < 0.0 || fee > 100.0) {
            _userMessage.value = "Exchange fee must be between 0% and 100%"
            return
        }

        viewModelScope.launch {
            exchangeConfigRepository.updateExchangeConfig(
                ExchangeConfig(
                    creditsPerRupee = rate,
                    exchangeFeePercent = fee
                )
            )
            _userMessage.value = "Exchange settings updated"
        }
    }

    fun executeExchange() {
        val amount = _amountInput.value.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _userMessage.value = "Enter a valid amount greater than 0"
            return
        }

        viewModelScope.launch {
            val result = barterManager.executeExchange(_direction.value, amount)
            result.fold(
                onSuccess = { exchangeResult ->
                    when (exchangeResult) {
                        is ExchangeResult.Success -> {
                            _amountInput.value = ""
                            val from = if (exchangeResult.direction == ExchangeDirection.RUPEES_TO_CREDITS) "₹${exchangeResult.inputAmount}" else "${exchangeResult.inputAmount} credits"
                            val to = if (exchangeResult.direction == ExchangeDirection.RUPEES_TO_CREDITS) "${exchangeResult.netAmount} credits" else "₹${exchangeResult.netAmount}"
                            _userMessage.value = "Exchanged $from for $to (Fee: ${exchangeResult.feeAmount})"
                        }
                        is ExchangeResult.InsufficientBalance -> {
                            _userMessage.value = "Insufficient ${exchangeResult.currencyUnit} balance. Required: ${exchangeResult.requiredAmount}, Available: ${exchangeResult.availableAmount}"
                        }
                        is ExchangeResult.InvalidAmount -> {
                            _userMessage.value = exchangeResult.reason
                        }
                        is ExchangeResult.InvalidConfig -> {
                            _userMessage.value = exchangeResult.reason
                        }
                    }
                },
                onFailure = { error ->
                    _userMessage.value = error.message ?: "Failed to execute exchange"
                }
            )
        }
    }

    fun dismissMessage() {
        _userMessage.value = null
    }

    class Factory(
        private val walletRepository: WalletRepository,
        private val exchangeConfigRepository: ExchangeConfigRepository,
        private val barterManager: BarterManager,
        private val themeRepository: ThemeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(walletRepository, exchangeConfigRepository, barterManager, themeRepository) as T
        }
    }
}
