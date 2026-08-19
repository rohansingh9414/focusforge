package com.rohansingh.focusforge

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.FocusSessionManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import com.rohansingh.focusforge.services.alarm.AndroidFocusSessionAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusSessionEngineAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var focusSessionRepository: FocusSessionRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var goalManager: GoalManager
    private lateinit var focusSessionManager: FocusSessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)

        focusSessionRepository = FocusSessionRepository(database.focusSessionDao())
        goalRepository = GoalRepository(database.goalTemplateDao(), database.goalLogDao())
        walletRepository = WalletRepository(database.walletDao())
        restrictedAppRepository = RestrictedAppRepository(database.restrictedAppDao())
        goalManager = GoalManager(goalRepository, walletRepository)

        val alarmScheduler = AndroidFocusSessionAlarmScheduler(context)
        focusSessionManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = alarmScheduler
        )

        val packageName = context.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $packageName GET_USAGE_STATS allow")
        SystemClock.sleep(500)
    }

    @Test
    fun testFocusSessionPersistence_lifecycle(): Unit = runBlocking {
        // Create a test goal
        val goal = GoalTemplate(title = "Instrumented Study", unit = "hours", creditRate = 12.0)
        val goalId = goalRepository.insertGoalTemplate(goal)
        val savedGoal = goalRepository.getGoalTemplateById(goalId)!!

        // Start session
        val startResult = focusSessionManager.startSession(savedGoal, 30)
        assertTrue("Session should start successfully", startResult.isSuccess)
        val active = startResult.getOrThrow()

        // Verify active query in Room
        val persistedActive = focusSessionRepository.getActiveSessionOnce()
        assertNotNull("Persisted session must be present", persistedActive)
        assertEquals("RUNNING", persistedActive?.status)
        assertEquals("Instrumented Study", persistedActive?.snapshotGoalTitle)

        // Complete session
        val completeResult = focusSessionManager.handleSessionCompletion(active.sessionId)
        assertTrue("Session should complete successfully", completeResult.isSuccess)
        assertEquals(6.0, completeResult.getOrThrow(), 0.001) // 30m = 0.5h * 12.0 = 6.0

        // Verify active session cleared
        val postActive = focusSessionRepository.getActiveSessionOnce()
        assertNull("No active session should remain", postActive)
    }

    @Test
    fun testNormalFocusSession_persistsRealWallClock(): Unit = runBlocking {
        val goal = GoalTemplate(title = "Normal Real Time Goal", unit = "hours", creditRate = 20.0)
        val goalId = goalRepository.insertGoalTemplate(goal)
        val savedGoal = goalRepository.getGoalTemplateById(goalId)!!

        val startResult = focusSessionManager.startSession(savedGoal, 60)
        assertTrue(startResult.isSuccess)
        val active = startResult.getOrThrow()

        val entity = focusSessionRepository.getActiveSessionOnce()
        assertNotNull(entity)
        assertEquals(60, entity?.targetDurationMinutes)

        // Must be exactly startedAt + 60 mins (3,600,000 ms) in real time
        val expectedEndMs = entity!!.startedAtWallClockMs + (60 * 60_000L)
        assertEquals(expectedEndMs, entity.targetEndWallClockMs)

        val completeResult = focusSessionManager.handleSessionCompletion(active.sessionId)
        assertTrue(completeResult.isSuccess)
    }

    @Test
    fun testAcceleratedFocusSession_onAndroidEmulator(): Unit = runBlocking {
        val testTimeSource = com.rohansingh.focusforge.domain.time.TestAcceleratedFocusSessionTimeSource(factor = 60L)
        val alarmScheduler = AndroidFocusSessionAlarmScheduler(context)
        val acceleratedManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = alarmScheduler,
            timeSource = testTimeSource
        )

        val goal = GoalTemplate(title = "Accelerated Deep Work", unit = "hours", creditRate = 24.0)
        val goalId = goalRepository.insertGoalTemplate(goal)
        val savedGoal = goalRepository.getGoalTemplateById(goalId)!!

        val startResult = acceleratedManager.startSession(savedGoal, 60)
        assertTrue(startResult.isSuccess)
        val active = startResult.getOrThrow()

        // 60 minutes session preserves TRUE real wall clock timestamp (startedAt + 60 * 60_000L)
        val entity = focusSessionRepository.getActiveSessionOnce()
        assertNotNull(entity)
        assertEquals(60, entity?.targetDurationMinutes)
        val expectedRealEnd = entity!!.startedAtWallClockMs + (60 * 60_000L)
        assertEquals(expectedRealEnd, entity.targetEndWallClockMs)

        // Complete the session
        val completeResult = acceleratedManager.handleSessionCompletion(active.sessionId)
        assertTrue(completeResult.isSuccess)
        assertEquals(24.0, completeResult.getOrThrow(), 0.001) // 1.0 hour * 24.0 = 24.0 credits
    }

    @Test
    fun seedFocusSessionTestData(): Unit = runBlocking {
        walletRepository.updateWallet(Wallet(id = 1, creditBalance = 0.0, rupeeBalance = 0.0, screenTimeMinutes = 10, lastDailyGrantDate = "2026-08-19"))
        goalRepository.insertGoalTemplate(GoalTemplate(title = "Deep Work", unit = "hours", creditRate = 20.0, recurring = true))
        goalRepository.insertGoalTemplate(GoalTemplate(title = "Read Book", unit = "pages", creditRate = 0.5, recurring = true))
        restrictedAppRepository.setAppRestricted("com.android.chrome", "Chrome", true)
    }
}
