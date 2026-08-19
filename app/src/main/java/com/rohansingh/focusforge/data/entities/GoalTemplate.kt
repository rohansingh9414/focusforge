package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a configured goal template.
 * Defined in ECONOMY.md §4 and ROADMAP.md Phase 3.
 */
@Entity(tableName = "goal_templates")
data class GoalTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val unit: String,
    val creditRate: Double,
    val dailyCap: Double = 0.0,
    val recurring: Boolean = true
)
