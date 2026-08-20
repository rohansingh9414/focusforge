package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.AppScreenTimeUsageStat
import com.rohansingh.focusforge.data.dao.DailyScreenTimeStat
import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.dao.ScreenTimeLogDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.ScreenTimeLog
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenTimeManagerLoggingTest {

    private lateinit var fakeWalletDao: FakeWalletDao
    private lateinit var fakeRestrictedDao: FakeRestrictedAppDao
    private lateinit var fakeScreenTimeLogDao: FakeScreenTimeLogDao
    private lateinit var walletRepository: WalletRepository
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var screenTimeManager: ScreenTimeManager

    private val intervalMs = 60_000L

    @Before
    fun setUp() {
        fakeWalletDao = FakeWalletDao()
        fakeRestrictedDao = FakeRestrictedAppDao()
        fakeScreenTimeLogDao = FakeScreenTimeLogDao()
        walletRepository = WalletRepository(fakeWalletDao)
        restrictedAppRepository = RestrictedAppRepository(fakeRestrictedDao)
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository,
            screenTimeLogDao = fakeScreenTimeLogDao,
            minuteIntervalMs = intervalMs
        )
    }

    @Test
    fun testSingleSession_multipleMinutesDeducted_createsExactlyOneLogAtSessionEnd() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 10)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        // Session start
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 60s -> 1st min deducted
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(0, fakeScreenTimeLogDao.logs.size) // Still running, 0 logs written yet


        // 120s -> 2nd min deducted
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 180s -> 3rd min deducted
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)
        assertEquals(0, fakeScreenTimeLogDao.logs.size) // No row written during active session

        // 195s -> App backgrounded / user leaves Chrome
        time += 15_000L
        screenTimeManager.processTick("com.rohansingh.focusforge", true, time)

        // Exactly ONE ScreenTimeLog row must be produced with 3 minutes
        assertEquals(1, fakeScreenTimeLogDao.logs.size)
        val log = fakeScreenTimeLogDao.logs.first()
        assertEquals("com.android.chrome", log.packageName)
        assertEquals("Chrome", log.appName)
        assertEquals(3, log.minutesConsumed)
        assertEquals(time, log.consumedAt)
        assertEquals(7, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testPartialMinute_lessThanOneMinute_noLogCreated() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 10)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 45s passed, 0 full minutes deducted
        time += 45_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // User switches away
        time += 5_000L
        screenTimeManager.processTick(null, false, time)

        // Zero rows written because 0 whole minutes were deducted from wallet
        assertEquals(0, fakeScreenTimeLogDao.logs.size)
        assertEquals(10, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testSwitchedRestrictedApps_createsSeparateLogsPerSession() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 10)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.google.android.youtube", "YouTube", true))

        var time = 100_000L
        // Chrome for 120s (2 min)
        screenTimeManager.processTick("com.android.chrome", true, time)
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // Switch directly to YouTube
        time += 1000L
        screenTimeManager.processTick("com.google.android.youtube", true, time)
        // Chrome session should have flushed
        assertEquals(1, fakeScreenTimeLogDao.logs.size)
        assertEquals("com.android.chrome", fakeScreenTimeLogDao.logs[0].packageName)
        assertEquals(2, fakeScreenTimeLogDao.logs[0].minutesConsumed)

        // YouTube for 60s (1 min)
        time += 60_000L
        screenTimeManager.processTick("com.google.android.youtube", true, time)

        // Switch to FocusForge
        time += 1000L
        screenTimeManager.processTick("com.rohansingh.focusforge", true, time)

        // Now both sessions are logged
        assertEquals(2, fakeScreenTimeLogDao.logs.size)
        assertEquals("com.google.android.youtube", fakeScreenTimeLogDao.logs[1].packageName)
        assertEquals("YouTube", fakeScreenTimeLogDao.logs[1].appName)
        assertEquals(1, fakeScreenTimeLogDao.logs[1].minutesConsumed)
        assertEquals(7, fakeWalletDao.wallet?.screenTimeMinutes)
    }

    @Test
    fun testAppBlockedAtZeroBalance_flushesSessionImmediately() = runBlocking {
        fakeWalletDao.wallet = Wallet(id = 1, screenTimeMinutes = 1)
        fakeRestrictedDao.insertOrUpdate(RestrictedApp("com.android.chrome", "Chrome", true))

        var time = 100_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 60s passes -> wallet reaches 0 -> shouldBlock is true and session is flushed immediately
        time += 60_000L
        val status = screenTimeManager.processTick("com.android.chrome", true, time)
        assertTrue(status.shouldBlock)
        assertEquals(1, fakeScreenTimeLogDao.logs.size)
        assertEquals(1, fakeScreenTimeLogDao.logs[0].minutesConsumed)
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

    private class FakeScreenTimeLogDao : ScreenTimeLogDao {
        val logs = mutableListOf<ScreenTimeLog>()

        override suspend fun insertLog(log: ScreenTimeLog): Long {
            logs.add(log)
            return logs.size.toLong()
        }

        override fun getTotalScreenTimeConsumed(startTimeMs: Long, endTimeMs: Long): Flow<Int> = flowOf(
            logs.filter { it.consumedAt in startTimeMs..endTimeMs }.sumOf { it.minutesConsumed }
        )

        override fun getDailyScreenTimeUsage(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyScreenTimeStat>> = flowOf(
            emptyList()
        )

        override fun getAppScreenTimeUsage(startTimeMs: Long, endTimeMs: Long): Flow<List<AppScreenTimeUsageStat>> = flowOf(
            emptyList()
        )

        override fun getAllLogs(): Flow<List<ScreenTimeLog>> = flowOf(logs)
    }
}
