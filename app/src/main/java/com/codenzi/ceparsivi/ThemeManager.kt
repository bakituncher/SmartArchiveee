package com.codenzi.ceparsivi

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeManager {

    private const val PREFERENCES_NAME = "ThemePrefs"
    private const val KEY_THEME = "selected_theme"

    enum class ThemeMode(val value: String) {
        LIGHT("light"),
        DARK("dark"),
        SYSTEM("system")
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun applyTheme(themePreference: String) {
        val mode = when (themePreference) {
            ThemeMode.LIGHT.value -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK.value -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getTheme(context: Context): String {
        return getPreferences(context).getString(KEY_THEME, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
    }


    fun setTheme(context: Context, theme: ThemeMode) {
        getPreferences(context).edit { putString(KEY_THEME, theme.value) }

    }
}