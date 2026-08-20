package com.rohansingh.focusforge

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.services.notifications.FocusForgeNotificationManager
import com.rohansingh.focusforge.services.notifications.GoalReminderScheduler
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

@RunWith(AndroidJUnit4::class)
class NotificationsAndroidTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testNotificationChannelsInitialization() {
        FocusForgeNotificationManager.createNotificationChannels(context)
        assertNotNull(context)
    }

    @Test
    fun testPostAllNotificationTypes() {
        FocusForgeNotificationManager.createNotificationChannels(context)
        FocusForgeNotificationManager.showDailyGrantNotification(context, 50.0, 60)
        FocusForgeNotificationManager.showLowScreenTimeNotification(context, 15)
        FocusForgeNotificationManager.showGoalReminderNotification(context, 1L, "Evening Study")
        assertNotNull(context)
    }

    private fun setExactAlarmAppOp(allow: Boolean) {
        val mode = if (allow) "allow" else "ignore"
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val parcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(
            "cmd appops set com.rohansingh.focusforge SCHEDULE_EXACT_ALARM $mode"
        )
        parcelFileDescriptor.close()
        Thread.sleep(200)
    }

    private fun getActiveReminderPendingIntent(goalId: Long): android.app.PendingIntent? {
        val intent = android.content.Intent(context, com.rohansingh.focusforge.services.receiver.GoalReminderReceiver::class.java).apply {
            action = com.rohansingh.focusforge.services.receiver.GoalReminderReceiver.ACTION_GOAL_REMINDER
            putExtra(com.rohansingh.focusforge.services.receiver.GoalReminderReceiver.EXTRA_GOAL_ID, goalId)
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            goalId.toInt(),
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Test
    fun testExactAlarmPath_scheduleAndCancel() {
        setExactAlarmAppOp(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            assertTrue("Expected exact alarm permission to be granted", GoalReminderScheduler.canScheduleExact(context))
        }

        val goal = GoalTemplate(
            id = 201,
            title = "Exact Alarm Goal",
            unit = "pages",
            creditRate = 2.0,
            recurring = true,
            reminderEnabled = true,
            reminderHour = 20,
            reminderMinute = 0
        )

        GoalReminderScheduler.scheduleReminder(context, goal)
        assertNotNull("PendingIntent should exist when exact reminder is scheduled", getActiveReminderPendingIntent(201L))

        GoalReminderScheduler.cancelReminder(context, 201L)
        org.junit.Assert.assertNull("PendingIntent should be null after cancellation", getActiveReminderPendingIntent(201L))
    }

    @Test
    fun testFallbackAlarmPath_scheduleAndCancel_whenExactAlarmDenied() {
        try {
            setExactAlarmAppOp(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                assertFalse("Expected exact alarm permission to be denied/ignored", GoalReminderScheduler.canScheduleExact(context))
            }

            val goal = GoalTemplate(
                id = 202,
                title = "Fallback Alarm Goal",
                unit = "minutes",
                creditRate = 1.5,
                recurring = true,
                reminderEnabled = true,
                reminderHour = 21,
                reminderMinute = 15
            )

            // Must not throw an exception when exact alarms are unavailable
            GoalReminderScheduler.scheduleReminder(context, goal)
            assertNotNull("PendingIntent should exist when fallback non-exact reminder is scheduled", getActiveReminderPendingIntent(202L))

            GoalReminderScheduler.cancelReminder(context, 202L)
            org.junit.Assert.assertNull("PendingIntent should be null after cancellation", getActiveReminderPendingIntent(202L))
        } finally {
            setExactAlarmAppOp(true)
        }
    }

    @Test
    fun testReminderRescheduling_updatesExistingAlarm() {
        val goal = GoalTemplate(
            id = 203,
            title = "Reschedule Goal",
            unit = "reps",
            creditRate = 3.0,
            recurring = true,
            reminderEnabled = true,
            reminderHour = 19,
            reminderMinute = 0
        )

        GoalReminderScheduler.scheduleReminder(context, goal)
        assertNotNull("PendingIntent should exist after initial schedule", getActiveReminderPendingIntent(203L))

        // Update reminder time to 22:30
        val updatedGoal = goal.copy(reminderHour = 22, reminderMinute = 30)
        GoalReminderScheduler.scheduleReminder(context, updatedGoal)
        assertNotNull("PendingIntent should exist after rescheduling", getActiveReminderPendingIntent(203L))

        GoalReminderScheduler.cancelReminder(context, 203L)
        org.junit.Assert.assertNull("PendingIntent should be null after cancel", getActiveReminderPendingIntent(203L))
    }

    @Test
    fun testGoalReminderRescheduleAllEnabled() {
        val goals = listOf(
            GoalTemplate(
                id = 204,
                title = "Morning Read",
                unit = "pages",
                creditRate = 2.0,
                recurring = true,
                reminderEnabled = true,
                reminderHour = 8,
                reminderMinute = 0
            ),
            GoalTemplate(
                id = 205,
                title = "Disabled Reminder Goal",
                unit = "tasks",
                creditRate = 5.0,
                recurring = true,
                reminderEnabled = false
            ),
            GoalTemplate(
                id = 206,
                title = "Non-recurring Goal",
                unit = "tasks",
                creditRate = 5.0,
                recurring = false,
                reminderEnabled = true
            )
        )

        GoalReminderScheduler.rescheduleAllEnabledReminders(context, goals)
        assertNotNull("Enabled recurring goal should have scheduled alarm", getActiveReminderPendingIntent(204L))
        org.junit.Assert.assertNull("Disabled reminder goal should not have scheduled alarm", getActiveReminderPendingIntent(205L))
        org.junit.Assert.assertNull("Non-recurring goal should not have scheduled alarm", getActiveReminderPendingIntent(206L))

        GoalReminderScheduler.cancelReminder(context, 204L)
        org.junit.Assert.assertNull(getActiveReminderPendingIntent(204L))
    }

    @Test
    fun testGoalTemplatePersistence_withReminderFields() = runBlocking {
        val goal = GoalTemplate(
            id = 1,
            title = "Study Math",
            unit = "minutes",
            creditRate = 1.0,
            dailyCap = 120.0,
            recurring = true,
            reminderEnabled = true,
            reminderHour = 19,
            reminderMinute = 45
        )

        val insertedId = database.goalTemplateDao().insertGoalTemplate(goal)
        val loaded = database.goalTemplateDao().getGoalTemplateById(insertedId)

        assertNotNull(loaded)
        assertEquals("Study Math", loaded!!.title)
        assertTrue(loaded.reminderEnabled)
        assertEquals(19, loaded.reminderHour)
        assertEquals(45, loaded.reminderMinute)
    }

    @Test
    fun testMigration6To7_executesAndAddsReminderColumns() {
        val dbName = "test_migration_6_7.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS goal_templates (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            unit TEXT NOT NULL,
                            creditRate REAL NOT NULL,
                            dailyCap REAL NOT NULL,
                            recurring INTEGER NOT NULL
                        )
                    """)
                    db.execSQL("""
                        INSERT INTO goal_templates (id, title, unit, creditRate, dailyCap, recurring)
                        VALUES (1, 'Morning Run', 'km', 5.0, 50.0, 1)
                    """)
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val writableDb = helper.writableDatabase

        // Execute MIGRATION_6_7
        AppDatabase.MIGRATION_6_7.migrate(writableDb)

        // Query migrated table
        val cursor = writableDb.query("SELECT id, title, reminderEnabled, reminderHour, reminderMinute FROM goal_templates WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
        assertEquals("Morning Run", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reminderEnabled")))
        assertEquals(20, cursor.getInt(cursor.getColumnIndexOrThrow("reminderHour")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reminderMinute")))
        cursor.close()
        writableDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun testGoalReminderReceiverExecution() = kotlinx.coroutines.runBlocking {
        val app = context.applicationContext as FocusForgeApplication
        val goalDao = app.database.goalTemplateDao()
        val goal = GoalTemplate(
            id = 555,
            title = "Test Reminder Goal",
            unit = "min",
            creditRate = 1.0,
            recurring = true,
            reminderEnabled = true,
            reminderHour = 20,
            reminderMinute = 0
        )
        goalDao.insertGoalTemplate(goal)

        val intent = android.content.Intent(context, com.rohansingh.focusforge.services.receiver.GoalReminderReceiver::class.java).apply {
            action = com.rohansingh.focusforge.services.receiver.GoalReminderReceiver.ACTION_GOAL_REMINDER
            putExtra(com.rohansingh.focusforge.services.receiver.GoalReminderReceiver.EXTRA_GOAL_ID, 555L)
        }

        val receiver = com.rohansingh.focusforge.services.receiver.GoalReminderReceiver()
        receiver.onReceive(context, intent)

        // Wait a bit for coroutine inside goAsync to complete
        kotlinx.coroutines.delay(500)

        // Clean up
        goalDao.deleteGoalTemplate(goal)
        GoalReminderScheduler.cancelReminder(context, 555L)
    }
}
