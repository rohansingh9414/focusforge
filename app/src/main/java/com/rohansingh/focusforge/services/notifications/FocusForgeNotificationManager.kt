package com.rohansingh.focusforge.services.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rohansingh.focusforge.MainActivity
import com.rohansingh.focusforge.R

/**
 * Centralized notification manager for FocusForge.
 * Manages notification channels and standard notification delivery.
 */
object FocusForgeNotificationManager {

    const val CHANNEL_DAILY_GRANT = "daily_grant_channel"
    const val CHANNEL_SCREEN_TIME_ALERT = "screen_time_alert_channel"
    const val CHANNEL_GOAL_REMINDER = "goal_reminder_channel"
    const val CHANNEL_FOCUS_SESSION = "focus_session_channel"

    const val NOTIFICATION_ID_DAILY_GRANT = 3001
    const val NOTIFICATION_ID_SCREEN_TIME_ALERT = 3002
    const val NOTIFICATION_ID_FOCUS_SESSION = 2001
    private const val NOTIFICATION_ID_GOAL_REMINDER_BASE = 4000

    /**
     * Initializes all required application notification channels.
     * Safe to call repeatedly.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val dailyGrantChannel = NotificationChannel(
                CHANNEL_DAILY_GRANT,
                "Daily Grant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Confirmations when daily grant is applied"
            }

            val screenTimeAlertChannel = NotificationChannel(
                CHANNEL_SCREEN_TIME_ALERT,
                "Screen Time Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when available screen-time minutes are low"
            }

            val goalReminderChannel = NotificationChannel(
                CHANNEL_GOAL_REMINDER,
                "Goal Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily reminders for active recurring goals"
            }

            val focusSessionChannel = NotificationChannel(
                CHANNEL_FOCUS_SESSION,
                "Focus Session",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Focus Session updates and completion alerts"
            }

            manager.createNotificationChannels(
                listOf(
                    dailyGrantChannel,
                    screenTimeAlertChannel,
                    goalReminderChannel,
                    focusSessionChannel
                )
            )
        }
    }

    /**
     * Shows a confirmation notification when the daily grant is applied.
     */
    fun showDailyGrantNotification(context: Context, rupeesGranted: Double, screenTimeGranted: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val pendingIntent = createMainActivityPendingIntent(context)
        val formattedRupees = if (rupeesGranted % 1.0 == 0.0) rupeesGranted.toInt().toString() else String.format("%.1f", rupeesGranted)

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_GRANT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Daily Grant Applied! 🎁")
            .setContentText("+₹$formattedRupees and +$screenTimeGranted min screen time added to your wallet.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID_DAILY_GRANT, notification)
    }

    /**
     * Shows a warning notification when remaining screen time is low (<= 15 minutes).
     */
    fun showLowScreenTimeNotification(context: Context, remainingMinutes: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val pendingIntent = createMainActivityPendingIntent(context)
        val title = if (remainingMinutes <= 0) "Screen Time Exhausted ⏱️" else "Low Screen Time Warning ⚠️"
        val message = if (remainingMinutes <= 0) {
            "You have 0 minutes of screen time remaining. Restricted apps will be blocked."
        } else {
            "Only $remainingMinutes minute${if (remainingMinutes == 1) "" else "s"} of screen time remaining!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SCREEN_TIME_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID_SCREEN_TIME_ALERT, notification)
    }

    /**
     * Shows a scheduled goal reminder notification for an active recurring goal.
     */
    fun showGoalReminderNotification(context: Context, goalId: Long, goalTitle: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val pendingIntent = createMainActivityPendingIntent(context)
        val notificationId = (NOTIFICATION_ID_GOAL_REMINDER_BASE + (goalId % 5000)).toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_GOAL_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Goal Reminder: $goalTitle ⭐")
            .setContentText("Don't forget to complete \"$goalTitle\" today!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    private fun createMainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
