package com.rohansingh.focusforge

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohansingh.focusforge.services.usage.ForegroundAppDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundAppDetectorAndroidTest {

    private lateinit var context: Context
    private lateinit var detector: ForegroundAppDetector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detector = ForegroundAppDetector(context)

        // Ensure usage access and notification permissions are granted via shell command
        val packageName = context.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $packageName GET_USAGE_STATS allow")
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        SystemClock.sleep(500)
    }

    @Test
    fun testPermissionGranted() {
        assertTrue("Usage access permission should be granted", detector.hasUsageAccessPermission())
    }

    @Test
    fun testForegroundDetectionFocusForge() {
        // Launch FocusForge MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        context.startActivity(intent)
        SystemClock.sleep(1000)

        val foregroundApp = detector.getForegroundApp()
        Log.d("TestDetector", "Detected foreground app: $foregroundApp")
        assertEquals("com.rohansingh.focusforge", foregroundApp)
    }

    @Test
    fun testForegroundDetectionSwitchToSettingsAndBack() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

        // 1. Launch Settings app
        val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(settingsIntent)

        var detectedSettings: String? = null
        val startSettingsTime = System.currentTimeMillis()
        for (i in 1..20) {
            SystemClock.sleep(100)
            val current = detector.getForegroundApp()
            if (current == "com.android.settings") {
                detectedSettings = current
                val latency = System.currentTimeMillis() - startSettingsTime
                Log.d("TestDetector", "Settings detected in $latency ms (iteration $i)")
                break
            }
        }
        assertEquals("com.android.settings", detectedSettings)

        // 2. Switch back to FocusForge
        val focusForgeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val startFocusForgeTime = System.currentTimeMillis()
        context.startActivity(focusForgeIntent)

        var detectedFocusForge: String? = null
        for (i in 1..20) {
            SystemClock.sleep(100)
            val current = detector.getForegroundApp()
            if (current == "com.rohansingh.focusforge") {
                detectedFocusForge = current
                val latency = System.currentTimeMillis() - startFocusForgeTime
                Log.d("TestDetector", "FocusForge detected in $latency ms (iteration $i)")
                break
            }
        }
        assertEquals("com.rohansingh.focusforge", detectedFocusForge)
    }

    @Test
    fun testRepeatedAppTransitions() {
        val targetApps = listOf("com.android.settings", "com.rohansingh.focusforge")
        var transitionCount = 0

        for (cycle in 1..3) {
            // Launch Settings
            val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            SystemClock.sleep(800)
            val app1 = detector.getForegroundApp()
            Log.d("TestDetector", "Cycle $cycle App1: $app1")
            if (app1 == "com.android.settings") transitionCount++

            // Launch FocusForge
            val ffIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(ffIntent)
            SystemClock.sleep(800)
            val app2 = detector.getForegroundApp()
            Log.d("TestDetector", "Cycle $cycle App2: $app2")
            if (app2 == "com.rohansingh.focusforge") transitionCount++
        }

        assertEquals("All 6 transitions must be captured accurately", 6, transitionCount)
    }
}
