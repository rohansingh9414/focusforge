package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording a reward redemption event.
 * Defined in ECONOMY.md §10 and ROADMAP.md Phase 4.
 */
@Entity(
    tableName = "redemption_logs",
    foreignKeys = [
        ForeignKey(
            entity = RewardTemplate::class,
            parentColumns = ["id"],
            childColumns = ["rewardTemplateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rewardTemplateId"])]
)
data class RedemptionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardTemplateId: Long,
    val unitsRedeemed: Double,
    val creditsSpent: Double,
    val redeemedAt: Long = System.currentTimeMillis()
)
