package com.rohansingh.focusforge.services.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.services.notifications.FocusForgeNotificationManager
import com.rohansingh.focusforge.services.notifications.GoalReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * BroadcastReceiver triggered by AlarmManager when a goal reminder fires.
 *
 * Verifies:
 * 1. Goal exists and is active.
 * 2. Goal is recurring and has reminderEnabled == true.
 * 3. Goal has not already been completed today.
 *
 * If qualified, posts a notification and reschedules for tomorrow.
 */
class GoalReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        if (goalId <= 0L) {
            Log.e(TAG, "Invalid goal ID received: $goalId")
            return
        }

        Log.d(TAG, "Goal reminder alarm fired for goal #$goalId")

        val app = context.applicationContext as? FocusForgeApplication ?: FocusForgeApplication.instance
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val goalTemplate = app.database.goalTemplateDao().getGoalTemplateById(goalId)
                if (goalTemplate == null || !goalTemplate.reminderEnabled || !goalTemplate.recurring) {
                    Log.d(TAG, "Goal #$goalId no longer qualifies for reminder. Cancelling alarm.")
                    GoalReminderScheduler.cancelReminder(context, goalId)
                    return@launch
                }

                // Check if goal has already been completed today
                val now = Calendar.getInstance()
                val startOfDay = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfDay = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val creditsEarnedToday = app.database.goalLogDao().getCreditsEarnedToday(
                    goalTemplateId = goalId,
                    startOfDayTimestamp = startOfDay,
                    endOfDayTimestamp = endOfDay
                )

                if (creditsEarnedToday > 0.0) {
                    Log.d(TAG, "Goal #$goalId was already completed today ($creditsEarnedToday credits). Suppressing notification.")
                } else {
                    Log.i(TAG, "Delivering goal reminder notification for #${goalTemplate.id} ('${goalTemplate.title}')")
                    FocusForgeNotificationManager.showGoalReminderNotification(
                        context = context,
                        goalId = goalTemplate.id,
                        goalTitle = goalTemplate.title
                    )
                }

                // Reschedule for next day
                GoalReminderScheduler.scheduleReminder(context, goalTemplate)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling goal reminder for goal #$goalId", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GoalReminderReceiver"
        const val ACTION_GOAL_REMINDER = "com.rohansingh.focusforge.ACTION_GOAL_REMINDER"
        const val EXTRA_GOAL_ID = "extra_goal_id"
    }
}
