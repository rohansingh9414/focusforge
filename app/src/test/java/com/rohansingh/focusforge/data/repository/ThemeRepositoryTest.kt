package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.domain.models.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRepositoryTest {

    @Test
    fun testParseThemeMode_defaultWhenNullOrEmpty() {
        assertEquals(ThemeMode.SYSTEM, ThemeRepository.parseThemeMode(null))
        assertEquals(ThemeMode.SYSTEM, ThemeRepository.parseThemeMode(""))
        assertEquals(ThemeMode.SYSTEM, ThemeRepository.parseThemeMode("UNKNOWN_MODE"))
    }

    @Test
    fun testParseThemeMode_validModes() {
        assertEquals(ThemeMode.LIGHT, ThemeRepository.parseThemeMode("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeRepository.parseThemeMode("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeRepository.parseThemeMode("SYSTEM"))
    }

    @Test
    fun testThemeModeResolutionToDarkTheme() {
        // LIGHT always maps to darkTheme = false
        val lightDarkTheme = resolveDarkTheme(ThemeMode.LIGHT, isSystemDark = true)
        assertFalse(lightDarkTheme)

        // DARK always maps to darkTheme = true
        val darkDarkTheme = resolveDarkTheme(ThemeMode.DARK, isSystemDark = false)
        assertTrue(darkDarkTheme)

        // SYSTEM maps to system setting
        val systemWhenSystemDark = resolveDarkTheme(ThemeMode.SYSTEM, isSystemDark = true)
        assertTrue(systemWhenSystemDark)

        val systemWhenSystemLight = resolveDarkTheme(ThemeMode.SYSTEM, isSystemDark = false)
        assertFalse(systemWhenSystemLight)
    }

    private fun resolveDarkTheme(themeMode: ThemeMode, isSystemDark: Boolean): Boolean {
        return when (themeMode) {
            ThemeMode.SYSTEM -> isSystemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
}
