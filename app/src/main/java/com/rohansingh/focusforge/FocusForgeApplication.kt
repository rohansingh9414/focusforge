package com.rohansingh.focusforge

import android.app.Application
import com.rohansingh.focusforge.services.daily.DailyGrantScheduler

/**
 * Application class for FocusForge.
 * Initializes daily grant background automation scheduling on startup.
 */
class FocusForgeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Ensure unique daily WorkManager automation is scheduled
        DailyGrantScheduler.scheduleDailyGrant(this)
    }
}
