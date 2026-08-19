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
import com.rohansingh.focusforge.MainActivity
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.ScreenTimeManager
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
 * Foreground service foundation and engine that continuously runs ForegroundAppDetector
 * and ScreenTimeManager when restricted applications are configured and active.
 */
class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: ForegroundAppDetector
    private lateinit var restrictedAppRepository: RestrictedAppRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var screenTimeManager: ScreenTimeManager
    private lateinit var powerManager: PowerManager

    private var isBlockerActive = false
    private var lastBlockedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing AppMonitoringService")
        createNotificationChannel()
        detector = ForegroundAppDetector(applicationContext)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        val db = AppDatabase.getDatabase(applicationContext)
        restrictedAppRepository = RestrictedAppRepository(db.restrictedAppDao())
        walletRepository = WalletRepository(db.walletDao())
        screenTimeManager = ScreenTimeManager(
            walletRepository = walletRepository,
            restrictedAppRepository = restrictedAppRepository
        )

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
            val status = screenTimeManager.processTick(
                currentPackage = currentPackage,
                isInteractive = isInteractive,
                currentTimeMs = System.currentTimeMillis()
            )

            // If current package is FocusForge or null/unrestricted, reset blocker flag
            if (currentPackage == null || currentPackage == packageName || !status.isRestricted) {
                isBlockerActive = false
                lastBlockedPackage = null
            }

            if (status.shouldBlock && currentPackage != null && currentPackage != packageName) {
                // If blocker is not currently active or package changed, trigger blocker
                if (!isBlockerActive || lastBlockedPackage != currentPackage) {
                    isBlockerActive = true
                    lastBlockedPackage = currentPackage
                    triggerBlocker(currentPackage)
                }
            }
        }
    }

    private fun triggerBlocker(blockedPackage: String) {
        Log.d(TAG, "Triggering BlockerActivity for: $blockedPackage")
        val intent = Intent(this, BlockerActivity::class.java).apply {
            putExtra(BlockerActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun stopMonitoringService() {
        Log.d(TAG, "Stopping AppMonitoringService")
        detector.stopMonitoring()
        screenTimeManager.resetTrackingState()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: AppMonitoringService destroyed")
        detector.stopMonitoring()
        screenTimeManager.resetTrackingState()
        serviceScope.cancel()
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusForge Restriction Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors restricted application usage"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusForge Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val TAG = "AppMonitoringService"
        const val CHANNEL_ID = "focusforge_app_monitoring"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_MONITORING = "com.rohansingh.focusforge.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.rohansingh.focusforge.STOP_MONITORING"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AppMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
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
