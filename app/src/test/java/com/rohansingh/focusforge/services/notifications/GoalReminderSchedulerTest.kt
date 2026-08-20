package com.rohansingh.focusforge.services.notifications

import com.rohansingh.focusforge.data.entities.GoalTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GoalReminderSchedulerTest {

    @Test
    fun `calculateNextTriggerTime schedules for today when time is in the future`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Target: 20:00 (8:00 PM today)
        val triggerTimeMs = GoalReminderScheduler.calculateNextTriggerTime(
            hour = 20,
            minute = 0,
            now = now
        )

        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerTimeMs }
        assertEquals(now.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(20, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertTrue(triggerTimeMs > now.timeInMillis)
    }

    @Test
    fun `calculateNextTriggerTime schedules for tomorrow when time has passed today`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Target: 20:00 (already passed today)
        val triggerTimeMs = GoalReminderScheduler.calculateNextTriggerTime(
            hour = 20,
            minute = 0,
            now = now
        )

        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerTimeMs }
        val tomorrowDay = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.get(Calendar.DAY_OF_YEAR)
        assertEquals(tomorrowDay, triggerCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(20, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertTrue(triggerTimeMs > now.timeInMillis)
    }

    @Test
    fun `GoalTemplate default reminder values are false and 20_00`() {
        val goal = GoalTemplate(
            id = 1,
            title = "Exercise",
            unit = "minutes",
            creditRate = 1.0,
            recurring = true
        )
        assertEquals(false, goal.reminderEnabled)
        assertEquals(20, goal.reminderHour)
        assertEquals(0, goal.reminderMinute)
    }
}
