package com.rohansingh.focusforge

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.entities.ScreenTimeLog
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.StatisticsRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.ScreenTimeManager
import com.rohansingh.focusforge.domain.models.RewardType
import com.rohansingh.focusforge.domain.models.TimePeriod
import com.rohansingh.focusforge.domain.models.TimeRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsDashboardAndroidTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: StatisticsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = StatisticsRepository(
            goalLogDao = database.goalLogDao(),
            redemptionLogDao = database.redemptionLogDao(),
            xpLogDao = database.xpLogDao(),
            goalStreakDao = database.goalStreakDao(),
            focusSessionDao = database.focusSessionDao(),
            screenTimeLogDao = database.screenTimeLogDao(),
            walletDao = database.walletDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRoomMigration5to6_createsScreenTimeLogsTable() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_5_6.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS wallet (
                            id INTEGER PRIMARY KEY NOT NULL,
                            creditBalance REAL NOT NULL,
                            rupeeBalance REAL NOT NULL,
                            screenTimeMinutes INTEGER NOT NULL,
                            totalXp INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        context.deleteDatabase("test_migration_5_6.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO wallet (id, creditBalance, rupeeBalance, screenTimeMinutes, totalXp) VALUES (1, 150.0, 75.0, 45, 1200)")

        // Execute MIGRATION_5_6
        AppDatabase.MIGRATION_5_6.migrate(db)

        // Verify wallet table unaffected
        val cursorWallet = db.query("SELECT * FROM wallet WHERE id = 1")
        assertTrue(cursorWallet.moveToFirst())
        assertEquals(150.0, cursorWallet.getDouble(cursorWallet.getColumnIndexOrThrow("creditBalance")), 0.001)
        assertEquals(1200L, cursorWallet.getLong(cursorWallet.getColumnIndexOrThrow("totalXp")))
        cursorWallet.close()

        // Verify screen_time_logs table exists and is fully functional
        db.execSQL("INSERT INTO screen_time_logs (packageName, appName, minutesConsumed, consumedAt) VALUES ('com.android.chrome', 'Google Chrome', 12, 1724150000000)")
        val cursorLog = db.query("SELECT * FROM screen_time_logs WHERE packageName = 'com.android.chrome'")
        assertTrue(cursorLog.moveToFirst())
        assertEquals("Google Chrome", cursorLog.getString(cursorLog.getColumnIndexOrThrow("appName")))
        assertEquals(12, cursorLog.getInt(cursorLog.getColumnIndexOrThrow("minutesConsumed")))
        assertEquals(1724150000000L, cursorLog.getLong(cursorLog.getColumnIndexOrThrow("consumedAt")))
        cursorLog.close()

        db.close()
        context.deleteDatabase("test_migration_5_6.db")
    }

    @Test
    fun testContinuousScreenTimeSessionLogging_integration() = runBlocking {
        val walletRepo = WalletRepository(database.walletDao())
        val restrictedRepo = RestrictedAppRepository(database.restrictedAppDao())
        val screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepo,
            restrictedAppRepository = restrictedRepo,
            screenTimeLogDao = database.screenTimeLogDao(),
            minuteIntervalMs = 60_000L
        )

        // Initial state: 10 minutes screen time, Chrome restricted
        walletRepo.ensureWalletInitialized()
        val initialWallet = walletRepo.getWalletOnce()!!
        walletRepo.updateWallet(initialWallet.copy(screenTimeMinutes = 10))
        restrictedRepo.setAppRestricted("com.android.chrome", "Google Chrome", true)

        var time = 100_000L
        // Enter Chrome
        screenTimeManager.processTick("com.android.chrome", true, time)

        // 120s of active usage in Chrome (2 full minutes deducted)
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)
        time += 60_000L
        screenTimeManager.processTick("com.android.chrome", true, time)

        // No logs yet while active
        val logsBeforeEnd = database.screenTimeLogDao().getAllLogs().first()
        assertEquals(0, logsBeforeEnd.size)

        // Leave Chrome -> session ends
        time += 15_000L
        screenTimeManager.processTick(null, false, time)

        // Exactly ONE row written for Chrome with 2 minutes
        val logsAfterEnd = database.screenTimeLogDao().getAllLogs().first()
        assertEquals(1, logsAfterEnd.size)
        val log = logsAfterEnd.first()
        assertEquals("com.android.chrome", log.packageName)
        assertEquals("Google Chrome", log.appName)
        assertEquals(2, log.minutesConsumed)

        // Wallet balance reflects 8 min
        val wallet = walletRepo.getWalletOnce()
        assertNotNull(wallet)
        assertEquals(8, wallet!!.screenTimeMinutes)
    }

    @Test
    fun testCreditsAndGoalsAggregation_byDateRange() = runBlocking {
        val goalId1 = database.goalTemplateDao().insertGoalTemplate(
            GoalTemplate(id = 1, title = "Deep Work", unit = "hours", creditRate = 20.0, dailyCap = 100.0)
        )
        val goalId2 = database.goalTemplateDao().insertGoalTemplate(
            GoalTemplate(id = 2, title = "Reading", unit = "pages", creditRate = 1.0, dailyCap = 50.0)
        )

        val now = System.currentTimeMillis()
        val yesterday = now - 86_400_000L
        val lastMonth = now - (35L * 86_400_000L) // Outside 30 days

        // Insert Goal Logs
        database.goalLogDao().insertGoalLog(GoalLog(id = 1, goalTemplateId = goalId1, amountCompleted = 2.0, creditsEarned = 40.0, completedAt = now))
        database.goalLogDao().insertGoalLog(GoalLog(id = 2, goalTemplateId = goalId1, amountCompleted = 1.0, creditsEarned = 20.0, completedAt = now))
        database.goalLogDao().insertGoalLog(GoalLog(id = 3, goalTemplateId = goalId2, amountCompleted = 30.0, creditsEarned = 30.0, completedAt = yesterday))
        database.goalLogDao().insertGoalLog(GoalLog(id = 4, goalTemplateId = goalId2, amountCompleted = 10.0, creditsEarned = 10.0, completedAt = lastMonth))

        // Query 7 Days range
        val range7D = StatisticsRepository.calculateTimeRange(TimePeriod.LAST_7_DAYS)
        val economy7D = repository.getEconomyStats(TimePeriod.LAST_7_DAYS, range7D).first()
        val goals7D = repository.getGoalsStats(TimePeriod.LAST_7_DAYS, range7D).first()

        // 40 + 20 + 30 = 90.0 (lastMonth 10.0 excluded)
        assertEquals(90.0, economy7D.totalCreditsEarned, 0.001)
        assertEquals(3, goals7D.totalCompletions)
        assertEquals(2, goals7D.goalPerformance.size)

        val deepWorkPerf = goals7D.goalPerformance.find { it.goalTemplateId == goalId1 }
        assertNotNull(deepWorkPerf)
        assertEquals(60.0, deepWorkPerf!!.totalCredits, 0.001)
        assertEquals(3.0, deepWorkPerf.totalAmount, 0.001)
        assertEquals(2, deepWorkPerf.completionCount)
    }

    @Test
    fun testRewardRedemptionsAggregation_andScreenTimeEarned() = runBlocking {
        val screenTimeRewardId = database.rewardTemplateDao().insertRewardTemplate(
            RewardTemplate(id = 1, title = "30m Screen Time", unit = "min", rewardType = RewardType.SCREEN_TIME, creditRate = 30.0)
        )
        val coffeeRewardId = database.rewardTemplateDao().insertRewardTemplate(
            RewardTemplate(id = 2, title = "Coffee", unit = "cup", rewardType = RewardType.CUSTOM, creditRate = 50.0)
        )

        val now = System.currentTimeMillis()
        database.redemptionLogDao().insertRedemptionLog(
            RedemptionLog(id = 1, rewardTemplateId = screenTimeRewardId, unitsRedeemed = 30.0, creditsSpent = 30.0, redeemedAt = now)
        )
        database.redemptionLogDao().insertRedemptionLog(
            RedemptionLog(id = 2, rewardTemplateId = coffeeRewardId, unitsRedeemed = 1.0, creditsSpent = 50.0, redeemedAt = now)
        )

        val range = TimeRange(now - 3600_000L, now + 3600_000L)
        val rewardStats = repository.getRewardsStats(range).first()
        val screenTimeMinutesEarned = database.redemptionLogDao().getScreenTimeMinutesRedeemed(range.startTimeMs, range.endTimeMs).first()

        assertEquals(2, rewardStats.totalRedemptionsCount)
        assertEquals(80.0, rewardStats.totalCreditsSpent, 0.001)
        assertEquals(30.0, screenTimeMinutesEarned, 0.001)
    }

    @Test
    fun testFocusSessionAggregation_actualDurationCalculation() = runBlocking {
        val goalId = database.goalTemplateDao().insertGoalTemplate(
            GoalTemplate(id = 1, title = "Coding", unit = "min", creditRate = 1.0, dailyCap = 100.0)
        )

        val now = System.currentTimeMillis()
        val startTime = now - 3600_000L // 1 hour ago
        val completedTime = startTime + (25 * 60_000L) // exactly 25 minutes elapsed

        database.focusSessionDao().insertSession(
            FocusSessionEntity(
                id = 1,
                goalId = goalId,
                snapshotGoalTitle = "Coding",
                snapshotGoalUnit = "min",
                snapshotCreditRate = 1.0,
                snapshotDailyCap = 100.0,
                targetDurationMinutes = 25,
                status = "COMPLETED",
                startedAtWallClockMs = startTime,
                targetEndWallClockMs = startTime + (25 * 60_000L),
                startedAtElapsedRealtimeMs = 1000L,
                completedAtWallClockMs = completedTime
            )
        )

        val range = TimeRange(startTime - 1000L, now + 1000L)
        val focusStats = repository.getFocusStats(range).first()

        assertEquals(1, focusStats.completedSessionsCount)
        assertEquals(25, focusStats.totalFocusMinutes)
        assertEquals(25.0, focusStats.avgDurationMinutes, 0.001)
        assertEquals(1, focusStats.goalBreakdown.size)
        assertEquals("Coding", focusStats.goalBreakdown.first().goalTitle)
    }
}
