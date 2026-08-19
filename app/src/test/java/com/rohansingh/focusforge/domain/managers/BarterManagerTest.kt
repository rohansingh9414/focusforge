package com.rohansingh.focusforge.domain.managers

import android.content.Context
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import com.rohansingh.focusforge.domain.models.ExchangeDirection
import com.rohansingh.focusforge.domain.models.ExchangeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarterManagerTest {

    private lateinit var fakeWalletDao: FakeWalletDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var fakeConfigRepo: FakeExchangeConfigRepository
    private lateinit var barterManager: BarterManager

    @Before
    fun setup() {
        fakeWalletDao = FakeWalletDao()
        walletRepository = WalletRepository(fakeWalletDao)
        fakeConfigRepo = FakeExchangeConfigRepository()
        barterManager = BarterManager(walletRepository, fakeConfigRepo)
    }

    @Test
    fun `preview rupees to credits with 0 percent fee`() {
        val config = ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)
        val preview = barterManager.calculatePreview(ExchangeDirection.RUPEES_TO_CREDITS, 50.0, config)

        assertEquals(50.0, preview.inputAmount, 0.001)
        assertEquals(50.0, preview.grossAmount, 0.001)
        assertEquals(0.0, preview.feePercent, 0.001)
        assertEquals(0.0, preview.feeAmount, 0.001)
        assertEquals(50.0, preview.netAmount, 0.001)
        assertEquals("₹", preview.fromUnit)
        assertEquals("credits", preview.toUnit)
    }

    @Test
    fun `preview rupees to credits with 10 percent fee`() {
        val config = ExchangeConfig(creditsPerRupee = 2.0, exchangeFeePercent = 10.0)
        val preview = barterManager.calculatePreview(ExchangeDirection.RUPEES_TO_CREDITS, 20.0, config)

        assertEquals(20.0, preview.inputAmount, 0.001)
        assertEquals(40.0, preview.grossAmount, 0.001) // 20 * 2.0 = 40.0 credits
        assertEquals(10.0, preview.feePercent, 0.001)
        assertEquals(4.0, preview.feeAmount, 0.001)    // 10% of 40 = 4.0 credits
        assertEquals(36.0, preview.netAmount, 0.001)   // 40 - 4 = 36.0 credits
    }

    @Test
    fun `preview credits to rupees with 5 percent fee`() {
        val config = ExchangeConfig(creditsPerRupee = 2.0, exchangeFeePercent = 5.0)
        val preview = barterManager.calculatePreview(ExchangeDirection.CREDITS_TO_RUPEES, 100.0, config)

        assertEquals(100.0, preview.inputAmount, 0.001)
        assertEquals(50.0, preview.grossAmount, 0.001) // 100 / 2.0 = 50.0 rupees
        assertEquals(5.0, preview.feePercent, 0.001)
        assertEquals(2.5, preview.feeAmount, 0.001)    // 5% of 50 = 2.5 rupees
        assertEquals(47.5, preview.netAmount, 0.001)   // 50 - 2.5 = 47.5 rupees
        assertEquals("credits", preview.fromUnit)
        assertEquals("₹", preview.toUnit)
    }

    @Test
    fun `execute rupees to credits succeeds and updates wallet`() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 10.0, rupeeBalance = 50.0, screenTimeMinutes = 60)
        fakeConfigRepo.config = ExchangeConfig(creditsPerRupee = 1.5, exchangeFeePercent = 10.0)

        // Exchange ₹20 -> gross 30 credits, fee 3 credits, net 27 credits
        val result = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 20.0)

        assertTrue(result.isSuccess)
        val exchangeResult = result.getOrNull()
        assertTrue(exchangeResult is ExchangeResult.Success)
        val success = exchangeResult as ExchangeResult.Success
        assertEquals(20.0, success.inputAmount, 0.001)
        assertEquals(3.0, success.feeAmount, 0.001)
        assertEquals(27.0, success.netAmount, 0.001)

        val updatedWallet = fakeWalletDao.wallet!!
        assertEquals(30.0, updatedWallet.rupeeBalance, 0.001) // 50 - 20 = 30
        assertEquals(37.0, updatedWallet.creditBalance, 0.001) // 10 + 27 = 37
        assertEquals(60, updatedWallet.screenTimeMinutes)     // unchanged
    }

    @Test
    fun `execute credits to rupees succeeds and updates wallet`() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 50.0, rupeeBalance = 10.0, screenTimeMinutes = 60)
        fakeConfigRepo.config = ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)

        // Exchange 30 credits -> 30 rupees
        val result = barterManager.executeExchange(ExchangeDirection.CREDITS_TO_RUPEES, 30.0)

        assertTrue(result.isSuccess)
        val exchangeResult = result.getOrNull()
        assertTrue(exchangeResult is ExchangeResult.Success)
        val success = exchangeResult as ExchangeResult.Success
        assertEquals(30.0, success.inputAmount, 0.001)
        assertEquals(0.0, success.feeAmount, 0.001)
        assertEquals(30.0, success.netAmount, 0.001)

        val updatedWallet = fakeWalletDao.wallet!!
        assertEquals(20.0, updatedWallet.creditBalance, 0.001) // 50 - 30 = 20
        assertEquals(40.0, updatedWallet.rupeeBalance, 0.001)  // 10 + 30 = 40
    }

    @Test
    fun `execute fails when rupee balance is insufficient`() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 0.0, rupeeBalance = 25.0)
        fakeConfigRepo.config = ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)

        val result = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 50.0)

        assertTrue(result.isSuccess)
        val exchangeResult = result.getOrNull()
        assertTrue(exchangeResult is ExchangeResult.InsufficientBalance)
        val insufficient = exchangeResult as ExchangeResult.InsufficientBalance
        assertEquals(50.0, insufficient.requiredAmount, 0.001)
        assertEquals(25.0, insufficient.availableAmount, 0.001)
        assertEquals("₹", insufficient.currencyUnit)

        // Wallet must remain unchanged
        assertEquals(25.0, fakeWalletDao.wallet!!.rupeeBalance, 0.001)
        assertEquals(0.0, fakeWalletDao.wallet!!.creditBalance, 0.001)
    }

    @Test
    fun `execute fails when credit balance is insufficient`() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 15.0, rupeeBalance = 0.0)
        fakeConfigRepo.config = ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)

        val result = barterManager.executeExchange(ExchangeDirection.CREDITS_TO_RUPEES, 20.0)

        assertTrue(result.isSuccess)
        val exchangeResult = result.getOrNull()
        assertTrue(exchangeResult is ExchangeResult.InsufficientBalance)
        val insufficient = exchangeResult as ExchangeResult.InsufficientBalance
        assertEquals(20.0, insufficient.requiredAmount, 0.001)
        assertEquals(15.0, insufficient.availableAmount, 0.001)
        assertEquals("credits", insufficient.currencyUnit)

        // Wallet must remain unchanged
        assertEquals(15.0, fakeWalletDao.wallet!!.creditBalance, 0.001)
        assertEquals(0.0, fakeWalletDao.wallet!!.rupeeBalance, 0.001)
    }

    @Test
    fun `execute fails for zero and negative amounts`() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 50.0, rupeeBalance = 50.0)
        fakeConfigRepo.config = ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)

        val zeroResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 0.0)
        assertTrue(zeroResult.getOrNull() is ExchangeResult.InvalidAmount)

        val negativeResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, -10.0)
        assertTrue(negativeResult.getOrNull() is ExchangeResult.InvalidAmount)

        // Wallet must remain unchanged
        assertEquals(50.0, fakeWalletDao.wallet!!.rupeeBalance, 0.001)
        assertEquals(50.0, fakeWalletDao.wallet!!.creditBalance, 0.001)
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

    private class FakeExchangeConfigRepository : ExchangeConfigRepository(
        android.content.ContextWrapper(null)
    ) {
        var config: ExchangeConfig = ExchangeConfig()

        override val exchangeConfig: Flow<ExchangeConfig>
            get() = flowOf(config)

        override suspend fun getExchangeConfigOnce(): ExchangeConfig = config

        override suspend fun updateExchangeConfig(config: ExchangeConfig) {
            this.config = config
        }
    }
}
