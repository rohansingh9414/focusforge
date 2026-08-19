package com.rohansingh.focusforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.ScreenTimeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTimeEngineAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var walletRepository: WalletRepository
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var screenTimeManager: ScreenTimeManager

    private val testIntervalMs = 1000L // 1-second interval for fast testing

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        walletRepository = WalletRepository(database.walletDao())
        restrictedAppRepository = RestrictedAppRepository(database.restrictedAppDao())

        walletRepository.ensureWalletInitialized()
        val wallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(wallet.copy(screenTimeMinutes = 5))

        restrictedAppRepository.setAppRestricted("com.android.chrome", "Chrome", true)

        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository,
            minuteIntervalMs = testIntervalMs
        )
    }

    @Test
    fun prepareTestState_2minutes() = runBlocking {
        val wallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(wallet.copy(screenTimeMinutes = 2))
        restrictedAppRepository.setAppRestricted("com.android.chrome", "Chrome", true)
        restrictedAppRepository.setAppRestricted("com.google.android.calendar", "Calendar", true)
    }

    @Test
    fun prepareTestState_0minutes() = runBlocking {
        val wallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(wallet.copy(screenTimeMinutes = 0))
        restrictedAppRepository.setAppRestricted("com.android.chrome", "Chrome", true)
        restrictedAppRepository.setAppRestricted("com.google.android.calendar", "Calendar", true)
    }

    @Test
    fun testLiveDeductionInDatabase() = runBlocking {
        var time = 10_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Elapsed 1 interval
        time += testIntervalMs
        val status = screenTimeManager.processTick("com.android.chrome", true, time)

        assertEquals(1, status.minutesDeducted)
        assertEquals(4, status.remainingScreenTimeMinutes)
        assertFalse(status.shouldBlock)

        // Verify direct from database
        val dbWallet = walletRepository.getWalletOnce()
        assertEquals(4, dbWallet?.screenTimeMinutes)
    }

    @Test
    fun testLiveZeroBalanceBlockingInDatabase() = runBlocking {
        val wallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(wallet.copy(screenTimeMinutes = 0))

        val status = screenTimeManager.processTick("com.android.chrome", true, 1000L)
        assertTrue(status.isRestricted)
        assertTrue(status.shouldBlock)
        assertEquals(0, status.remainingScreenTimeMinutes)
    }

    @Test
    fun testUnrestrictedAppCausesNoDatabaseDeduction() = runBlocking {
        val initialWallet = walletRepository.getWalletOnce()
        val initialMinutes = initialWallet?.screenTimeMinutes ?: 5

        var time = 10_000L
        screenTimeManager.processTick("com.rohansingh.focusforge", true, time)

        time += testIntervalMs * 3
        val status = screenTimeManager.processTick("com.rohansingh.focusforge", true, time)

        assertFalse(status.isRestricted)
        assertFalse(status.shouldBlock)
        assertEquals(0, status.minutesDeducted)

        val finalWallet = walletRepository.getWalletOnce()
        assertEquals(initialMinutes, finalWallet?.screenTimeMinutes)
    }
}
