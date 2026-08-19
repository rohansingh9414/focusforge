package com.rohansingh.focusforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.FocusSessionManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import com.rohansingh.focusforge.services.alarm.AndroidFocusSessionAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamificationEngineAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var goalRepository: GoalRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var focusSessionRepository: FocusSessionRepository
    private lateinit var goalManager: GoalManager
    private lateinit var focusSessionManager: FocusSessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)

        goalRepository = GoalRepository(
            database = database,
            goalTemplateDao = database.goalTemplateDao(),
            goalLogDao = database.goalLogDao(),
            goalStreakDao = database.goalStreakDao(),
            xpLogDao = database.xpLogDao(),
            walletDao = database.walletDao()
        )
        walletRepository = WalletRepository(database.walletDao())
        focusSessionRepository = FocusSessionRepository(database.focusSessionDao())
        goalManager = GoalManager(goalRepository)

        val alarmScheduler = AndroidFocusSessionAlarmScheduler(context)
        focusSessionManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = alarmScheduler
        )

        runBlocking {
            database.clearAllTables()
            walletRepository.ensureWalletInitialized()
            val wallet = walletRepository.getWalletOnce() ?: Wallet()
            walletRepository.updateWallet(wallet.copy(creditBalance = 0.0, totalXp = 0L))
        }
    }

    @Test
    fun testAtomicGoalCompletion_updatesWalletXpAndStreak() = runBlocking {
        val goal = GoalTemplate(
            title = "Gamified Study",
            unit = "hours",
            creditRate = 10.0,
            dailyCap = 0.0,
            recurring = true
        )
        val goalId = goalRepository.insertGoalTemplate(goal)
        val insertedGoal = goalRepository.getGoalTemplateById(goalId)!!

        val result = goalManager.completeGoal(insertedGoal, 1.5) // 1.5 hr * 10.0 = 15.0 credits, +150 XP
        assertTrue(result.isSuccess)
        assertEquals(15.0, result.getOrNull()!!, 0.001)

        // Verify Wallet updated atomically in Room
        val wallet = walletRepository.getWalletOnce()
        assertNotNull(wallet)
        assertEquals(15.0, wallet!!.creditBalance, 0.001)
        assertEquals(150L, wallet.totalXp)

        // Verify XpLog in Room
        val xpLogs = database.xpLogDao().getXpLogsForGoal(goalId).first()
        assertEquals(1, xpLogs.size)
        assertEquals(150L, xpLogs[0].xpEarned)

        // Verify GoalStreak in Room
        val streak = database.goalStreakDao().getStreakForGoalOnce(goalId)
        assertNotNull(streak)
        assertEquals(1, streak!!.currentStreak)
        assertEquals(1, streak.longestStreak)
    }

    @Test
    fun testFocusSession_completesAndAwardsXpViaSamePath() = runBlocking {
        val goal = GoalTemplate(
            title = "Gamified Coding",
            unit = "hours",
            creditRate = 20.0,
            dailyCap = 0.0,
            recurring = true
        )
        val goalId = goalRepository.insertGoalTemplate(goal)
        val insertedGoal = goalRepository.getGoalTemplateById(goalId)!!

        val startResult = focusSessionManager.startSession(insertedGoal, 60)
        assertTrue(startResult.isSuccess)

        val active = focusSessionManager.activeSession.value
        assertNotNull(active)

        // Complete session
        val completeResult = focusSessionManager.handleSessionCompletion(active!!.sessionId)
        assertTrue(completeResult.isSuccess)
        assertEquals(20.0, completeResult.getOrNull()!!, 0.001)

        // Verify session marked completed
        val sessionEntity = focusSessionRepository.getSessionById(active.sessionId)
        assertNotNull(sessionEntity)
        assertEquals(FocusSessionStatus.COMPLETED.name, sessionEntity!!.status)

        // Verify 20.0 credits and 200 XP awarded to Wallet via unified GoalManager path
        val wallet = walletRepository.getWalletOnce()
        assertNotNull(wallet)
        assertTrue(wallet!!.creditBalance >= 20.0)
        assertTrue(wallet.totalXp >= 200L)
    }
}
