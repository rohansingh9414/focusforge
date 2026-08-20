package com.rohansingh.focusforge.services.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.services.receiver.GoalReminderReceiver
import java.util.Calendar

/**
 * Scheduler for daily recurring goal reminders using Android AlarmManager.
 */
object GoalReminderScheduler {

    private const val TAG = "GoalReminderScheduler"

    /**
     * Checks whether exact alarms can currently be scheduled on this device and OS version.
     */
    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Schedules or updates a daily alarm for the given [goal] at its configured reminderHour:reminderMinute.
     * Uses exact alarms if available, otherwise falls back gracefully to a non-exact idle alarm.
     */
    fun scheduleReminder(context: Context, goal: GoalTemplate) {
        if (!goal.reminderEnabled || !goal.recurring) {
            cancelReminder(context, goal.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = createReminderPendingIntent(context, goal.id)

        val triggerTimeMs = calculateNextTriggerTime(goal.reminderHour, goal.reminderMinute)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled exact reminder for goal #${goal.id} ('${goal.title}') at triggerTime=$triggerTimeMs")
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException scheduling exact alarm for goal #${goal.id}, falling back to non-exact alarm", e)
            }
        }

        // Safe fallback: non-exact alarm that works without SCHEDULE_EXACT_ALARM permission
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled non-exact fallback reminder for goal #${goal.id} ('${goal.title}') at triggerTime=$triggerTimeMs")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling non-exact fallback reminder for goal #${goal.id}", e)
        }
    }

    /**
     * Cancels any scheduled reminder alarm for [goalId].
     */
    fun cancelReminder(context: Context, goalId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = createReminderPendingIntent(context, goalId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled reminder alarm for goal #$goalId")
    }

    /**
     * Reschedules reminders for all goals where reminder is enabled.
     */
    fun rescheduleAllEnabledReminders(context: Context, goals: List<GoalTemplate>) {
        goals.filter { it.reminderEnabled && it.recurring }.forEach { goal ->
            scheduleReminder(context, goal)
        }
    }

    private fun createReminderPendingIntent(context: Context, goalId: Long): PendingIntent {
        val intent = Intent(context, GoalReminderReceiver::class.java).apply {
            action = GoalReminderReceiver.ACTION_GOAL_REMINDER
            putExtra(GoalReminderReceiver.EXTRA_GOAL_ID, goalId)
        }
        return PendingIntent.getBroadcast(
            context,
            goalId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun calculateNextTriggerTime(hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Long {
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, hour)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        if (target.timeInMillis <= now.timeInMillis) {
            // Target time has already passed today; schedule for tomorrow
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
