package com.rohansingh.focusforge.domain.managers

import android.util.Log
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.domain.models.ActiveFocusSession
import com.rohansingh.focusforge.domain.models.FocusSessionStatus
import com.rohansingh.focusforge.domain.time.FocusSessionTimeSource
import com.rohansingh.focusforge.domain.time.RealFocusSessionTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interface for scheduling and cancelling exact completion alarms.
 */
interface FocusSessionAlarmScheduler {
    fun scheduleCompletionAlarm(sessionId: Long, triggerAtWallClockMs: Long)
    fun cancelCompletionAlarm(sessionId: Long)
}

/**
 * Domain engine managing Focus Session lifecycle, Room persistence,
 * monotonic runtime timing, process death/reboot recovery, and
 * automatic goal completion dispatch.
 */
class FocusSessionManager(
    private val focusSessionRepository: FocusSessionRepository,
    private val goalManager: GoalManager,
    private val alarmScheduler: FocusSessionAlarmScheduler? = null,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    val timeSource: FocusSessionTimeSource = RealFocusSessionTimeSource()
) {
    private val mutex = Mutex()

    private val _activeSession = MutableStateFlow<ActiveFocusSession?>(null)
    val activeSession: StateFlow<ActiveFocusSession?> = _activeSession.asStateFlow()

    private var countdownJob: Job? = null

    init {
        // Recover any active session on initialization (process death recovery)
        externalScope.launch {
            restoreSessionState()
        }
    }

    /**
     * Checks if a goal is eligible for Focus Sessions (units of minutes or hours).
     */
    fun isGoalFocusEligible(goal: GoalTemplate): Boolean {
        val unit = goal.unit.trim().lowercase()
        return unit in FOCUS_ELIGIBLE_UNITS
    }

    /**
     * Starts a new Focus Session for an eligible goal.
     * Rejects if another session is already RUNNING.
     */
    suspend fun startSession(goal: GoalTemplate, durationMinutes: Int): Result<ActiveFocusSession> = mutex.withLock {
        if (!isGoalFocusEligible(goal)) {
            return Result.failure(IllegalArgumentException("Goal unit '${goal.unit}' is not eligible for Focus Sessions. Must be minutes or hours."))
        }
        if (durationMinutes <= 0) {
            return Result.failure(IllegalArgumentException("Duration must be greater than 0 minutes."))
        }

        val existingActive = focusSessionRepository.getActiveSessionOnce()
        if (existingActive != null) {
            return Result.failure(IllegalStateException("A Focus Session is already running for goal '${existingActive.snapshotGoalTitle}'."))
        }

        val nowWallClock = timeSource.currentWallClockMs()
        val nowElapsedRealtime = timeSource.currentElapsedRealtimeMs()

        // Production wall-clock end timestamp is ALWAYS real nowWallClock + durationMinutes * 60_000L
        val realDurationMs = durationMinutes * 60_000L
        val targetEndWallClock = nowWallClock + realDurationMs

        val entity = FocusSessionEntity(
            goalId = goal.id,
            snapshotGoalTitle = goal.title,
            snapshotGoalUnit = goal.unit,
            snapshotCreditRate = goal.creditRate,
            snapshotDailyCap = goal.dailyCap,
            targetDurationMinutes = durationMinutes,
            status = FocusSessionStatus.RUNNING.name,
            startedAtWallClockMs = nowWallClock,
            targetEndWallClockMs = targetEndWallClock,
            startedAtElapsedRealtimeMs = nowElapsedRealtime
        )

        val sessionId = focusSessionRepository.insertSession(entity)

        // Schedule background AlarmManager exact trigger with real wall clock target
        alarmScheduler?.scheduleCompletionAlarm(sessionId, targetEndWallClock)

        val active = ActiveFocusSession(
            sessionId = sessionId,
            goalId = goal.id,
            goalTitle = goal.title,
            goalUnit = goal.unit,
            creditRate = goal.creditRate,
            dailyCap = goal.dailyCap,
            targetDurationMinutes = durationMinutes,
            remainingSeconds = durationMinutes * 60,
            status = FocusSessionStatus.RUNNING
        )

        _activeSession.value = active
        startCountdownTicker(active, targetEndWallClock)

        Log.d(TAG, "Started Focus Session #$sessionId for '${goal.title}' ($durationMinutes mins, targetEndWallClock=$targetEndWallClock)")
        return Result.success(active)
    }

    /**
     * Completes a focus session idempotently, awards credits through GoalManager,
     * updates Room, and transitions state to COMPLETED / IDLE.
     */
    suspend fun handleSessionCompletion(sessionId: Long): Result<Double> = mutex.withLock {
        val session = focusSessionRepository.getSessionById(sessionId)
            ?: return Result.failure(IllegalArgumentException("Focus Session #$sessionId not found."))

        if (session.status != FocusSessionStatus.RUNNING.name) {
            Log.d(TAG, "Session #$sessionId already processed (status: ${session.status}). Skipping.")
            return Result.success(0.0)
        }

        stopCountdownTicker()

        // 1. Mark completed in Room
        val nowWallClock = timeSource.currentWallClockMs()
        focusSessionRepository.markSessionCompleted(sessionId, nowWallClock)

        // 2. Convert duration to goal amount based on snapshotted unit
        val unit = session.snapshotGoalUnit.trim().lowercase()
        val amount = if (unit in HOUR_UNITS) {
            session.targetDurationMinutes / 60.0
        } else {
            session.targetDurationMinutes.toDouble()
        }

        // 3. Delegate to authoritative GoalManager using snapshotted parameters
        val goalSnapshot = GoalTemplate(
            id = session.goalId,
            title = session.snapshotGoalTitle,
            unit = session.snapshotGoalUnit,
            creditRate = session.snapshotCreditRate,
            dailyCap = session.snapshotDailyCap,
            recurring = true
        )

        val creditResult = goalManager.completeGoal(goalSnapshot, amount)

        // 4. Update in-memory state
        _activeSession.value = null
        alarmScheduler?.cancelCompletionAlarm(sessionId)

        Log.d(TAG, "Completed Focus Session #$sessionId for '${session.snapshotGoalTitle}', awarded ${creditResult.getOrNull()} credits.")
        return creditResult
    }

    /**
     * Restores active session state after process death or app launch.
     */
    suspend fun restoreSessionState() {
        mutex.withLock {
            val activeEntity = focusSessionRepository.getActiveSessionOnce()
            if (activeEntity == null) {
                _activeSession.value = null
                return@withLock
            }

            val nowWallClock = timeSource.currentWallClockMs()
            if (nowWallClock >= activeEntity.targetEndWallClockMs) {
                // Target end time has passed while process was dead -> complete session now
                Log.d(TAG, "Active session #${activeEntity.id} expired while app was closed. Completing now.")
                // Mark completed directly
                focusSessionRepository.markSessionCompleted(activeEntity.id, nowWallClock)

                val unit = activeEntity.snapshotGoalUnit.trim().lowercase()
                val amount = if (unit in HOUR_UNITS) {
                    activeEntity.targetDurationMinutes / 60.0
                } else {
                    activeEntity.targetDurationMinutes.toDouble()
                }

                val goalSnapshot = GoalTemplate(
                    id = activeEntity.goalId,
                    title = activeEntity.snapshotGoalTitle,
                    unit = activeEntity.snapshotGoalUnit,
                    creditRate = activeEntity.snapshotCreditRate,
                    dailyCap = activeEntity.snapshotDailyCap,
                    recurring = true
                )

                goalManager.completeGoal(goalSnapshot, amount)
                _activeSession.value = null
                alarmScheduler?.cancelCompletionAlarm(activeEntity.id)
            } else {
                // Session is still actively running -> restore countdown and re-arm alarm
                val remainingMs = activeEntity.targetEndWallClockMs - nowWallClock
                val remainingSeconds = (remainingMs / 1000).toInt()

                val active = ActiveFocusSession(
                    sessionId = activeEntity.id,
                    goalId = activeEntity.goalId,
                    goalTitle = activeEntity.snapshotGoalTitle,
                    goalUnit = activeEntity.snapshotGoalUnit,
                    creditRate = activeEntity.snapshotCreditRate,
                    dailyCap = activeEntity.snapshotDailyCap,
                    targetDurationMinutes = activeEntity.targetDurationMinutes,
                    remainingSeconds = remainingSeconds,
                    status = FocusSessionStatus.RUNNING
                )

                _activeSession.value = active
                alarmScheduler?.scheduleCompletionAlarm(activeEntity.id, activeEntity.targetEndWallClockMs)
                startCountdownTicker(active, activeEntity.targetEndWallClockMs)
                Log.d(TAG, "Restored active Focus Session #${activeEntity.id} ($remainingSeconds seconds remaining).")
            }
        }
    }

    private fun startCountdownTicker(session: ActiveFocusSession, targetEndWallClock: Long) {
        val acceleration = timeSource.timeAccelerationFactor.coerceAtLeast(1L)
        countdownJob?.cancel()
        countdownJob = externalScope.launch {
            val startWallClock = timeSource.currentWallClockMs()
            while (isActive) {
                val now = timeSource.currentWallClockMs()
                val elapsedRealMs = now - startWallClock
                val simulatedElapsedMs = elapsedRealMs * acceleration
                val remainingSimulatedMs = (session.targetDurationMinutes * 60_000L) - simulatedElapsedMs

                if (remainingSimulatedMs <= 0L || (acceleration == 1L && now >= targetEndWallClock)) {
                    _activeSession.value = _activeSession.value?.copy(remainingSeconds = 0)
                    countdownJob = null
                    handleSessionCompletion(session.sessionId)
                    break
                }

                val remainingSimulatedSec = (remainingSimulatedMs / 1000L).toInt()
                _activeSession.value = _activeSession.value?.copy(remainingSeconds = remainingSimulatedSec)

                val tickIntervalMs = if (acceleration > 1L) 100L else 1000L
                delay(tickIntervalMs)
            }
        }
    }

    private fun stopCountdownTicker() {
        val job = countdownJob
        countdownJob = null
        job?.cancel()
    }

    companion object {
        private const val TAG = "FocusSessionManager"

        val MINUTE_UNITS = setOf("minute", "minutes", "min", "mins")
        val HOUR_UNITS = setOf("hour", "hours", "hr", "hrs")
        val FOCUS_ELIGIBLE_UNITS = MINUTE_UNITS + HOUR_UNITS
    }
}
