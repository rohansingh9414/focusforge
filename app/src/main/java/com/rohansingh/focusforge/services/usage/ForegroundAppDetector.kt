package com.rohansingh.focusforge.services.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detector for Phase 7 using UsageStatsManager to identify foreground application.
 */
class ForegroundAppDetector(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private var monitoringJob: Job? = null

    /**
     * Checks whether the app has been granted PACKAGE_USAGE_STATS permission via AppOps.
     */
    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Checks whether the app has been granted SYSTEM_ALERT_WINDOW (Display over other apps) permission.
     */
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Returns an Intent to open the system Usage Access Settings screen.
     */
    fun getUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Returns an Intent to open the system Display Over Other Apps Settings screen.
     */
    fun getOverlaySettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Queries the system for the currently foreground package.
     * Returns null if permission is missing, screen is off, or no event is found.
     */
    fun getForegroundApp(): String? {
        if (!hasUsageAccessPermission() || usageStatsManager == null) {
            return null
        }

        // If screen is off/not interactive, no app is actively in foreground
        if (powerManager != null && !powerManager.isInteractive) {
            return null
        }

        val endTime = System.currentTimeMillis()
        // Query events from the last 1 hour to support continuous long-running sessions
        val beginTime = endTime - (1000 * 60 * 60)

        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()

        var latestEventTime = 0L
        var latestPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN
            ) {
                if (event.timeStamp > latestEventTime) {
                    latestEventTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }

        return latestPackage
    }

    /**
     * Starts a polling loop in the given CoroutineScope.
     * Emits foreground package changes to [foregroundPackage] flow and calls [onTick] every interval.
     */
    fun startMonitoring(
        scope: CoroutineScope,
        pollingIntervalMs: Long = 500L,
        onTick: ((currentPackage: String?) -> Unit)? = null
    ) {
        stopMonitoring()
        monitoringJob = scope.launch {
            var previousPackage: String? = null
            while (isActive) {
                val currentPackage = getForegroundApp()
                if (currentPackage != previousPackage) {
                    Log.d(TAG, "Foreground transition: '$previousPackage' -> '$currentPackage'")
                    previousPackage = currentPackage
                    _foregroundPackage.value = currentPackage
                }
                onTick?.invoke(currentPackage)
                delay(pollingIntervalMs)
            }
        }
    }

    /**
     * Stops the active polling coroutine.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    companion object {
        private const val TAG = "ForegroundAppDetector"
    }
}
