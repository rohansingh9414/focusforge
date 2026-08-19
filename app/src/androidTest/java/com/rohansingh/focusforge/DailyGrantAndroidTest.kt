package com.rohansingh.focusforge

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.DailyGrantManager
import com.rohansingh.focusforge.domain.models.DailyGrantResult
import com.rohansingh.focusforge.services.daily.DailyGrantWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyGrantAndroidTest {

    private lateinit var database: AppDatabase
    private lateinit var walletRepository: WalletRepository
    private lateinit var dailyGrantManager: DailyGrantManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = AppDatabase.getDatabase(context)
        walletRepository = WalletRepository(database.walletDao())
        dailyGrantManager = DailyGrantManager(walletRepository)
    }

    @Test
    fun testFirstGrantAndIdempotencyOnDevice() = runBlocking {
        // Set initial wallet state on device
        walletRepository.ensureWalletInitialized()
        val initialWallet = walletRepository.getWalletOnce() ?: Wallet(id = 1)
        walletRepository.updateWallet(
            initialWallet.copy(
                rupeeBalance = 0.0,
                screenTimeMinutes = 0,
                lastDailyGrantDate = null
            )
        )

        val today = DailyGrantManager.getTodayDateString()

        // 1. FIRST GRANT
        val firstResult = dailyGrantManager.applyDailyGrant(today)
        assertTrue("First grant should succeed", firstResult.isSuccess)
        val firstGrant = firstResult.getOrNull()
        assertTrue("Result should be Applied", firstGrant is DailyGrantResult.Applied)

        val walletAfterFirst = walletRepository.getWalletOnce()!!
        assertEquals(50.0, walletAfterFirst.rupeeBalance, 0.001)
        assertEquals(60, walletAfterFirst.screenTimeMinutes)
        assertEquals(today, walletAfterFirst.lastDailyGrantDate)

        // 2. IDEMPOTENT SAME-DAY GRANT
        val secondResult = dailyGrantManager.applyDailyGrant(today)
        assertTrue("Second grant attempt should succeed", secondResult.isSuccess)
        val secondGrant = secondResult.getOrNull()
        assertTrue("Result should be AlreadyApplied", secondGrant is DailyGrantResult.AlreadyApplied)

        val walletAfterSecond = walletRepository.getWalletOnce()!!
        assertEquals(50.0, walletAfterSecond.rupeeBalance, 0.001)
        assertEquals(60, walletAfterSecond.screenTimeMinutes)
        assertEquals(today, walletAfterSecond.lastDailyGrantDate)

        // 3. NEXT-DATE GRANT
        val tomorrow = "2099-01-01"
        val nextResult = dailyGrantManager.applyDailyGrant(tomorrow)
        assertTrue("Next-date grant should succeed", nextResult.isSuccess)
        val nextGrant = nextResult.getOrNull()
        assertTrue("Result should be Applied", nextGrant is DailyGrantResult.Applied)

        val walletAfterNext = walletRepository.getWalletOnce()!!
        assertEquals(100.0, walletAfterNext.rupeeBalance, 0.001)
        assertEquals(120, walletAfterNext.screenTimeMinutes)
        assertEquals(tomorrow, walletAfterNext.lastDailyGrantDate)

        // Reset to today's grant for production UI verification
        walletRepository.updateWallet(
            walletAfterNext.copy(
                rupeeBalance = 50.0,
                screenTimeMinutes = 60,
                lastDailyGrantDate = today
            )
        )
    }

    @Test
    fun testDailyGrantWorkerExecution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<DailyGrantWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
