package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording a goal completion event.
 * Defined in ECONOMY.md §4, §10 and ROADMAP.md Phase 3.
 */
@Entity(
    tableName = "goal_logs",
    foreignKeys = [
        ForeignKey(
            entity = GoalTemplate::class,
            parentColumns = ["id"],
            childColumns = ["goalTemplateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalTemplateId"])]
)
data class GoalLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalTemplateId: Long,
    val amountCompleted: Double,
    val creditsEarned: Double,
    val completedAt: Long = System.currentTimeMillis()
)
