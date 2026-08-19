package com.rohansingh.focusforge.domain.models

/**
 * Lifecycle states for Focus Sessions.
 */
enum class FocusSessionStatus {
    IDLE,
    RUNNING,
    COMPLETED
}

/**
 * UI and domain representation of an active focus session.
 */
data class ActiveFocusSession(
    val sessionId: Long,
    val goalId: Long,
    val goalTitle: String,
    val goalUnit: String,
    val creditRate: Double,
    val dailyCap: Double,
    val targetDurationMinutes: Int,
    val remainingSeconds: Int,
    val status: FocusSessionStatus
)

/**
 * Contextual reason for triggering the blocking overlay.
 */
enum class BlockerReason {
    REGULAR_SCREEN_TIME_EXHAUSTED,
    FOCUS_SESSION_ACTIVE
}
