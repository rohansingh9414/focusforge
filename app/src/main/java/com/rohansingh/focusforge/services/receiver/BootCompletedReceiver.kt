package com.rohansingh.focusforge.services.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered when device boot completes.
 * Restores active Focus Sessions, reschedules alarms, and restarts monitoring.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BOOT_COMPLETED received. Restoring FocusForge services and session state.")

        val app = context.applicationContext as? FocusForgeApplication ?: FocusForgeApplication.instance
        val focusSessionManager = app.focusSessionManager

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                focusSessionManager.restoreSessionState()
                // Restart monitoring service if permissions and restrictions are active
                AppMonitoringService.start(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring session on boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
