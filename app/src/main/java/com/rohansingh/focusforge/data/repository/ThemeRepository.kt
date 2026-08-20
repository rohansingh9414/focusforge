package com.rohansingh.focusforge.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rohansingh.focusforge.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * DataStore-backed repository for managing user theme preferences.
 */
open class ThemeRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    open val themeMode: Flow<ThemeMode>
        get() = context.themeDataStore.data.map { preferences ->
            val modeName = preferences[PreferencesKeys.THEME_MODE]
            parseThemeMode(modeName)
        }

    open suspend fun getThemeModeOnce(): ThemeMode {
        return themeMode.first()
    }

    open suspend fun updateThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    companion object {
        fun parseThemeMode(name: String?): ThemeMode {
            return when (name) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.DARK.name -> ThemeMode.DARK
                ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
                else -> ThemeMode.SYSTEM
            }
        }
    }
}
