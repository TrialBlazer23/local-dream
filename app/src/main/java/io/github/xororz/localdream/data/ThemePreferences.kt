package io.github.xororz.localdream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

enum class AppTheme {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM; // Follow system theme
    
    companion object {
        fun fromString(value: String): AppTheme {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                SYSTEM
            }
        }
    }
}

class ThemePreferences(private val context: Context) {
    private val THEME_KEY = stringPreferencesKey("app_theme")
    private val DYNAMIC_COLOR_KEY = stringPreferencesKey("dynamic_color")
    
    val currentTheme: Flow<AppTheme> = context.themeDataStore.data
        .map { preferences ->
            val themeValue = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
            AppTheme.fromString(themeValue)
        }
    
    val useDynamicColor: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR_KEY]?.toBoolean() ?: true
        }
    
    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }
    
    suspend fun setDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled.toString()
        }
    }
}
