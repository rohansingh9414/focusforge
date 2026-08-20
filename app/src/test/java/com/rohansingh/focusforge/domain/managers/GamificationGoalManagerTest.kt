package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalStreakDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.dao.XpLogDao
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.entities.XpLog
import com.rohansingh.focusforge.data.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class GamificationGoalManagerTest {

    private lateinit var fakeGoalTemplateDao: FakeGoalTemplateDao
    private lateinit var fakeGoalLogDao: FakeGoalLogDao
    private lateinit var fakeGoalStreakDao: FakeGoalStreakDao
    private lateinit var fakeXpLogDao: FakeXpLogDao
    private lateinit var fakeWalletDao: FakeWalletDao

    private lateinit var goalRepository: GoalRepository

    private var simulatedCalendar: Calendar = Calendar.getInstance()

    @Before
    fun setUp() {
        fakeGoalTemplateDao = FakeGoalTemplateDao()
        fakeGoalLogDao = FakeGoalLogDao()
        fakeGoalStreakDao = FakeGoalStreakDao()
        fakeXpLogDao = FakeXpLogDao()
        fakeWalletDao = FakeWalletDao()

        fakeWalletDao.wallet = Wallet(id = 1, creditBalance = 0.0, rupeeBalance = 0.0, screenTimeMinutes = 0, totalXp = 0L)

        goalRepository = GoalRepository(
            database = null,
            goalTemplateDao = fakeGoalTemplateDao,
            goalLogDao = fakeGoalLogDao,
            goalStreakDao = fakeGoalStreakDao,
            xpLogDao = fakeXpLogDao,
            walletDao = fakeWalletDao
        )

        simulatedCalendar = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        }
    }

    private fun createGoalManager(): GoalManager {
        return GoalManager(
            goalRepository = goalRepository,
            calendarProvider = { simulatedCalendar.clone() as Calendar }
        )
    }

    @Test
    fun testFirstDayCompletion_startsStreakAt1_noBonus_awardsXp() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Read", unit = "pages", creditRate = 2.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        val result = manager.completeGoal(goal, 5.0) // 5 pages * 2.0 = 10.0 credits

        assertTrue(result.isSuccess)
        assertEquals(10.0, result.getOrNull()!!, 0.001)

        // Wallet
        val wallet = fakeWalletDao.getWalletOnce()
        assertNotNull(wallet)
        assertEquals(10.0, wallet!!.creditBalance, 0.001)
        assertEquals(100L, wallet.totalXp) // 10.0 credits * 10 = 100 XP

        // Streak
        val streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertNotNull(streak)
        assertEquals(1, streak!!.currentStreak)
        assertEquals(1, streak.longestStreak)
        assertEquals("2026-08-20", streak.lastCompletedDate)

        // XpLog
        assertEquals(1, fakeXpLogDao.logs.size)
        assertEquals(100L, fakeXpLogDao.logs[0].xpEarned)
    }

    @Test
    fun testConsecutiveDayCompletion_incrementsStreak_appliesBonus() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 10.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        // Day 1: Aug 20
        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        manager.completeGoal(goal, 1.0) // 1 hr * 10.0 = 10.0 credits, +100 XP, streak = 1

        var streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertEquals(1, streak!!.currentStreak)

        // Day 2: Aug 21
        simulatedCalendar.set(2026, Calendar.AUGUST, 21, 10, 0, 0)
        val result = manager.completeGoal(goal, 1.0) // 1 hr * (10.0 * 1.02) = 10.2 credits

        assertTrue(result.isSuccess)
        assertEquals(10.2, result.getOrNull()!!, 0.001)

        val wallet = fakeWalletDao.getWalletOnce()
        assertEquals(20.2, wallet!!.creditBalance, 0.001)
        assertEquals(202L, wallet.totalXp) // 100 + round(10.2 * 10) = 100 + 102 = 202 XP

        streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertEquals(2, streak!!.currentStreak)
        assertEquals(2, streak.longestStreak)
        assertEquals("2026-08-21", streak.lastCompletedDate)
    }

    @Test
    fun testSameDayDuplicateCompletion_preservesStreak_doesNotIncrementAgain() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Study", unit = "hours", creditRate = 10.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        // Day 1: Aug 20 first completion
        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        manager.completeGoal(goal, 1.0)

        // Day 1: Aug 20 second completion (same day)
        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 15, 0, 0)
        val result = manager.completeGoal(goal, 1.0)

        assertTrue(result.isSuccess)
        val streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertEquals(1, streak!!.currentStreak) // Remains 1, not 2!
        assertEquals("2026-08-20", streak.lastCompletedDate)
    }

    @Test
    fun testMissedDay_breaksStreak_resetsToOne() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Exercise", unit = "minutes", creditRate = 0.5)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        // Day 1: Aug 20
        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        manager.completeGoal(goal, 20.0) // streak = 1

        // Skip Aug 21 -> Jump to Aug 22 (missed day)
        simulatedCalendar.set(2026, Calendar.AUGUST, 22, 10, 0, 0)
        val result = manager.completeGoal(goal, 20.0) // 20 * 0.5 = 10.0 (no streak bonus because broken)

        assertTrue(result.isSuccess)
        assertEquals(10.0, result.getOrNull()!!, 0.001)

        val streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertEquals(1, streak!!.currentStreak) // Reset to 1
        assertEquals(1, streak.longestStreak)
        assertEquals("2026-08-22", streak.lastCompletedDate)
    }

    @Test
    fun testMaxStreakBonus_cappedAt20Percent() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Code", unit = "hours", creditRate = 10.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        // Pre-seed a 15-day streak completed yesterday (Aug 19)
        fakeGoalStreakDao.insertOrUpdate(
            GoalStreak(
                goalTemplateId = 1,
                currentStreak = 15,
                longestStreak = 15,
                lastCompletedDate = "2026-08-19"
            )
        )

        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        val result = manager.completeGoal(goal, 1.0) // 1 hr * (10.0 * (1.0 + 0.20)) = 12.0 credits

        assertTrue(result.isSuccess)
        assertEquals(12.0, result.getOrNull()!!, 0.001) // Max +20% bonus, not +30%

        val streak = fakeGoalStreakDao.getStreakForGoalOnce(1)
        assertEquals(16, streak!!.currentStreak)
        assertEquals(16, streak.longestStreak)
    }

    @Test
    fun testDailyCap_capsCredits_andXpCalculatedFromFinalCredits() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Walk", unit = "km", creditRate = 5.0, dailyCap = 8.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        val result = manager.completeGoal(goal, 2.0) // 2 * 5.0 = 10.0 potential, capped at 8.0

        assertTrue(result.isSuccess)
        assertEquals(8.0, result.getOrNull()!!, 0.001)

        val wallet = fakeWalletDao.getWalletOnce()
        assertEquals(8.0, wallet!!.creditBalance, 0.001)
        assertEquals(80L, wallet.totalXp) // round(8.0 * 10) = 80 XP, NOT 100 XP!
    }

    @Test
    fun testZeroCreditsEarned_whenDailyCapExhausted_awardsZeroXp() = runBlocking {
        val manager = createGoalManager()
        val goal = GoalTemplate(id = 1, title = "Walk", unit = "km", creditRate = 5.0, dailyCap = 5.0)
        fakeGoalTemplateDao.insertGoalTemplate(goal)

        simulatedCalendar.set(2026, Calendar.AUGUST, 20, 10, 0, 0)
        // First completion reaches daily cap
        manager.completeGoal(goal, 1.0) // 5.0 credits, +50 XP

        // Second completion on same day has 0 remaining cap
        val result = manager.completeGoal(goal, 1.0)

        assertTrue(result.isSuccess)
        assertEquals(0.0, result.getOrNull()!!, 0.001)

        val wallet = fakeWalletDao.getWalletOnce()
        assertEquals(5.0, wallet!!.creditBalance, 0.001)
        assertEquals(50L, wallet.totalXp) // No extra XP awarded
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

    private class FakeGoalStreakDao : GoalStreakDao {
        val streaks = mutableMapOf<Long, GoalStreak>()

        override suspend fun insertOrUpdate(streak: GoalStreak): Long {
            streaks[streak.goalTemplateId] = streak
            return 1L
        }

        override suspend fun getStreakForGoalOnce(goalId: Long): GoalStreak? {
            return streaks[goalId]
        }

        override fun getStreakForGoal(goalId: Long): Flow<GoalStreak?> {
            return flowOf(streaks[goalId])
        }

        override fun getAllStreaks(): Flow<List<GoalStreak>> {
            return flowOf(streaks.values.toList())
        }

        override suspend fun deleteStreakForGoal(goalId: Long): Int {
            val removed = streaks.remove(goalId)
            return if (removed != null) 1 else 0
        }
    }

    private class FakeXpLogDao : XpLogDao {
        val logs = mutableListOf<XpLog>()

        override suspend fun insertXpLog(xpLog: XpLog): Long {
            logs.add(xpLog)
            return logs.size.toLong()
        }

        override fun getAllXpLogs(): Flow<List<XpLog>> {
            return flowOf(logs)
        }

        override suspend fun getTotalXp(): Long? {
            return logs.sumOf { it.xpEarned }
        }

        override fun getXpLogsForGoal(goalId: Long): Flow<List<XpLog>> {
            return flowOf(logs.filter { it.goalTemplateId == goalId })
        }

        override suspend fun deleteXpLogsForGoal(goalId: Long): Int {
            val removed = logs.removeAll { it.goalTemplateId == goalId }
            return if (removed) 1 else 0
        }

        override fun getTotalXpEarned(startTimeMs: Long, endTimeMs: Long): Flow<Long> = flow {
            emit(logs.filter { it.completedAt in startTimeMs..endTimeMs }.sumOf { it.xpEarned })
        }

        override fun getDailyXpEarned(startTimeMs: Long, endTimeMs: Long): Flow<List<com.rohansingh.focusforge.data.dao.DailyXpStat>> = flow {
            emit(emptyList())
        }
    }
}
