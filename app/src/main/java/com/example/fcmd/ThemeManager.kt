package com.example.fcmd

import android.content.Context
import android.content.SharedPreferences

/**
 * Theme Manager - handles theme selection, persistence, and switching
 */
class ThemeManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "fcmd_theme_prefs",
        Context.MODE_PRIVATE
    )

    private var currentTheme: UITheme = loadSavedTheme()
    private val listeners = mutableListOf<ThemeChangeListener>()

    companion object {
        private const val KEY_THEME_TYPE = "theme_type"

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Interface for listening to theme changes
     */
    interface ThemeChangeListener {
        fun onThemeChanged(newTheme: UITheme)
    }

    /**
     * Get current active theme
     */
    fun getCurrentTheme(): UITheme = currentTheme

    /**
     * Set new theme and notify listeners
     */
    fun setTheme(themeType: ThemeType) {
        currentTheme = themeType.createTheme()
        saveTheme(themeType)
        notifyListeners()
    }

    /**
     * Register listener for theme changes
     */
    fun addThemeChangeListener(listener: ThemeChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Unregister listener
     */
    fun removeThemeChangeListener(listener: ThemeChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Get all available themes
     */
    fun getAvailableThemes(): List<ThemeInfo> {
        return ThemeType.values().map { type ->
            ThemeInfo(
                type = type,
                theme = type.createTheme(),
                isActive = type.createTheme()::class == currentTheme::class
            )
        }
    }

    /**
     * Load saved theme from preferences
     */
    private fun loadSavedTheme(): UITheme {
        val savedThemeName = prefs.getString(KEY_THEME_TYPE, ThemeType.DEFAULT.name)
        val themeType = try {
            ThemeType.valueOf(savedThemeName ?: ThemeType.DEFAULT.name)
        } catch (e: IllegalArgumentException) {
            ThemeType.DEFAULT
        }
        return themeType.createTheme()
    }

    /**
     * Save theme preference
     */
    private fun saveTheme(themeType: ThemeType) {
        prefs.edit().putString(KEY_THEME_TYPE, themeType.name).apply()
    }

    /**
     * Notify all listeners of theme change
     */
    private fun notifyListeners() {
        listeners.forEach { it.onThemeChanged(currentTheme) }
    }

    /**
     * Data class for theme information
     */
    data class ThemeInfo(
        val type: ThemeType,
        val theme: UITheme,
        val isActive: Boolean
    )
}
