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

class ScreenTimeManagerWarningTest {

    private lateinit var fakeWalletDao: FakeWalletDao
    private lateinit var fakeRestrictedAppDao: FakeRestrictedAppDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var screenTimeManager: ScreenTimeManager

    private val testPackage = "com.example.social"

    @Before
    fun setup() {
        fakeWalletDao = FakeWalletDao()
        fakeRestrictedAppDao = FakeRestrictedAppDao()
        walletRepository = WalletRepository(fakeWalletDao)
        restrictedAppRepository = RestrictedAppRepository(fakeRestrictedAppDao)
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository,
            minuteIntervalMs = 60_000L
        )

        fakeRestrictedAppDao.addRestrictedApp(
            RestrictedApp(packageName = testPackage, appName = "Social App", isRestricted = true)
        )
    }

    @Test
    fun `low screen time warning fires when balance falls to 15 minutes`() = runBlocking {
        // Start with 16 minutes
        fakeWalletDao.setWallet(Wallet(id = 1, screenTimeMinutes = 16))

        val t0 = 1000000L
        // t0: Start session
        var status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t0)
        assertFalse(status.shouldWarnLowScreenTime)
        assertEquals(16, status.remainingScreenTimeMinutes)

        // t1: 60s later -> 1 min deducted, balance becomes 15 -> should warn!
        val t1 = t0 + 60_000L
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t1)
        assertTrue("Expected low screen time warning at 15 minutes", status.shouldWarnLowScreenTime)
        assertEquals(15, status.remainingScreenTimeMinutes)

        // t2: 60s later -> 1 min deducted, balance becomes 14 -> should NOT warn again (anti-spam)
        val t2 = t1 + 60_000L
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t2)
        assertFalse("Expected no duplicate warning at 14 minutes", status.shouldWarnLowScreenTime)
        assertEquals(14, status.remainingScreenTimeMinutes)
    }

    @Test
    fun `low screen time warning re-arms after replenishing balance above threshold`() = runBlocking {
        // Start at 15 minutes -> initial warning
        fakeWalletDao.setWallet(Wallet(id = 1, screenTimeMinutes = 15))
        val t0 = 1000000L
        var status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t0)
        assertTrue(status.shouldWarnLowScreenTime)

        // Subsequent tick at 14 min -> no warning
        val t1 = t0 + 60_000L
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t1)
        assertFalse(status.shouldWarnLowScreenTime)

        // Replenish wallet to 30 minutes (e.g., reward redemption or daily grant)
        fakeWalletDao.setWallet(Wallet(id = 1, screenTimeMinutes = 30))

        val t2 = t1 + 60_000L
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t2)
        assertFalse(status.shouldWarnLowScreenTime)
        assertEquals(29, status.remainingScreenTimeMinutes)

        // Set wallet to 16 minutes at current tick
        fakeWalletDao.setWallet(Wallet(id = 1, screenTimeMinutes = 16))
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t2)
        assertFalse(status.shouldWarnLowScreenTime)
        assertEquals(16, status.remainingScreenTimeMinutes)

        // Deduct 1 minute to 15 -> warning should re-trigger!
        val t3 = t2 + 60_000L
        status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = t3)
        assertTrue("Warning should re-trigger after crossing 15 again", status.shouldWarnLowScreenTime)
        assertEquals(15, status.remainingScreenTimeMinutes)
    }

    @Test
    fun `zero balance triggers low warning if not previously warned`() = runBlocking {
        fakeWalletDao.setWallet(Wallet(id = 1, screenTimeMinutes = 0))

        val status = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = 1000000L)
        assertTrue(status.shouldBlock)
        assertTrue(status.shouldWarnLowScreenTime)

        // Subsequent tick at 0 min -> should NOT warn again
        val status2 = screenTimeManager.processTick(testPackage, isInteractive = true, currentTimeMs = 1001000L)
        assertTrue(status2.shouldBlock)
        assertFalse(status2.shouldWarnLowScreenTime)
    }

    private class FakeWalletDao : WalletDao {
        private var wallet: Wallet? = null
        fun setWallet(w: Wallet) { wallet = w }
        override fun getWallet(): Flow<Wallet?> = flow { emit(wallet) }
        override suspend fun getWalletOnce(): Wallet? = wallet
        override suspend fun insertWallet(w: Wallet): Long { wallet = w; return 1L }
        override suspend fun updateWallet(w: Wallet): Int { wallet = w; return 1 }
    }

    private class FakeRestrictedAppDao : RestrictedAppDao {
        private val list = mutableListOf<RestrictedApp>()
        fun addRestrictedApp(app: RestrictedApp) { list.add(app) }
        override fun getAllRestrictedApps(): Flow<List<RestrictedApp>> = flow { emit(list) }
        override fun getActiveRestrictedApps(): Flow<List<RestrictedApp>> = flow { emit(list.filter { it.isRestricted }) }
        override suspend fun getRestrictedApp(packageName: String): RestrictedApp? = list.find { it.packageName == packageName }
        override fun observeRestrictedApp(packageName: String): Flow<RestrictedApp?> = flow { emit(list.find { it.packageName == packageName }) }
        override suspend fun insertOrUpdate(app: RestrictedApp): Long {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1L
        }
        override suspend fun insertAll(apps: List<RestrictedApp>): List<Long> {
            apps.forEach { insertOrUpdate(it) }
            return apps.map { 1L }
        }
        override suspend fun update(app: RestrictedApp): Int {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1
        }
        override suspend fun setRestrictedState(packageName: String, isRestricted: Boolean): Int {
            val app = list.find { it.packageName == packageName }
            if (app != null) {
                list.remove(app)
                list.add(app.copy(isRestricted = isRestricted))
                return 1
            }
            return 0
        }
        override suspend fun delete(app: RestrictedApp): Int {
            return if (list.remove(app)) 1 else 0
        }
        override suspend fun deleteByPackageName(packageName: String): Int {
            val removed = list.removeAll { it.packageName == packageName }
            return if (removed) 1 else 0
        }
    }
}
