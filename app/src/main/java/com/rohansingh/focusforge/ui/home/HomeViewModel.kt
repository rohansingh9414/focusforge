package com.rohansingh.focusforge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.repository.WalletRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val creditBalance: Double = 0.0,
    val rupeeBalance: Double = 0.0,
    val screenTimeMinutes: Int = 0,
    val isLoading: Boolean = false
)

class HomeViewModel(private val walletRepository: WalletRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            walletRepository.ensureWalletInitialized()
        }
    }

    val uiState: StateFlow<HomeUiState> = walletRepository.wallet
        .map { wallet ->
            if (wallet != null) {
                HomeUiState(
                    creditBalance = wallet.creditBalance,
                    rupeeBalance = wallet.rupeeBalance,
                    screenTimeMinutes = wallet.screenTimeMinutes,
                    isLoading = false
                )
            } else {
                HomeUiState(isLoading = true)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

    class Factory(private val walletRepository: WalletRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(walletRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
