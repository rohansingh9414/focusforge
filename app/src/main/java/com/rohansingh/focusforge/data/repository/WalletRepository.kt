package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing clean abstraction over Wallet database operations.
 */
class WalletRepository(private val walletDao: WalletDao) {

    val wallet: Flow<Wallet?> = walletDao.getWallet()

    suspend fun getWalletOnce(): Wallet? = walletDao.getWalletOnce()

    suspend fun updateWallet(wallet: Wallet) {
        walletDao.updateWallet(wallet)
    }

    suspend fun ensureWalletInitialized() {
        if (walletDao.getWalletOnce() == null) {
            walletDao.insertWallet(
                Wallet(
                    id = 1,
                    creditBalance = 0.0,
                    rupeeBalance = 0.0,
                    screenTimeMinutes = 0,
                    lastDailyGrantDate = null
                )
            )
        }
    }
}
