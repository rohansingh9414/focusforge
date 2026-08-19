package com.rohansingh.focusforge

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.BarterManager
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import com.rohansingh.focusforge.domain.models.ExchangeDirection
import com.rohansingh.focusforge.domain.models.ExchangeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarterAndroidTest {

    private lateinit var database: AppDatabase
    private lateinit var walletRepository: WalletRepository
    private lateinit var exchangeConfigRepository: ExchangeConfigRepository
    private lateinit var barterManager: BarterManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = AppDatabase.getDatabase(context)
        walletRepository = WalletRepository(database.walletDao())
        exchangeConfigRepository = ExchangeConfigRepository(context)
        barterManager = BarterManager(walletRepository, exchangeConfigRepository)
    }

    @Test
    fun testLiveExchangeFlowOnDevice() = runBlocking {
        // Reset configuration to default: rate = 1.0, fee = 0%
        exchangeConfigRepository.updateExchangeConfig(
            ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)
        )

        // Set initial wallet: ₹50.0 rupees, 0 credits, 60 min screen time
        walletRepository.ensureWalletInitialized()
        val initialWallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(
            initialWallet.copy(
                rupeeBalance = 50.0,
                creditBalance = 0.0,
                screenTimeMinutes = 60
            )
        )

        // 1. RUPEES -> CREDITS (₹20 -> 20 credits, 0% fee)
        val r2cResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 20.0)
        assertTrue(r2cResult.isSuccess)
        val r2cSuccess = r2cResult.getOrNull()
        assertTrue(r2cSuccess is ExchangeResult.Success)
        val success1 = r2cSuccess as ExchangeResult.Success
        assertEquals(20.0, success1.inputAmount, 0.001)
        assertEquals(0.0, success1.feeAmount, 0.001)
        assertEquals(20.0, success1.netAmount, 0.001)

        val walletAfterR2C = walletRepository.getWalletOnce()!!
        assertEquals(30.0, walletAfterR2C.rupeeBalance, 0.001) // 50 - 20 = 30
        assertEquals(20.0, walletAfterR2C.creditBalance, 0.001) // 0 + 20 = 20

        // 2. CREDITS -> RUPEES (10 credits -> ₹10, 0% fee)
        val c2rResult = barterManager.executeExchange(ExchangeDirection.CREDITS_TO_RUPEES, 10.0)
        assertTrue(c2rResult.isSuccess)
        val c2rSuccess = c2rResult.getOrNull()
        assertTrue(c2rSuccess is ExchangeResult.Success)
        val success2 = c2rSuccess as ExchangeResult.Success
        assertEquals(10.0, success2.inputAmount, 0.001)
        assertEquals(0.0, success2.feeAmount, 0.001)
        assertEquals(10.0, success2.netAmount, 0.001)

        val walletAfterC2R = walletRepository.getWalletOnce()!!
        assertEquals(10.0, walletAfterC2R.creditBalance, 0.001) // 20 - 10 = 10
        assertEquals(40.0, walletAfterC2R.rupeeBalance, 0.001)  // 30 + 10 = 40

        // 3. FEE CALCULATION TEST (10% fee, ₹10 -> gross 10 credits, fee 1.0 credit, net 9.0 credits)
        exchangeConfigRepository.updateExchangeConfig(
            ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 10.0)
        )
        val feeResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 10.0)
        assertTrue(feeResult.isSuccess)
        val feeSuccess = feeResult.getOrNull() as ExchangeResult.Success
        assertEquals(10.0, feeSuccess.inputAmount, 0.001)
        assertEquals(1.0, feeSuccess.feeAmount, 0.001)
        assertEquals(9.0, feeSuccess.netAmount, 0.001)

        val walletAfterFee = walletRepository.getWalletOnce()!!
        assertEquals(30.0, walletAfterFee.rupeeBalance, 0.001) // 40 - 10 = 30
        assertEquals(19.0, walletAfterFee.creditBalance, 0.001) // 10 + 9 = 19

        // 4. INSUFFICIENT RUPEE BALANCE TEST (Attempt ₹100 with ₹30 balance)
        val insuffRupeeResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 100.0)
        assertTrue(insuffRupeeResult.isSuccess)
        assertTrue(insuffRupeeResult.getOrNull() is ExchangeResult.InsufficientBalance)
        val insuffRupee = insuffRupeeResult.getOrNull() as ExchangeResult.InsufficientBalance
        assertEquals(100.0, insuffRupee.requiredAmount, 0.001)
        assertEquals(30.0, insuffRupee.availableAmount, 0.001)

        // Wallet must not change
        val walletAfterInsuffRupee = walletRepository.getWalletOnce()!!
        assertEquals(30.0, walletAfterInsuffRupee.rupeeBalance, 0.001)
        assertEquals(19.0, walletAfterInsuffRupee.creditBalance, 0.001)

        // 5. INSUFFICIENT CREDIT BALANCE TEST (Attempt 50 credits with 19 credits balance)
        val insuffCreditResult = barterManager.executeExchange(ExchangeDirection.CREDITS_TO_RUPEES, 50.0)
        assertTrue(insuffCreditResult.isSuccess)
        assertTrue(insuffCreditResult.getOrNull() is ExchangeResult.InsufficientBalance)

        // Wallet must not change
        val walletAfterInsuffCredit = walletRepository.getWalletOnce()!!
        assertEquals(30.0, walletAfterInsuffCredit.rupeeBalance, 0.001)
        assertEquals(19.0, walletAfterInsuffCredit.creditBalance, 0.001)

        // 6. INVALID INPUT TEST (0 and negative)
        val zeroResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, 0.0)
        assertTrue(zeroResult.getOrNull() is ExchangeResult.InvalidAmount)

        val negResult = barterManager.executeExchange(ExchangeDirection.RUPEES_TO_CREDITS, -5.0)
        assertTrue(negResult.getOrNull() is ExchangeResult.InvalidAmount)

        // Reset config back to default for clean UI testing
        exchangeConfigRepository.updateExchangeConfig(
            ExchangeConfig(creditsPerRupee = 1.0, exchangeFeePercent = 0.0)
        )
    }
}
