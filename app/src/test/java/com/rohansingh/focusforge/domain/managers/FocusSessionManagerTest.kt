package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.FocusSessionDao
import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusSessionManagerTest {

    private lateinit var fakeFocusSessionDao: FakeFocusSessionDao
    private lateinit var fakeGoalTemplateDao: FakeGoalTemplateDao
    private lateinit var fakeGoalLogDao: FakeGoalLogDao
    private lateinit var fakeWalletDao: FakeWalletDao

    private lateinit var focusSessionRepository: FocusSessionRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var goalManager: GoalManager
    private lateinit var focusSessionManager: FocusSessionManager

    private val fakeAlarmScheduler = FakeAlarmScheduler()

    @Before
    fun setUp() {
        fakeFocusSessionDao = FakeFocusSessionDao()
        fakeGoalTemplateDao = FakeGoalTemplateDao()
        fakeGoalLogDao = FakeGoalLogDao()
        fakeWalletDao = FakeWalletDao()

        focusSessionRepository = FocusSessionRepository(fakeFocusSessionDao)
        goalRepository = GoalRepository(
            goalTemplateDao = fakeGoalTemplateDao,
            goalLogDao = fakeGoalLogDao,
            walletDao = fakeWalletDao
        )
        walletRepository = WalletRepository(fakeWalletDao)
        goalManager = GoalManager(goalRepository)

        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 0.0, rupeeBalance = 0.0, screenTimeMinutes = 30)

        focusSessionManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = fakeAlarmScheduler,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testGoalEligibility_timeBasedGoalsAccepted() {
        val hourGoal = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 10.0)
        val minGoal = GoalTemplate(id = 2, title = "Meditate", unit = "minutes", creditRate = 0.2)
        val pageGoal = GoalTemplate(id = 3, title = "Read", unit = "pages", creditRate = 0.5)
        val kmGoal = GoalTemplate(id = 4, title = "Run", unit = "km", creditRate = 2.0)

        assertTrue(focusSessionManager.isGoalFocusEligible(hourGoal))
        assertTrue(focusSessionManager.isGoalFocusEligible(minGoal))
        assertFalse(focusSessionManager.isGoalFocusEligible(pageGoal))
        assertFalse(focusSessionManager.isGoalFocusEligible(kmGoal))
    }

    @Test
    fun testStartSession_withNonTimeGoal_fails() = runBlocking {
        val nonTimeGoal = GoalTemplate(id = 1, title = "Read", unit = "pages", creditRate = 0.5)
        val result = focusSessionManager.startSession(nonTimeGoal, 30)

        assertTrue(result.isFailure)
        assertNull(focusSessionManager.activeSession.value)
    }

    @Test
    fun testStartSession_withValidGoal_persistsAndSchedulesAlarm() = runBlocking {
        val goal = GoalTemplate(id = 1, title = "Coding", unit = "hours", creditRate = 20.0)
        val result = focusSessionManager.startSession(goal, 45)

        assertTrue(result.isSuccess)
        val active = result.getOrNull()
        assertNotNull(active)
        assertEquals(45, active?.targetDurationMinutes)
        assertEquals(45 * 60, active?.remainingSeconds)
        assertEquals(FocusSessionStatus.RUNNING, active?.status)

        // Verify alarm scheduled
        assertEquals(1, fakeAlarmScheduler.scheduledAlarms.size)
        assertEquals(active?.sessionId, fakeAlarmScheduler.scheduledAlarms.keys.first())
    }

    @Test
    fun testStartSession_whenAlreadyRunning_rejectsSecondSession() = runBlocking {
        val goal1 = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 10.0)
        val goal2 = GoalTemplate(id = 2, title = "Meditate", unit = "minutes", creditRate = 0.5)

        val res1 = focusSessionManager.startSession(goal1, 30)
        assertTrue(res1.isSuccess)

        val res2 = focusSessionManager.startSession(goal2, 15)
        assertTrue(res2.isFailure)
        assertEquals("Study", focusSessionManager.activeSession.value?.goalTitle)
    }

    @Test
    fun testCompletion_hourGoal_converts45minsTo0point75hours() = runBlocking {
        val goal = GoalTemplate(id = 1, title = "Study Math", unit = "hours", creditRate = 20.0)
        val startResult = focusSessionManager.startSession(goal, 45)
        val sessionId = startResult.getOrThrow().sessionId

        val completeResult = focusSessionManager.handleSessionCompletion(sessionId)
        assertTrue(completeResult.isSuccess)

        // 45m = 0.75h * 20.0 credits/h = 15.0 credits
        val earned = completeResult.getOrThrow()
        assertEquals(15.0, earned, 0.001)
        assertEquals(15.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)

        // Verify session marked COMPLETED in repository
        val sessionEntity = fakeFocusSessionDao.getSessionById(sessionId)
        assertEquals("COMPLETED", sessionEntity?.status)
        assertNull(focusSessionManager.activeSession.value)
    }

    @Test
    fun testCompletion_minuteGoal_convertsExactMinutes() = runBlocking {
        val goal = GoalTemplate(id = 2, title = "Meditate", unit = "minutes", creditRate = 0.5)
        val startResult = focusSessionManager.startSession(goal, 20)
        val sessionId = startResult.getOrThrow().sessionId

        val completeResult = focusSessionManager.handleSessionCompletion(sessionId)
        assertTrue(completeResult.isSuccess)

        // 20m * 0.5 credits/m = 10.0 credits
        val earned = completeResult.getOrThrow()
        assertEquals(10.0, earned, 0.001)
        assertEquals(10.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)
    }

    @Test
    fun testCompletion_respectsDailyCap() = runBlocking {
        val goal = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 20.0, dailyCap = 10.0)
        val startResult = focusSessionManager.startSession(goal, 60)
        val sessionId = startResult.getOrThrow().sessionId

        val completeResult = focusSessionManager.handleSessionCompletion(sessionId)
        assertTrue(completeResult.isSuccess)

        // 60m = 1.0h * 20.0 = 20.0, capped at 10.0
        val earned = completeResult.getOrThrow()
        assertEquals(10.0, earned, 0.001)
        assertEquals(10.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)
    }

    @Test
    fun testIdempotentCompletion_preventsDoublePayout() = runBlocking {
        val goal = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 10.0)
        val startResult = focusSessionManager.startSession(goal, 60)
        val sessionId = startResult.getOrThrow().sessionId

        val firstCall = focusSessionManager.handleSessionCompletion(sessionId)
        assertEquals(10.0, firstCall.getOrThrow(), 0.001)
        assertEquals(10.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)

        val secondCall = focusSessionManager.handleSessionCompletion(sessionId)
        assertEquals(0.0, secondCall.getOrThrow(), 0.001)
        assertEquals(10.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)
    }

    @Test
    fun testRestoreSessionState_whenExpired_completesImmediately() = runBlocking {
        val now = System.currentTimeMillis()
        val expiredEntity = FocusSessionEntity(
            id = 10,
            goalId = 1,
            snapshotGoalTitle = "Deep Work",
            snapshotGoalUnit = "hours",
            snapshotCreditRate = 10.0,
            snapshotDailyCap = 0.0,
            targetDurationMinutes = 60,
            status = "RUNNING",
            startedAtWallClockMs = now - 70 * 60_000L,
            targetEndWallClockMs = now - 10 * 60_000L,
            startedAtElapsedRealtimeMs = 1000L
        )
        fakeFocusSessionDao.insertSession(expiredEntity)

        focusSessionManager.restoreSessionState()

        // Should have completed expired session and awarded 10.0 credits
        assertEquals(10.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)
        assertNull(focusSessionManager.activeSession.value)
    }

    @Test
    fun testNormalSession_persistsRealWallClockAndRealAlarm() = runBlocking {
        val goal = GoalTemplate(id = 1, title = "Deep Work", unit = "hours", creditRate = 20.0)
        val now = System.currentTimeMillis()
        val result = focusSessionManager.startSession(goal, 60)

        assertTrue(result.isSuccess)
        val active = result.getOrThrow()

        val entity = fakeFocusSessionDao.getSessionById(active.sessionId)
        assertNotNull(entity)
        assertEquals(60, entity?.targetDurationMinutes)

        // Real target wall clock must be now + 60 mins (NOT scaled)
        val expectedEndMs = entity!!.startedAtWallClockMs + 60 * 60_000L
        assertEquals(expectedEndMs, entity.targetEndWallClockMs)

        // Alarm must be scheduled with the real target wall clock
        assertEquals(expectedEndMs, fakeAlarmScheduler.scheduledAlarms[active.sessionId])
    }

    @Test
    fun testAcceleratedSession_simulatesElapsedCountdown_andAwardsFullCredits() = runBlocking {
        var simulatedWallClock = 1_000_000L
        val testTimeSource = object : com.rohansingh.focusforge.domain.time.FocusSessionTimeSource {
            override fun currentWallClockMs(): Long = simulatedWallClock
            override fun currentElapsedRealtimeMs(): Long = simulatedWallClock
            override val timeAccelerationFactor: Long = 60L
        }

        val acceleratedManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = fakeAlarmScheduler,
            externalScope = CoroutineScope(Dispatchers.Unconfined),
            timeSource = testTimeSource
        )

        val goal = GoalTemplate(id = 5, title = "Deep Work", unit = "hours", creditRate = 20.0)

        val startResult = acceleratedManager.startSession(goal, 60)
        assertTrue(startResult.isSuccess)

        val active = startResult.getOrThrow()
        assertEquals(60, active.targetDurationMinutes)

        // Persisted entity preserves TRUE real wall clock (simulatedWallClock + 60 * 60_000L)
        val entity = fakeFocusSessionDao.getSessionById(active.sessionId)
        assertNotNull(entity)
        assertEquals(simulatedWallClock + 60 * 60_000L, entity?.targetEndWallClockMs)

        // Advance simulated wall clock by 60 real seconds (which equals 60 simulated minutes at 60x)
        simulatedWallClock += 60_001L

        val completeResult = acceleratedManager.handleSessionCompletion(active.sessionId)
        assertTrue(completeResult.isSuccess)

        // Full 60 minutes = 1.0 hour * 20.0 = 20.0 credits awarded
        assertEquals(20.0, completeResult.getOrThrow(), 0.001)
        assertEquals(20.0, fakeWalletDao.wallet?.creditBalance ?: 0.0, 0.001)
    }

    private class FakeAlarmScheduler : FocusSessionAlarmScheduler {
        val scheduledAlarms = mutableMapOf<Long, Long>()

        override fun scheduleCompletionAlarm(sessionId: Long, triggerAtWallClockMs: Long) {
            scheduledAlarms[sessionId] = triggerAtWallClockMs
        }

        override fun cancelCompletionAlarm(sessionId: Long) {
            scheduledAlarms.remove(sessionId)
        }
    }

    private class FakeFocusSessionDao : FocusSessionDao {
        private val list = mutableListOf<FocusSessionEntity>()

        override fun getActiveSession(): Flow<FocusSessionEntity?> = flow {
            emit(list.find { it.status == "RUNNING" })
        }

        override suspend fun getActiveSessionOnce(): FocusSessionEntity? =
            list.find { it.status == "RUNNING" }

        override suspend fun getSessionById(id: Long): FocusSessionEntity? =
            list.find { it.id == id }

        override suspend fun getActiveSessionForGoal(goalId: Long): FocusSessionEntity? =
            list.find { it.goalId == goalId && it.status == "RUNNING" }

        override fun getAllSessions(): Flow<List<FocusSessionEntity>> = flow {
            emit(list.toList())
        }

        override suspend fun insertSession(session: FocusSessionEntity): Long {
            val id = if (session.id == 0L) (list.size + 1).toLong() else session.id
            val entity = session.copy(id = id)
            list.removeAll { it.id == id }
            list.add(entity)
            return id
        }

        override suspend fun updateSession(session: FocusSessionEntity): Int {
            list.removeAll { it.id == session.id }
            list.add(session)
            return 1
        }

        override suspend fun markSessionCompleted(id: Long, completedAt: Long): Int {
            val existing = list.find { it.id == id }
            if (existing != null) {
                list.removeAll { it.id == id }
                list.add(existing.copy(status = "COMPLETED", completedAtWallClockMs = completedAt))
                return 1
            }
            return 0
        }

        override fun getFocusSessionSummary(startTimeMs: Long, endTimeMs: Long): Flow<com.rohansingh.focusforge.data.dao.FocusSessionSummaryStat> = flow {
            val completed = list.filter { it.status == "COMPLETED" && it.startedAtWallClockMs in startTimeMs..endTimeMs }
            val totalMin = completed.sumOf { it.targetDurationMinutes }
            val avgMin = if (completed.isNotEmpty()) totalMin.toDouble() / completed.size else 0.0
            emit(com.rohansingh.focusforge.data.dao.FocusSessionSummaryStat(completed.size, totalMin, avgMin))
        }

        override fun getGoalFocusBreakdown(startTimeMs: Long, endTimeMs: Long): Flow<List<com.rohansingh.focusforge.data.dao.GoalFocusStat>> = flow {
            val completed = list.filter { it.status == "COMPLETED" && it.startedAtWallClockMs in startTimeMs..endTimeMs }
            val grouped = completed.groupBy { it.goalId }
            emit(grouped.map { (goalId, sessions) ->
                com.rohansingh.focusforge.data.dao.GoalFocusStat(goalId, sessions.first().snapshotGoalTitle, sessions.size, sessions.sumOf { it.targetDurationMinutes })
            })
        }

        override fun getDailyFocusTrend(startTimeMs: Long, endTimeMs: Long): Flow<List<com.rohansingh.focusforge.data.dao.DailyFocusStat>> = flow {
            emit(emptyList())
        }
    }

    private class FakeGoalTemplateDao : GoalTemplateDao {
        private val list = mutableListOf<GoalTemplate>()
        override fun getAllGoalTemplates(): Flow<List<GoalTemplate>> = flow { emit(list.toList()) }
        override suspend fun getAllGoalTemplatesList(): List<GoalTemplate> = list.toList()
        override suspend fun getGoalTemplateById(id: Long): GoalTemplate? = list.find { it.id == id }
        override suspend fun insertGoalTemplate(goal: GoalTemplate): Long {
            list.add(goal)
            return goal.id
        }
        override suspend fun updateGoalTemplate(goal: GoalTemplate): Int = 1
        override suspend fun deleteGoalTemplate(goal: GoalTemplate): Int = 1
        override suspend fun deleteGoalTemplateById(id: Long): Int {
            val removed = list.removeAll { it.id == id }
            return if (removed) 1 else 0
        }
    }

    private class FakeGoalLogDao : GoalLogDao {
        private val logs = mutableListOf<GoalLog>()
        override fun getLogsForGoal(goalTemplateId: Long): Flow<List<GoalLog>> = flow { emit(logs.filter { it.goalTemplateId == goalTemplateId }) }
        override fun getAllLogs(): Flow<List<GoalLog>> = flow { emit(logs.toList()) }
        override suspend fun getCreditsEarnedToday(goalTemplateId: Long, startOfDay: Long, endOfDay: Long): Double =
            logs.filter { it.goalTemplateId == goalTemplateId && it.completedAt in startOfDay..endOfDay }.sumOf { it.creditsEarned }
        override suspend fun insertGoalLog(log: GoalLog): Long {
            logs.add(log)
            return log.id
        }
        override fun getTotalCreditsEarned(startTimeMs: Long, endTimeMs: Long): Flow<Double> = flow {
            emit(logs.filter { it.completedAt in startTimeMs..endTimeMs }.sumOf { it.creditsEarned })
        }
        override fun getTotalCompletionsCount(startTimeMs: Long, endTimeMs: Long): Flow<Int> = flow {
            emit(logs.count { it.completedAt in startTimeMs..endTimeMs })
        }
        override fun getDailyCreditsEarned(startTimeMs: Long, endTimeMs: Long): Flow<List<com.rohansingh.focusforge.data.dao.DailyCreditsStat>> = flow {
            emit(emptyList())
        }
        override fun getGoalPerformanceStats(startTimeMs: Long, endTimeMs: Long): Flow<List<com.rohansingh.focusforge.data.dao.GoalPerformanceStat>> = flow {
            emit(emptyList())
        }
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
}
