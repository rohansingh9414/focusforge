package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rohansingh.focusforge.domain.models.PricingMode
import com.rohansingh.focusforge.domain.models.RewardType

/**
 * Room entity representing a configured reward template.
 * Defined in ECONOMY.md §5 and ROADMAP.md Phase 4.
 */
@Entity(tableName = "reward_templates")
data class RewardTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val unit: String,
    val rewardType: RewardType = RewardType.CUSTOM,
    val pricingMode: PricingMode = PricingMode.AUTO,
    val rupeeCost: Double = 0.0,
    val creditRate: Double = 0.0,
    val isActive: Boolean = true
)
