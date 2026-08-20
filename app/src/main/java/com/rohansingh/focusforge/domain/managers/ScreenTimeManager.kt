package com.rohansingh.focusforge.domain.managers

import com.rohansingh.focusforge.data.dao.ScreenTimeLogDao
import com.rohansingh.focusforge.data.entities.ScreenTimeLog
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScreenTimeStatus(
    val currentPackage: String?,
    val isRestricted: Boolean,
    val isInteractive: Boolean,
    val remainingScreenTimeMinutes: Int,
    val shouldBlock: Boolean,
    val minutesDeducted: Int = 0
)

/**
 * Domain engine responsible for managing screen-time session state,
 * calculating qualifying elapsed restricted usage, deducting minutes from Wallet,
 * determining when an application must be blocked, and persisting continuous
 * foreground session usage logs into ScreenTimeLogDao.
 */
class ScreenTimeManager(
    private val walletRepository: WalletRepository,
    private val restrictedAppRepository: RestrictedAppRepository,
    private val screenTimeLogDao: ScreenTimeLogDao? = null,
    private val minuteIntervalMs: Long = 60_000L
) {
    private val mutex = Mutex()

    private var activeRestrictedPackage: String? = null
    private var activeRestrictedAppName: String? = null
    private var lastTickTimestamp: Long = 0L
    private var accumulatedRestrictedTimeMs: Long = 0L
    private var sessionDeductedMinutes: Int = 0

    /**
     * Ends the currently active continuous foreground session and writes
     * ONE ScreenTimeLog row if whole minutes were consumed.
     */
    private suspend fun endAndLogActiveSession(consumedAtMs: Long) {
        val pkg = activeRestrictedPackage
        val minutes = sessionDeductedMinutes
        val appName = activeRestrictedAppName
        if (pkg != null && minutes > 0 && screenTimeLogDao != null) {
            screenTimeLogDao.insertLog(
                ScreenTimeLog(
                    packageName = pkg,
                    appName = appName,
                    minutesConsumed = minutes,
                    consumedAt = consumedAtMs
                )
            )
        }
        resetTrackingState()
    }

    /**
     * Processes a single detection tick.
     *
     * @param currentPackage The currently foreground package name (null if unknown or screen off)
     * @param isInteractive Whether the device screen is on and interactive
     * @param currentTimeMs Timestamp in milliseconds of this tick
     * @return [ScreenTimeStatus] representing the updated state and action needed
     */
    suspend fun processTick(
        currentPackage: String?,
        isInteractive: Boolean,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ScreenTimeStatus = mutex.withLock {
        val wallet = walletRepository.getWalletOnce()
        val currentMinutes = wallet?.screenTimeMinutes ?: 0

        // 1. If screen is non-interactive (off) or no package detected, end session
        if (!isInteractive || currentPackage == null) {
            if (activeRestrictedPackage != null) {
                endAndLogActiveSession(currentTimeMs)
            } else {
                resetTrackingState()
            }
            return ScreenTimeStatus(
                currentPackage = currentPackage,
                isRestricted = false,
                isInteractive = isInteractive,
                remainingScreenTimeMinutes = currentMinutes,
                shouldBlock = false
            )
        }

        // 2. Check if the package is restricted
        val isRestricted = restrictedAppRepository.isAppRestricted(currentPackage)
        if (!isRestricted) {
            if (activeRestrictedPackage != null) {
                endAndLogActiveSession(currentTimeMs)
            } else {
                resetTrackingState()
            }
            return ScreenTimeStatus(
                currentPackage = currentPackage,
                isRestricted = false,
                isInteractive = true,
                remainingScreenTimeMinutes = currentMinutes,
                shouldBlock = false
            )
        }

        // 3. Current package IS restricted
        // If switched to a different restricted app, close previous session logging first
        if (activeRestrictedPackage != null && activeRestrictedPackage != currentPackage) {
            val prevPkg = activeRestrictedPackage
            val prevMinutes = sessionDeductedMinutes
            val prevAppName = activeRestrictedAppName
            if (prevPkg != null && prevMinutes > 0 && screenTimeLogDao != null) {
                screenTimeLogDao.insertLog(
                    ScreenTimeLog(
                        packageName = prevPkg,
                        appName = prevAppName,
                        minutesConsumed = prevMinutes,
                        consumedAt = currentTimeMs
                    )
                )
            }
            // Transition to new restricted app session
            sessionDeductedMinutes = 0
            activeRestrictedPackage = currentPackage
            activeRestrictedAppName = restrictedAppRepository.getAppName(currentPackage) ?: currentPackage
        }

        // If wallet has 0 or fewer minutes, block immediately and end session!
        if (currentMinutes <= 0) {
            if (activeRestrictedPackage != null) {
                endAndLogActiveSession(currentTimeMs)
            } else {
                resetTrackingState()
            }
            return ScreenTimeStatus(
                currentPackage = currentPackage,
                isRestricted = true,
                isInteractive = true,
                remainingScreenTimeMinutes = 0,
                shouldBlock = true
            )
        }

        // 4. User has screen time (> 0). Track elapsed active time.
        var deductedCount = 0
        var newMinutes = currentMinutes

        if (activeRestrictedPackage == null || lastTickTimestamp == 0L) {
            // Starting new restricted session
            activeRestrictedPackage = currentPackage
            activeRestrictedAppName = restrictedAppRepository.getAppName(currentPackage) ?: currentPackage
            lastTickTimestamp = currentTimeMs
            accumulatedRestrictedTimeMs = 0L
            sessionDeductedMinutes = 0
        } else {
            // Continuing active restricted session
            val elapsed = currentTimeMs - lastTickTimestamp
            if (elapsed in 1..(minuteIntervalMs * 5)) {
                accumulatedRestrictedTimeMs += elapsed
            }
            lastTickTimestamp = currentTimeMs
            activeRestrictedPackage = currentPackage
            if (activeRestrictedAppName == null) {
                activeRestrictedAppName = restrictedAppRepository.getAppName(currentPackage) ?: currentPackage
            }

            // Deduct full minutes
            while (accumulatedRestrictedTimeMs >= minuteIntervalMs && newMinutes > 0) {
                accumulatedRestrictedTimeMs -= minuteIntervalMs
                newMinutes = maxOf(0, newMinutes - 1)
                deductedCount++
                sessionDeductedMinutes++
            }

            if (deductedCount > 0 && wallet != null) {
                walletRepository.updateWallet(wallet.copy(screenTimeMinutes = newMinutes))
            }
        }

        val shouldBlock = (newMinutes <= 0)
        if (shouldBlock) {
            endAndLogActiveSession(currentTimeMs)
        }

        return ScreenTimeStatus(
            currentPackage = currentPackage,
            isRestricted = true,
            isInteractive = true,
            remainingScreenTimeMinutes = newMinutes,
            shouldBlock = shouldBlock,
            minutesDeducted = deductedCount
        )
    }

    /**
     * Flushes and logs any active continuous foreground session.
     */
    suspend fun flushActiveSession(currentTimeMs: Long = System.currentTimeMillis()) = mutex.withLock {
        if (activeRestrictedPackage != null) {
            endAndLogActiveSession(currentTimeMs)
        }
    }

    /**
     * Resets the in-memory tracking session state.
     */
    fun resetTrackingState() {
        activeRestrictedPackage = null
        activeRestrictedAppName = null
        lastTickTimestamp = 0L
        accumulatedRestrictedTimeMs = 0L
        sessionDeductedMinutes = 0
    }
}
