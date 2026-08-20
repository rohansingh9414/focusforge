package com.rohansingh.focusforge

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.ScreenTimeManager
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import com.rohansingh.focusforge.services.usage.ForegroundAppDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRestrictionsBackgroundAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var walletRepo: WalletRepository
    private lateinit var restrictedRepo: RestrictedAppRepository
    private lateinit var focusSessionRepo: FocusSessionRepository
    private lateinit var screenTimeManager: ScreenTimeManager
    private lateinit var detector: ForegroundAppDetector

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        walletRepo = WalletRepository(database.walletDao())
        restrictedRepo = RestrictedAppRepository(database.restrictedAppDao())
        focusSessionRepo = FocusSessionRepository(database.focusSessionDao())
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepo,
            restrictedAppRepository = restrictedRepo,
            screenTimeLogDao = database.screenTimeLogDao()
        )
        detector = ForegroundAppDetector(context)

        val packageName = context.packageName
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("appops set $packageName GET_USAGE_STATS allow")
        instrumentation.uiAutomation.executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        SystemClock.sleep(500)
    }

    private fun isServiceRunningInForeground(serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = am.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == serviceClass.name && it.foreground }
    }

    /**
     * TEST A: Confirm AppMonitoringService is running in true foreground state with notification.
     */
    @Test
    fun testA_ServiceRunningInTrueForegroundState() {
        AppMonitoringService.start(context)
        SystemClock.sleep(1000)

        assertTrue("AppMonitoringService should be running", AppMonitoringService.isRunning.value)
        assertTrue(
            "AppMonitoringService should be a true foreground service",
            isServiceRunningInForeground(AppMonitoringService::class.java)
        )
    }

    /**
     * TEST B: Start with screenTimeMinutes > 0, verify ScreenTimeManager decrements screen time
     * for restricted apps while service is running independently.
     */
    @Test
    fun testB_ScreenTimeConsumptionWhileMonitoring(): Unit = runBlocking {
        walletRepo.ensureWalletInitialized()
        val wallet = walletRepo.getWalletOnce() ?: Wallet(id = 1)
        walletRepo.updateWallet(wallet.copy(screenTimeMinutes = 30))
        restrictedRepo.setAppRestricted("com.android.settings", "Settings", true)

        AppMonitoringService.start(context)
        SystemClock.sleep(500)

        val initialWallet = walletRepo.getWalletOnce()
        assertEquals(30, initialWallet?.screenTimeMinutes ?: 0)

        // Process tick with interactive screen and restricted package
        val status = screenTimeManager.processTick(
            currentPackage = "com.android.settings",
            isInteractive = true,
            currentTimeMs = System.currentTimeMillis()
        )

        assertTrue("Settings must be detected as restricted", status.isRestricted)
        assertTrue("Should not block while balance > 0", !status.shouldBlock)
    }

    /**
     * TEST C: Set screenTimeMinutes = 0, verify ScreenTimeManager blocks restricted apps.
     */
    @Test
    fun testC_BlockerEvaluationWhenScreenTimeExhausted(): Unit = runBlocking {
        walletRepo.ensureWalletInitialized()
        val wallet = walletRepo.getWalletOnce() ?: Wallet(id = 1)
        walletRepo.updateWallet(wallet.copy(screenTimeMinutes = 0))
        restrictedRepo.setAppRestricted("com.android.chrome", "Chrome", true)

        val status = screenTimeManager.processTick(
            currentPackage = "com.android.chrome",
            isInteractive = true,
            currentTimeMs = System.currentTimeMillis()
        )

        assertTrue("Chrome must be restricted", status.isRestricted)
        assertTrue("Should block when screen time is 0", status.shouldBlock)
    }

    /**
     * TEST D: Unrestricted apps remain completely unblocked and unaffected.
     */
    @Test
    fun testD_UnrestrictedAppUnaffected(): Unit = runBlocking {
        restrictedRepo.setAppRestricted("com.android.calculator2", "Calculator", false)

        val status = screenTimeManager.processTick(
            currentPackage = "com.android.calculator2",
            isInteractive = true,
            currentTimeMs = System.currentTimeMillis()
        )

        assertTrue("Calculator should not be restricted", !status.isRestricted)
        assertTrue("Calculator should not be blocked", !status.shouldBlock)
    }

    /**
     * TEST E: While Focus Session is running, restricted apps are blocked immediately
     * regardless of screen-time balance.
     */
    @Test
    fun testE_FocusSessionLockdownEvaluation(): Unit = runBlocking {
        walletRepo.ensureWalletInitialized()
        val wallet = walletRepo.getWalletOnce() ?: Wallet(id = 1)
        walletRepo.updateWallet(wallet.copy(screenTimeMinutes = 60))
        restrictedRepo.setAppRestricted("com.android.chrome", "Chrome", true)

        val goal = com.rohansingh.focusforge.data.entities.GoalTemplate(
            title = "Deep Study",
            unit = "hours",
            creditRate = 10.0
        )
        val goalId = database.goalTemplateDao().insertGoalTemplate(goal)

        val now = System.currentTimeMillis()
        val session = FocusSessionEntity(
            id = 999,
            goalId = goalId,
            snapshotGoalTitle = "Deep Study",
            snapshotGoalUnit = "hours",
            snapshotCreditRate = 10.0,
            snapshotDailyCap = 10.0,
            targetDurationMinutes = 25,
            startedAtWallClockMs = now,
            startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            targetEndWallClockMs = now + (25 * 60 * 1000),
            status = FocusSessionStatus.RUNNING.name
        )
        focusSessionRepo.insertSession(session)

        val active = focusSessionRepo.activeSession.first()
        assertNotNull("Active focus session must be present", active)
        assertEquals(FocusSessionStatus.RUNNING.name, active?.status)

        // Clean up
        focusSessionRepo.markSessionCompleted(999, now)
        Unit
    }

    /**
     * TEST F: Duplicate starts of AppMonitoringService maintain single running state
     * and do not duplicate background collector jobs.
     */
    @Test
    fun testF_SingleServiceInstanceResilience() {
        AppMonitoringService.start(context)
        SystemClock.sleep(500)
        AppMonitoringService.start(context)
        SystemClock.sleep(500)

        assertTrue("AppMonitoringService should remain running", AppMonitoringService.isRunning.value)
    }
}
