package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenTimeManagerTest {

    private lateinit var fakeWalletDao: FakeWalletDao
    private lateinit var fakeRestrictedDao: FakeRestrictedAppDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var screenTimeManager: ScreenTimeManager

    private val intervalMs = 60_000L

    @Before
    fun setUp() {
        fakeWalletDao = FakeWalletDao()
        fakeRestrictedDao = FakeRestrictedAppDao()
        walletRepository = WalletRepository(fakeWalletDao)
        restrictedAppRepository = RestrictedAppRepository(fakeRestrictedDao)
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository,
            minuteIntervalMs = intervalMs
        )
    }

    @Test
    fun testUnrestrictedForegroundApp_noDeduction() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 10)
        // package is not restricted
        val status = screenTimeManager.processTick(
            currentPackage = "com.rohansingh.focusforge",
            isInteractive = true,
            currentTimeMs = 1000L
        )

        assertFalse(status.isRestricted)
        assertFalse(status.shouldBlock)
        assertEquals(0, status.minutesDeducted)
        assertEquals(10, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testRestrictedApp_withScreenTime_usageAllowed() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 10)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        val status = screenTimeManager.processTick(
            currentPackage = "com.android.chrome",
            isInteractive = true,
            currentTimeMs = 1000L
        )

        assertTrue(status.isRestricted)
        assertFalse(status.shouldBlock)
        assertEquals(10, status.remainingScreenTimeMinutes)
        assertEquals(0, status.minutesDeducted)
    }

    @Test
    fun testRestrictedApp_withZeroMinutes_blockedImmediately() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 0)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        val status = screenTimeManager.processTick(
            currentPackage = "com.android.chrome",
            isInteractive = true,
            currentTimeMs = 1000L
        )

        assertTrue(status.isRestricted)
        assertTrue(status.shouldBlock)
        assertEquals(0, status.remainingScreenTimeMinutes)
        assertEquals(0, status.minutesDeducted)
    }

    @Test
    fun testOneMinuteDeduction_deductsExactlyOneMinute() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 5)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        // First tick: starts session
        val s1 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(0, s1.minutesDeducted)
        assertEquals(5, s1.remainingScreenTimeMinutes)

        // Tick at 30s: no deduction yet
        time += 30_000L
        val s2 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(0, s2.minutesDeducted)
        assertEquals(5, s2.remainingScreenTimeMinutes)

        // Tick at 60s total: exactly 1 minute deducted!
        time += 30_000L
        val s3 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(1, s3.minutesDeducted)
        assertEquals(4, s3.remainingScreenTimeMinutes)
        assertEquals(4, fakeWalletDao.wallet?.screenTimeMinutes)
        assertFalse(s3.shouldBlock)
    }

    @Test
    fun testRepeatedPollingWithinSameMinute_onlyDeductsOnce() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 5)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Poll every 1 second for 65 seconds
        var totalDeductions = 0
        for (i in 1..65) {
            time += 1000L
            val s = screenTimeManager.processTick("com.android.chrome", true, time)
            totalDeductions += s.minutesDeducted
        }

        assertEquals(1, totalDeductions)
        assertEquals(4, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testTransitionBetweenRestrictedApps_preservesAccumulatedTime() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 5)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.google.android.calendar", "Calendar", true))

        var time = 100_000L
        // 30s in Chrome
        screenTimeManager.processTick("com.android.chrome", true, time)
        time += 30_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Switch to Calendar for 30s
        time += 30_000L
        val s = screenTimeManager.processTick("com.google.android.calendar", true, time)
        // 30s Chrome + 30s Calendar = 60s -> 1 minute deducted!
        assertEquals(1, s.minutesDeducted)
        assertEquals(4, s.remainingScreenTimeMinutes)
        assertEquals(4, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testRestrictedToUnrestrictedTransition_stopsDeductions() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 5)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)
        time += 30_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Switch to FocusForge (unrestricted) for 60 seconds
        for (i in 1..6) {
            time += 10_000L
            val s = screenTimeManager.processTick("com.rohansingh.focusforge", true, time)
            assertFalse(s.isRestricted)
            assertEquals(0, s.minutesDeducted)
        }

        // Wallet should still have 5 minutes
        assertEquals(5, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testScreenOff_noDeductionWhileNonInteractive() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 5)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Screen turns off for 5 minutes
        time += 300_000L
        val s = screenTimeManager.processTick(null, false, time)

        assertFalse(s.isInteractive)
        assertEquals(0, s.minutesDeducted)
        assertEquals(5, fakeWalletDao.wallet?.screenTimeMinutes)

        // Screen turns back on
        time += 1000L
        val s2 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertTrue(s2.isInteractive)
        assertEquals(0, s2.minutesDeducted)
        assertEquals(5, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testTimeNeverBecomesNegative() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 1)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 60s passes -> balance reaches 0 and triggers block
        time += 60_000L
        val s1 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(1, s1.minutesDeducted)
        assertEquals(0, s1.remainingScreenTimeMinutes)
        assertTrue(s1.shouldBlock)
        assertEquals(0, fakeWalletDao.wallet?.screenTimeMinutes)

        // Next tick when balance is 0
        time += 10_000L
        val s2 = screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(0, s2.minutesDeducted)
        assertEquals(0, s2.remainingScreenTimeMinutes)
        assertTrue(s2.shouldBlock)
        assertEquals(0, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    private class FakeWalletDao : WalletDao {
        var wallet: Wallet? = null

        override fun getWallet(): Flow<Wallet?> = flow { emit(wallet) }

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

    private class FakeRestrictedAppDao : RestrictedAppDao {
        private val list = mutableListOf<RestrictedApp>()

        override fun getAllRestrictedApps(): Flow<List<RestrictedApp>> = flow { emit(list.toList()) }
        override fun getActiveRestrictedApps(): Flow<List<RestrictedApp>> = flow {
            emit(list.filter { it.isRestricted })
        }
        override suspend fun getRestrictedApp(packageName: String): RestrictedApp? =
            list.find { it.packageName == packageName }
        override fun observeRestrictedApp(packageName: String): Flow<RestrictedApp?> = flow {
            emit(list.find { it.packageName == packageName })
        }
        override suspend fun insertOrUpdate(app: RestrictedApp): Long {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1L
        }
        override suspend fun insertAll(apps: List<RestrictedApp>): List<Long> {
            apps.forEach { insertOrUpdate(it) }
            return apps.indices.map { it.toLong() }
        }
        override suspend fun update(app: RestrictedApp): Int {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1
        }
        override suspend fun setRestrictedState(packageName: String, isRestricted: Boolean): Int {
            val app = list.find { it.packageName == packageName }
            if (app != null) {
                insertOrUpdate(app.copy(isRestricted = isRestricted))
                return 1
            }
            return 0
        }
        override suspend fun delete(app: RestrictedApp): Int {
            val removed = list.removeAll { it.packageName == app.packageName }
            return if (removed) 1 else 0
        }
        override suspend fun deleteByPackageName(packageName: String): Int {
            val removed = list.removeAll { it.packageName == packageName }
            return if (removed) 1 else 0
        }
    }
}
