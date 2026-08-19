package com.rohansingh.focusforge.services.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager when a Focus Session reaches target duration.
 * Idempotently executes session completion, awards credits, and posts completion notification.
 */
class FocusSessionCompletionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0L) {
            Log.e(TAG, "Invalid session ID received: $sessionId")
            return
        }

        Log.d(TAG, "Alarm triggered completion for Focus Session #$sessionId")

        val app = context.applicationContext as? FocusForgeApplication ?: FocusForgeApplication.instance
        val focusSessionManager = app.focusSessionManager

        // Handle completion in background scope
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = focusSessionManager.handleSessionCompletion(sessionId)
                result.onSuccess { credits ->
                    showCompletionNotification(context, credits)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling session completion for #$sessionId: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showCompletionNotification(context: Context, credits: Double) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Session",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Focus Session updates and completion alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val creditText = if (credits > 0.0) "+${String.format("%.1f", credits)} credits" else "Goal completed!"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus Session Complete! 🎉")
            .setContentText("Great job staying focused! Earned $creditText.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "FocusSessionReceiver"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val CHANNEL_ID = "focus_session_channel"
        const val NOTIFICATION_ID = 2001
    }
}
