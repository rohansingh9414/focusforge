package com.rohansingh.focusforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohansingh.focusforge.data.repository.ThemeRepository
import com.rohansingh.focusforge.domain.models.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeModeAndroidTest {

    private lateinit var context: Context
    private lateinit var themeRepository: ThemeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        themeRepository = ThemeRepository(context)
    }

    @Test
    fun testThemePersistence_roundTrip() = runBlocking {
        // Set to LIGHT
        themeRepository.updateThemeMode(ThemeMode.LIGHT)
        var current = themeRepository.getThemeModeOnce()
        assertEquals(ThemeMode.LIGHT, current)

        // Set to DARK
        themeRepository.updateThemeMode(ThemeMode.DARK)
        current = themeRepository.getThemeModeOnce()
        assertEquals(ThemeMode.DARK, current)

        // Set to SYSTEM
        themeRepository.updateThemeMode(ThemeMode.SYSTEM)
        current = themeRepository.getThemeModeOnce()
        assertEquals(ThemeMode.SYSTEM, current)
    }

    @Test
    fun testThemePersistence_acrossNewInstance() = runBlocking {
        // Write DARK with first repository instance
        themeRepository.updateThemeMode(ThemeMode.DARK)

        // Create new repository instance pointing to the same Context
        val newRepoInstance = ThemeRepository(context)
        val loaded = newRepoInstance.themeMode.first()
        assertEquals(ThemeMode.DARK, loaded)

        // Reset to SYSTEM
        newRepoInstance.updateThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, newRepoInstance.getThemeModeOnce())
    }
}
