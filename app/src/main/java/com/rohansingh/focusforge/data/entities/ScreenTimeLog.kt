package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording a continuous foreground usage session of a restricted application.
 * Defined in ROADMAP.md Phase 9.
 *
 * Logged once per continuous foreground session when the restricted app leaves the foreground,
 * is blocked, or device becomes non-interactive.
 */
@Entity(
    tableName = "screen_time_logs",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["consumedAt"])
    ]
)
data class ScreenTimeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String? = null,
    val minutesConsumed: Int,
    val consumedAt: Long = System.currentTimeMillis()
)
