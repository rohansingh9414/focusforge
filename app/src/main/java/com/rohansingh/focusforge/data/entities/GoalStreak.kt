package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing the consecutive daily completion streak for a goal.
 * Defined in ROADMAP.md Phase 8.
 */
@Entity(
    tableName = "goal_streaks",
    foreignKeys = [
        ForeignKey(
            entity = GoalTemplate::class,
            parentColumns = ["id"],
            childColumns = ["goalTemplateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["goalTemplateId"], unique = true)
    ]
)
data class GoalStreak(
    @PrimaryKey val goalTemplateId: Long,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: String? = null // Format: YYYY-MM-DD
)
