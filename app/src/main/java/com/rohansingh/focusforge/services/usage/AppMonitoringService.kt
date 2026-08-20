package com.rohansingh.focusforge.services.usage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.MainActivity
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.ScreenTimeManager
import com.rohansingh.focusforge.domain.models.BlockerReason
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import com.rohansingh.focusforge.ui.blocker.BlockerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service foundation and engine that continuously runs ForegroundAppDetector,
 * ScreenTimeManager, and FocusSession strict lockdown monitoring when restricted applications
 * are configured and active.
 */
class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: ForegroundAppDetector
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var focusSessionRepository: FocusSessionRepository
    private lateinit var screenTimeManager: ScreenTimeManager
    private lateinit var powerManager: PowerManager

    // Cached authoritative active session observed from Room
    @Volatile
    private var activeFocusSession: FocusSessionEntity? = null

    // Blocker re-trigger throttling
    private var lastBlockerTriggerTimeMs: Long = 0L
    private var lastBlockedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing AppMonitoringService")
        createNotificationChannel()
        detector = ForegroundAppDetector(applicationContext)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        val app = applicationContext as? FocusForgeApplication ?: FocusForgeApplication.instance
        restrictedAppRepository = app.restrictedAppRepository
        walletRepository = app.walletRepository
        focusSessionRepository = app.focusSessionRepository
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository,
            screenTimeLogDao = app.database.screenTimeLogDao()
        )


        // Authoritative Room observation for active FocusSession
        serviceScope.launch {
            focusSessionRepository.activeSession.collect { entity ->
                activeFocusSession = entity
                Log.d(TAG, "Observed Room active FocusSession change: id=${entity?.id}, status=${entity?.status}, goal=${entity?.snapshotGoalTitle}")
            }
        }

        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")

        if (intent?.action == ACTION_STOP_MONITORING) {
            stopMonitoringService()
            return START_NOT_STICKY
        }

        val notification = buildNotification("Monitoring active app restrictions")
        startForeground(NOTIFICATION_ID, notification)

        startForegroundDetection()

        return START_STICKY
    }

    private fun startForegroundDetection() {
        serviceScope.launch {
            restrictedAppRepository.activeRestrictedPackageNames.collectLatest { restrictedPackages ->
                Log.d(TAG, "Active restricted packages count: ${restrictedPackages.size}")
                if (restrictedPackages.isEmpty()) {
                    Log.d(TAG, "No restricted apps configured. Pausing detector.")
                    detector.stopMonitoring()
                    screenTimeManager.resetTrackingState()
                } else {
                    detector.startMonitoring(
                        scope = serviceScope,
                        pollingIntervalMs = 750L
                    ) { currentPackage ->
                        handleForegroundTick(currentPackage)
                    }
                }
            }
        }
    }

    private fun handleForegroundTick(currentPackage: String?) {
        serviceScope.launch {
            val isInteractive = powerManager.isInteractive
            val session = activeFocusSession
            val isFocusActive = session != null && session.status == FocusSessionStatus.RUNNING.name

            // If current package is FocusForge (including BlockerActivity or MainActivity) or null, reset blocker tracking
            if (currentPackage == null || currentPackage == packageName) {
                lastBlockedPackage = null
                return@launch
            }

            val isRestricted = restrictedAppRepository.isAppRestricted(currentPackage)
            val now = System.currentTimeMillis()

            if (isFocusActive && session != null) {
                // STRICT FOCUS MODE:
                // If foreground app is in RestrictedApp list -> BLOCK IMMEDIATELY
                // Overrides Wallet.screenTimeMinutes entirely, and does NOT consume wallet screen time
                if (isRestricted) {
                    val shouldTrigger = (lastBlockedPackage != currentPackage) ||
                        (now - lastBlockerTriggerTimeMs >= BLOCKER_THROTTLE_MS)

                    if (shouldTrigger) {
                        lastBlockerTriggerTimeMs = now
                        lastBlockedPackage = currentPackage

                        val remainingSeconds = ((session.targetEndWallClockMs - now) / 1000).coerceAtLeast(0).toInt()
                        triggerBlocker(
                            blockedPackage = currentPackage,
                            reason = BlockerReason.FOCUS_SESSION_ACTIVE,
                            goalTitle = session.snapshotGoalTitle,
                            remainingSeconds = remainingSeconds
                        )
                    }
                } else {
                    // Non-restricted app -> allow normally
                    lastBlockedPackage = null
                }
            } else {
                // NORMAL PHASE 7 MODE:
                val status = screenTimeManager.processTick(
                    currentPackage = currentPackage,
                    isInteractive = isInteractive,
                    currentTimeMs = now
                )

                if (!status.isRestricted || !status.shouldBlock) {
                    lastBlockedPackage = null
                } else if (status.shouldBlock) {
                    val shouldTrigger = (lastBlockedPackage != currentPackage) ||
                        (now - lastBlockerTriggerTimeMs >= BLOCKER_THROTTLE_MS)

                    if (shouldTrigger) {
                        lastBlockerTriggerTimeMs = now
                        lastBlockedPackage = currentPackage
                        triggerBlocker(
                            blockedPackage = currentPackage,
                            reason = BlockerReason.REGULAR_SCREEN_TIME_EXHAUSTED
                        )
                    }
                }
            }
        }
    }

    private fun triggerBlocker(
        blockedPackage: String,
        reason: BlockerReason,
        goalTitle: String? = null,
        remainingSeconds: Int = 0
    ) {
        Log.d(TAG, "Triggering BlockerActivity for: $blockedPackage (reason: $reason)")
        val intent = Intent(this, BlockerActivity::class.java).apply {
            putExtra(BlockerActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(BlockerActivity.EXTRA_BLOCKER_REASON, reason.name)
            putExtra(BlockerActivity.EXTRA_GOAL_TITLE, goalTitle)
            putExtra(BlockerActivity.EXTRA_REMAINING_SECONDS, remainingSeconds)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else {
            null
        }

        try {
            if (options != null) {
                startActivity(intent, options)
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BlockerActivity: ${e.message}", e)
        }
    }

    private fun stopMonitoringService() {
        Log.d(TAG, "Stopping AppMonitoringService")
        detector.stopMonitoring()
        serviceScope.launch {
            screenTimeManager.flushActiveSession()
        }
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: AppMonitoringService destroyed")
        detector.stopMonitoring()
        serviceScope.launch {
            screenTimeManager.flushActiveSession()
        }
        _isRunning.value = false
        serviceScope.cancel()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Restriction Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors restricted application usage and focus sessions"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusForge Monitoring")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "AppMonitoringService"
        private const val CHANNEL_ID = "app_monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BLOCKER_THROTTLE_MS = 1500L

        const val ACTION_STOP_MONITORING = "com.rohansingh.focusforge.ACTION_STOP_MONITORING"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AppMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
}
