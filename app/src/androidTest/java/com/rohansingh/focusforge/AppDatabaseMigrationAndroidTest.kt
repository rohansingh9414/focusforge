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
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.entities.ScreenTimeLog
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.entities.XpLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive migration and data preservation tests for FocusForge Room database.
 * Verifies that all user data from baseline Version 4 survives through Version 7.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDbName = "focusforge_migration_test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
    }

    @Test
    fun testFreshDatabaseCreation_atVersion7() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Insert and verify entities
        val walletId = db.walletDao().insertWallet(
            Wallet(
                id = 1,
                creditBalance = 50.0,
                rupeeBalance = 100.0,
                screenTimeMinutes = 30,
                lastDailyGrantDate = "2026-08-20",
                totalXp = 500L
            )
        )
        assertEquals(1L, walletId)

        val goalId = db.goalTemplateDao().insertGoalTemplate(
            GoalTemplate(
                title = "Read Book",
                unit = "pages",
                creditRate = 2.0,
                dailyCap = 40.0,
                recurring = true,
                reminderEnabled = true,
                reminderHour = 21,
                reminderMinute = 30
            )
        )
        assertTrue(goalId > 0)

        val loadedGoal = db.goalTemplateDao().getGoalTemplateById(goalId)
        assertNotNull(loadedGoal)
        assertEquals("Read Book", loadedGoal!!.title)
        assertTrue(loadedGoal.reminderEnabled)
        assertEquals(21, loadedGoal.reminderHour)
        assertEquals(30, loadedGoal.reminderMinute)

        db.close()
    }

    @Test
    fun testFullMigration_fromVersion4ToVersion7_preservesAllSeededData() = runBlocking {
        // 1. Create database at Version 4 and seed real data across all tables
        val v4Config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Version 4 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS wallet (
                            id INTEGER PRIMARY KEY NOT NULL,
                            creditBalance REAL NOT NULL,
                            rupeeBalance REAL NOT NULL,
                            screenTimeMinutes INTEGER NOT NULL,
                            lastDailyGrantDate TEXT
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS goal_templates (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            unit TEXT NOT NULL,
                            creditRate REAL NOT NULL,
                            dailyCap REAL NOT NULL,
                            recurring INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS goal_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            goalTemplateId INTEGER NOT NULL,
                            amountCompleted REAL NOT NULL,
                            creditsEarned REAL NOT NULL,
                            completedAt INTEGER NOT NULL,
                            FOREIGN KEY(goalTemplateId) REFERENCES goal_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_logs_goalTemplateId ON goal_logs(goalTemplateId)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS reward_templates (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            unit TEXT NOT NULL,
                            rewardType TEXT NOT NULL,
                            pricingMode TEXT NOT NULL,
                            rupeeCost REAL NOT NULL,
                            creditRate REAL NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS redemption_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            rewardTemplateId INTEGER NOT NULL,
                            unitsRedeemed REAL NOT NULL,
                            creditsSpent REAL NOT NULL,
                            redeemedAt INTEGER NOT NULL,
                            FOREIGN KEY(rewardTemplateId) REFERENCES reward_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_redemption_logs_rewardTemplateId ON redemption_logs(rewardTemplateId)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS restricted_apps (
                            packageName TEXT PRIMARY KEY NOT NULL,
                            appName TEXT NOT NULL,
                            isRestricted INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS focus_sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            goalId INTEGER NOT NULL,
                            snapshotGoalTitle TEXT NOT NULL,
                            snapshotGoalUnit TEXT NOT NULL,
                            snapshotCreditRate REAL NOT NULL,
                            snapshotDailyCap REAL NOT NULL,
                            targetDurationMinutes INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            startedAtWallClockMs INTEGER NOT NULL,
                            targetEndWallClockMs INTEGER NOT NULL,
                            startedAtElapsedRealtimeMs INTEGER NOT NULL,
                            completedAtWallClockMs INTEGER,
                            FOREIGN KEY(goalId) REFERENCES goal_templates(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_status ON focus_sessions(status)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_goalId ON focus_sessions(goalId)")

                    // Seed real historical sample rows in v4
                    db.execSQL("""
                        INSERT INTO wallet (id, creditBalance, rupeeBalance, screenTimeMinutes, lastDailyGrantDate)
                        VALUES (1, 150.5, 500.0, 45, '2026-08-19')
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO goal_templates (id, title, unit, creditRate, dailyCap, recurring)
                        VALUES (10, 'Deep Work Coding', 'hours', 10.0, 50.0, 1)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO goal_logs (id, goalTemplateId, amountCompleted, creditsEarned, completedAt)
                        VALUES (20, 10, 2.0, 20.0, 1724100000000)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO reward_templates (id, title, unit, rewardType, pricingMode, rupeeCost, creditRate, isActive)
                        VALUES (30, 'Video Games', 'hours', 'CUSTOM', 'MANUAL', 0.0, 15.0, 1)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO redemption_logs (id, rewardTemplateId, unitsRedeemed, creditsSpent, redeemedAt)
                        VALUES (40, 30, 1.0, 15.0, 1724101000000)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO restricted_apps (packageName, appName, isRestricted)
                        VALUES ('com.instagram.android', 'Instagram', 1)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO focus_sessions (
                            id, goalId, snapshotGoalTitle, snapshotGoalUnit, snapshotCreditRate, snapshotDailyCap,
                            targetDurationMinutes, status, startedAtWallClockMs, targetEndWallClockMs,
                            startedAtElapsedRealtimeMs, completedAtWallClockMs
                        ) VALUES (
                            50, 10, 'Deep Work Coding', 'hours', 10.0, 50.0,
                            60, 'COMPLETED', 1724102000000, 1724105600000,
                            100000, 1724105600000
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(v4Config)
        val writableDb = helper.writableDatabase
        // Ensure all seeded rows are committed
        writableDb.close()

        // 2. Open migrated database with Room at Version 7 with migrations 4->5, 5->6, 6->7
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .build()

        // Verify Wallet survival + totalXp default
        val wallet = roomDb.walletDao().getWalletOnce()
        assertNotNull(wallet)
        assertEquals(1, wallet!!.id)
        assertEquals(150.5, wallet.creditBalance, 0.001)
        assertEquals(500.0, wallet.rupeeBalance, 0.001)
        assertEquals(45, wallet.screenTimeMinutes)
        assertEquals("2026-08-19", wallet.lastDailyGrantDate)
        assertEquals(0L, wallet.totalXp) // Added with DEFAULT 0 in 4->5

        // Verify GoalTemplate survival + reminder default fields
        val goal = roomDb.goalTemplateDao().getGoalTemplateById(10)
        assertNotNull(goal)
        assertEquals(10L, goal!!.id)
        assertEquals("Deep Work Coding", goal.title)
        assertEquals("hours", goal.unit)
        assertEquals(10.0, goal.creditRate, 0.001)
        assertEquals(50.0, goal.dailyCap, 0.001)
        assertTrue(goal.recurring)
        assertFalse(goal.reminderEnabled) // Default 0
        assertEquals(20, goal.reminderHour) // Default 20
        assertEquals(0, goal.reminderMinute) // Default 0

        // Verify GoalLog survival
        val goalLogs = roomDb.goalLogDao().getLogsForGoal(10).first()
        assertEquals(1, goalLogs.size)
        assertEquals(20L, goalLogs[0].id)
        assertEquals(2.0, goalLogs[0].amountCompleted, 0.001)
        assertEquals(20.0, goalLogs[0].creditsEarned, 0.001)
        assertEquals(1724100000000L, goalLogs[0].completedAt)

        // Verify RewardTemplate survival
        val rewards = roomDb.rewardTemplateDao().getAllRewardTemplates().first()
        assertEquals(1, rewards.size)
        assertEquals(30L, rewards[0].id)
        assertEquals("Video Games", rewards[0].title)
        assertEquals(15.0, rewards[0].creditRate, 0.001)

        // Verify RedemptionLog survival
        val redemptions = roomDb.redemptionLogDao().getAllLogs().first()
        assertEquals(1, redemptions.size)
        assertEquals(40L, redemptions[0].id)
        assertEquals(1.0, redemptions[0].unitsRedeemed, 0.001)
        assertEquals(15.0, redemptions[0].creditsSpent, 0.001)

        // Verify RestrictedApp survival
        val restrictedApp = roomDb.restrictedAppDao().getRestrictedApp("com.instagram.android")
        assertNotNull(restrictedApp)
        assertEquals("Instagram", restrictedApp!!.appName)
        assertTrue(restrictedApp.isRestricted)

        // Verify FocusSession survival
        val session = roomDb.focusSessionDao().getSessionById(50)
        assertNotNull(session)
        assertEquals(50L, session!!.id)
        assertEquals(10L, session.goalId)
        assertEquals("Deep Work Coding", session.snapshotGoalTitle)
        assertEquals("COMPLETED", session.status)
        assertEquals(60, session.targetDurationMinutes)

        // Verify XpLog table creation and insertion
        val xpLogId = roomDb.xpLogDao().insertXpLog(
            XpLog(
                id = 60,
                goalTemplateId = 10,
                goalLogId = 20,
                xpEarned = 200,
                completedAt = 1724100000000L
            )
        )
        assertEquals(60L, xpLogId)
        val xpLogs = roomDb.xpLogDao().getXpLogsForGoal(10).first()
        assertEquals(1, xpLogs.size)
        assertEquals(60L, xpLogs[0].id)
        assertEquals(200L, xpLogs[0].xpEarned)

        // Verify GoalStreak table creation and insertion
        roomDb.goalStreakDao().insertOrUpdate(
            GoalStreak(
                goalTemplateId = 10,
                currentStreak = 3,
                longestStreak = 5,
                lastCompletedDate = "2026-08-19"
            )
        )
        val streak = roomDb.goalStreakDao().getStreakForGoalOnce(10)
        assertNotNull(streak)
        assertEquals(10L, streak!!.goalTemplateId)
        assertEquals(3, streak.currentStreak)
        assertEquals(5, streak.longestStreak)
        assertEquals("2026-08-19", streak.lastCompletedDate)

        // Verify ScreenTimeLog table creation and insertion
        val screenLogId = roomDb.screenTimeLogDao().insertLog(
            ScreenTimeLog(
                id = 70,
                packageName = "com.instagram.android",
                appName = "Instagram",
                minutesConsumed = 12,
                consumedAt = 1724103000000L
            )
        )
        assertEquals(70L, screenLogId)
        val screenLogs = roomDb.screenTimeLogDao().getAllLogs().first()
        assertEquals(1, screenLogs.size)
        assertEquals(70L, screenLogs[0].id)
        assertEquals("com.instagram.android", screenLogs[0].packageName)
        assertEquals(12, screenLogs[0].minutesConsumed)

        roomDb.close()
    }
}
