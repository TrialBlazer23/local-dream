package io.github.xororz.localdream.data.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.recentPromptsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_prompts")

@Serializable
data class RecentPrompt(
    val prompt: String,
    val negativePrompt: String,
    val timestamp: Long = System.currentTimeMillis()
)

class RecentPromptsManager(private val context: Context) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    companion object {
        private val RECENT_PROMPTS_KEY = stringPreferencesKey("recent_prompts_list")
        private const val MAX_RECENT_PROMPTS = 20
    }
    
    val recentPrompts: Flow<List<RecentPrompt>> = context.recentPromptsDataStore.data
        .map { preferences ->
            val jsonString = preferences[RECENT_PROMPTS_KEY] ?: "[]"
            try {
                json.decodeFromString<List<RecentPrompt>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    suspend fun addPrompt(prompt: String, negativePrompt: String) {
        if (prompt.isBlank()) return
        
        context.recentPromptsDataStore.edit { preferences ->
            val currentJson = preferences[RECENT_PROMPTS_KEY] ?: "[]"
            val current = try {
                json.decodeFromString<List<RecentPrompt>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            // Remove duplicate if exists
            val filtered = current.filter { 
                it.prompt != prompt || it.negativePrompt != negativePrompt 
            }
            
            // Add new prompt at the beginning
            val newPrompt = RecentPrompt(prompt, negativePrompt)
            val updated = listOf(newPrompt) + filtered
            
            // Keep only MAX_RECENT_PROMPTS
            val trimmed = updated.take(MAX_RECENT_PROMPTS)
            
            preferences[RECENT_PROMPTS_KEY] = json.encodeToString(trimmed)
        }
    }
    
    suspend fun clearAll() {
        context.recentPromptsDataStore.edit { preferences ->
            preferences[RECENT_PROMPTS_KEY] = "[]"
        }
    }
    
    suspend fun removePrompt(prompt: RecentPrompt) {
        context.recentPromptsDataStore.edit { preferences ->
            val currentJson = preferences[RECENT_PROMPTS_KEY] ?: "[]"
            val current = try {
                json.decodeFromString<List<RecentPrompt>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            val updated = current.filter { it != prompt }
            preferences[RECENT_PROMPTS_KEY] = json.encodeToString(updated)
        }
    }
}
