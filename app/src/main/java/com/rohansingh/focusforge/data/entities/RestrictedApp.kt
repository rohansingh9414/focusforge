package com.rohansingh.focusforge.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an application configured for restriction.
 */
@Entity(tableName = "restricted_apps")
data class RestrictedApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isRestricted: Boolean = true
)
