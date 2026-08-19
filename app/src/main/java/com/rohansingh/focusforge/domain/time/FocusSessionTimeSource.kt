package com.rohansingh.focusforge.domain.time

import android.os.SystemClock
import com.rohansingh.focusforge.BuildConfig

/**
 * Time abstraction interface for Focus Session timing, countdowns, and alarms.
 */
interface FocusSessionTimeSource {
    /**
     * Current wall-clock timestamp in milliseconds.
     */
    fun currentWallClockMs(): Long

    /**
     * Monotonic elapsed realtime in milliseconds.
     */
    fun currentElapsedRealtimeMs(): Long

    /**
     * Time acceleration multiplier (e.g. 1L = 1x real time, 60L = 60x accelerated).
     * Strictly 1L in release and default debug.
     */
    val timeAccelerationFactor: Long get() = 1L
}

/**
 * Default production implementation using system clocks and 1x real-time.
 */
class RealFocusSessionTimeSource : FocusSessionTimeSource {
    override fun currentWallClockMs(): Long = System.currentTimeMillis()

    override fun currentElapsedRealtimeMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Exception) {
        System.nanoTime() / 1_000_000L
    }

    override val timeAccelerationFactor: Long = 1L
}

/**
 * Test-only time source supporting accelerated countdown simulation in explicit test environments.
 * Release builds ignore any factor > 1L to prevent production backdoors.
 */
class TestAcceleratedFocusSessionTimeSource(
    factor: Long = 60L,
    private val delegate: FocusSessionTimeSource = RealFocusSessionTimeSource()
) : FocusSessionTimeSource {

    override val timeAccelerationFactor: Long = if (BuildConfig.DEBUG) {
        factor.coerceAtLeast(1L)
    } else {
        1L // Strictly locked to 1x in release builds
    }

    override fun currentWallClockMs(): Long = delegate.currentWallClockMs()

    override fun currentElapsedRealtimeMs(): Long = delegate.currentElapsedRealtimeMs()
}

/**
 * Debug configuration for Focus Session time acceleration.
 * Default is strictly 1L (1x real elapsed time) across all normal debug & release usage.
 * Can only be modified by explicit test harnesses.
 */
object FocusSessionDebugConfig {
    /**
     * Acceleration multiplier. Defaults to 1L (1x real time).
     */
    var accelerationFactor: Long = 1L
        get() = if (BuildConfig.DEBUG) field else 1L
        set(value) {
            field = if (BuildConfig.DEBUG) value.coerceAtLeast(1L) else 1L
        }
}
