package io.github.xororz.localdream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.exportDataStore: DataStore<Preferences> by preferencesDataStore(name = "export_settings")

enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp")
}

data class ExportSettings(
    val format: ImageFormat = ImageFormat.PNG,
    val quality: Int = 95, // 1-100, only applies to JPEG and WebP
    val customFolderPath: String? = null,
    val includeMetadata: Boolean = true,
    val autoSave: Boolean = true
)

class ExportPreferences(private val context: Context) {
    companion object {
        private val FORMAT_KEY = stringPreferencesKey("export_format")
        private val QUALITY_KEY = intPreferencesKey("export_quality")
        private val CUSTOM_FOLDER_KEY = stringPreferencesKey("custom_folder_path")
        private val INCLUDE_METADATA_KEY = stringPreferencesKey("include_metadata")
        private val AUTO_SAVE_KEY = stringPreferencesKey("auto_save")
    }
    
    val exportSettings: Flow<ExportSettings> = context.exportDataStore.data.map { preferences ->
        ExportSettings(
            format = try {
                ImageFormat.valueOf(preferences[FORMAT_KEY] ?: ImageFormat.PNG.name)
            } catch (e: IllegalArgumentException) {
                ImageFormat.PNG
            },
            quality = preferences[QUALITY_KEY] ?: 95,
            customFolderPath = preferences[CUSTOM_FOLDER_KEY],
            includeMetadata = preferences[INCLUDE_METADATA_KEY]?.toBoolean() ?: true,
            autoSave = preferences[AUTO_SAVE_KEY]?.toBoolean() ?: true
        )
    }
    
    suspend fun updateFormat(format: ImageFormat) {
        context.exportDataStore.edit { preferences ->
            preferences[FORMAT_KEY] = format.name
        }
    }
    
    suspend fun updateQuality(quality: Int) {
        context.exportDataStore.edit { preferences ->
            preferences[QUALITY_KEY] = quality.coerceIn(1, 100)
        }
    }
    
    suspend fun updateCustomFolder(path: String?) {
        context.exportDataStore.edit { preferences ->
            if (path != null) {
                preferences[CUSTOM_FOLDER_KEY] = path
            } else {
                preferences.remove(CUSTOM_FOLDER_KEY)
            }
        }
    }
    
    suspend fun updateIncludeMetadata(include: Boolean) {
        context.exportDataStore.edit { preferences ->
            preferences[INCLUDE_METADATA_KEY] = include.toString()
        }
    }
    
    suspend fun updateAutoSave(autoSave: Boolean) {
        context.exportDataStore.edit { preferences ->
            preferences[AUTO_SAVE_KEY] = autoSave.toString()
        }
    }
}
