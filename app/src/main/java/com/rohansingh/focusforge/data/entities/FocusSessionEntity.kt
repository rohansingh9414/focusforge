package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted Focus Session.
 * Contains an immutable snapshot of the goal's reward configuration at start time,
 * target duration, status, and dual-clock timestamps for recovery.
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = GoalTemplate::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["goalId"])
    ]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,

    // Immutable Goal Snapshot at Session Start
    val snapshotGoalTitle: String,
    val snapshotGoalUnit: String,
    val snapshotCreditRate: Double,
    val snapshotDailyCap: Double,

    // Session Parameters
    val targetDurationMinutes: Int,
    val status: String, // "RUNNING" or "COMPLETED"

    // Timing Benchmarks
    val startedAtWallClockMs: Long,
    val targetEndWallClockMs: Long,
    val startedAtElapsedRealtimeMs: Long,
    val completedAtWallClockMs: Long? = null
)
