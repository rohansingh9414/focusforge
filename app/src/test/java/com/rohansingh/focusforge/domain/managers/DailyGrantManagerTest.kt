package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.DailyGrantResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DailyGrantManagerTest {

    private lateinit var fakeWalletDao: FakeWalletDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var dailyGrantManager: DailyGrantManager

    @Before
    fun setup() {
        fakeWalletDao = FakeWalletDao()
        walletRepository = WalletRepository(fakeWalletDao)
        dailyGrantManager = DailyGrantManager(walletRepository)
    }

    @Test
    fun `first daily grant applies 50 rupees and 60 minutes screen time`() = runBlocking {
        // Initial state: no grant date, 0 balances, 10 credits
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 10.0, rupeeBalance = 0.0, screenTimeMinutes = 0, lastDailyGrantDate = null)

        val result = dailyGrantManager.applyDailyGrant("2026-08-19")

        assertTrue(result.isSuccess)
        val grant = result.getOrNull()
        assertTrue(grant is DailyGrantResult.Applied)
        assertEquals(50.0, (grant as DailyGrantResult.Applied).rupeeGranted, 0.001)
        assertEquals(60, grant.screenTimeGranted)
        assertEquals("2026-08-19", grant.date)

        val updatedWallet = fakeWalletDao.wallet!!
        assertEquals(10.0, updatedWallet.creditBalance, 0.001) // credit balance unchanged
        assertEquals(50.0, updatedWallet.rupeeBalance, 0.001)  // rupee balance +50
        assertEquals(60, updatedWallet.screenTimeMinutes)      // screen time +60
        assertEquals("2026-08-19", updatedWallet.lastDailyGrantDate)
    }

    @Test
    fun `idempotency - duplicate grant on same day does not modify balances`() = runBlocking {
        // State where grant was already applied for today
        fakeWalletDao.wallet = Wallet(
            id = 1,
            creditBalance = 25.0,
            rupeeBalance = 50.0,
            screenTimeMinutes = 60,
            lastDailyGrantDate = "2026-08-19"
        )

        val result = dailyGrantManager.applyDailyGrant("2026-08-19")

        assertTrue(result.isSuccess)
        val grant = result.getOrNull()
        assertTrue(grant is DailyGrantResult.AlreadyApplied)
        assertEquals("2026-08-19", (grant as DailyGrantResult.AlreadyApplied).date)

        // Verify wallet remains completely untouched
        val currentWallet = fakeWalletDao.wallet!!
        assertEquals(25.0, currentWallet.creditBalance, 0.001)
        assertEquals(50.0, currentWallet.rupeeBalance, 0.001)
        assertEquals(60, currentWallet.screenTimeMinutes)
        assertEquals("2026-08-19", currentWallet.lastDailyGrantDate)
    }

    @Test
    fun `next date grant applies new reward when date changes`() = runBlocking {
        // State where yesterday (2026-08-18) was granted
        fakeWalletDao.wallet = Wallet(
            id = 1,
            creditBalance = 100.0,
            rupeeBalance = 50.0,
            screenTimeMinutes = 60,
            lastDailyGrantDate = "2026-08-18"
        )

        // Apply grant on today (2026-08-19)
        val resultToday = dailyGrantManager.applyDailyGrant("2026-08-19")
        assertTrue(resultToday.isSuccess)
        assertTrue(resultToday.getOrNull() is DailyGrantResult.Applied)

        val walletToday = fakeWalletDao.wallet!!
        assertEquals(100.0, walletToday.creditBalance, 0.001)
        assertEquals(100.0, walletToday.rupeeBalance, 0.001) // 50 + 50 = 100
        assertEquals(120, walletToday.screenTimeMinutes)     // 60 + 60 = 120
        assertEquals("2026-08-19", walletToday.lastDailyGrantDate)

        // Apply grant on tomorrow (2026-08-20)
        val resultTomorrow = dailyGrantManager.applyDailyGrant("2026-08-20")
        assertTrue(resultTomorrow.isSuccess)
        assertTrue(resultTomorrow.getOrNull() is DailyGrantResult.Applied)

        val walletTomorrow = fakeWalletDao.wallet!!
        assertEquals(150.0, walletTomorrow.rupeeBalance, 0.001) // 100 + 50 = 150
        assertEquals(180, walletTomorrow.screenTimeMinutes)     // 120 + 60 = 180
        assertEquals("2026-08-20", walletTomorrow.lastDailyGrantDate)
    }

    private class FakeWalletDao : WalletDao {
        var wallet: Wallet? = null

        override fun getWallet(): Flow<Wallet?> = flowOf(wallet)

        override suspend fun getWalletOnce(): Wallet? = wallet

        override suspend fun insertWallet(wallet: Wallet): Long {
            this.wallet = wallet
            return 1L
        }

        override suspend fun updateWallet(wallet: Wallet): Int {
            this.wallet = wallet
            return 1
        }
    }
}
