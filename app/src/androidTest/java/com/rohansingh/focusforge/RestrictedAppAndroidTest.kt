package com.rohansingh.focusforge

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.services.usage.AppDiscoveryService
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestrictedAppAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RestrictedAppRepository
    private lateinit var discoveryService: AppDiscoveryService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        repository = RestrictedAppRepository(database.restrictedAppDao())
        discoveryService = AppDiscoveryService(context)

        val packageName = context.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $packageName GET_USAGE_STATS allow")
        SystemClock.sleep(500)
    }

    @Test
    fun testAppDiscovery_excludesFocusForgeAndFindsSettings() {
        val apps = discoveryService.getInstalledLaunchableApps()
        assertTrue("Installed apps list should not be empty", apps.isNotEmpty())

        // FocusForge itself must not be in the selectable apps list
        val selfApp = apps.find { it.packageName == context.packageName }
        assertEquals("FocusForge should not be in the list", null, selfApp)

        // Settings or Calendar or another system app should be found
        val settingsApp = apps.find { it.packageName == "com.android.settings" }
        assertNotNull("Settings app should be discovered", settingsApp)
    }

    @Test
    fun testRestrictedAppPersistence() = runBlocking {
        repository.setAppRestricted("com.android.settings", "Settings", true)
        assertTrue(repository.isAppRestricted("com.android.settings"))

        val active = repository.activeRestrictedPackageNames.first()
        assertTrue(active.contains("com.android.settings"))

        // Toggle off
        repository.setAppRestricted("com.android.settings", "Settings", false)
        assertFalse(repository.isAppRestricted("com.android.settings"))
    }

    @Test
    fun testMonitoringServiceLifecycle() {
        AppMonitoringService.start(context)
        SystemClock.sleep(1000)
        assertTrue("AppMonitoringService should be running", AppMonitoringService.isRunning.value)

        AppMonitoringService.stop(context)
        SystemClock.sleep(1000)
        assertFalse("AppMonitoringService should be stopped", AppMonitoringService.isRunning.value)
    }
}
