package com.rohansingh.focusforge.domain.models

/**
 * Domain model representing a launchable application on the device.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isRestricted: Boolean = false
)
