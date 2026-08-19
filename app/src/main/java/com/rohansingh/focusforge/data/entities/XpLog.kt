package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording an XP award transaction.
 * Defined in ROADMAP.md Phase 8.
 */
@Entity(
    tableName = "xp_logs",
    foreignKeys = [
        ForeignKey(
            entity = GoalTemplate::class,
            parentColumns = ["id"],
            childColumns = ["goalTemplateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GoalLog::class,
            parentColumns = ["id"],
            childColumns = ["goalLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["goalTemplateId"]),
        Index(value = ["goalLogId"])
    ]
)
data class XpLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalTemplateId: Long,
    val goalLogId: Long,
    val xpEarned: Long,
    val completedAt: Long = System.currentTimeMillis()
)
