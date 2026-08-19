package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the user's economy wallet.
 * Defined in ECONOMY.md §2 and ROADMAP.md Phase 8.
 */
@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: Int = 1,
    val creditBalance: Double = 0.0,
    val rupeeBalance: Double = 0.0,
    val screenTimeMinutes: Int = 0,
    val lastDailyGrantDate: String? = null,
    val totalXp: Long = 0L
)
