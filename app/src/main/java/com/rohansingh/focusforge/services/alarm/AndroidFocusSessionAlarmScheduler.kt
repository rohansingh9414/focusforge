package com.rohansingh.focusforge.services.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rohansingh.focusforge.domain.managers.FocusSessionAlarmScheduler
import com.rohansingh.focusforge.services.receiver.FocusSessionCompletionReceiver

/**
 * Android implementation of FocusSessionAlarmScheduler using AlarmManager.
 */
class AndroidFocusSessionAlarmScheduler(
    private val context: Context
) : FocusSessionAlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun scheduleCompletionAlarm(sessionId: Long, triggerAtWallClockMs: Long) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service unavailable")
            return
        }

        val intent = Intent(context, FocusSessionCompletionReceiver::class.java).apply {
            putExtra(FocusSessionCompletionReceiver.EXTRA_SESSION_ID, sessionId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtWallClockMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for session #$sessionId at $triggerAtWallClockMs")
                } else {
                    // Fallback to setAlarmClock which does not require SCHEDULE_EXACT_ALARM
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtWallClockMs, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "Scheduled alarmClock fallback for session #$sessionId at $triggerAtWallClockMs")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtWallClockMs,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm (pre-S) for session #$sessionId at $triggerAtWallClockMs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for session #$sessionId: ${e.message}", e)
        }
    }

    override fun cancelCompletionAlarm(sessionId: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, FocusSessionCompletionReceiver::class.java).apply {
            putExtra(FocusSessionCompletionReceiver.EXTRA_SESSION_ID, sessionId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled completion alarm for session #$sessionId")
    }

    companion object {
        private const val TAG = "FocusSessionAlarm"
        const val ALARM_REQUEST_CODE = 1001
    }
}
