package io.github.xororz.localdream.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "generations")
@TypeConverters(Converters::class)
data class GenerationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val prompt: String,
    val negativePrompt: String,
    val modelId: String,
    val modelName: String,
    val steps: Int,
    val cfg: Float,
    val seed: Long,
    val width: Int,
    val height: Int,
    val denoiseStrength: Float? = null,
    val runtime: String, // "NPU", "CPU", "GPU"
    val generationTime: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val inputImagePath: String? = null,
    val maskImagePath: String? = null,
    val isInpaintMode: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String? = null
)

@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val prompt: String,
    val negativePrompt: String,
    val category: String = "General", // General, Anime, Realistic, Portrait, Landscape, etc.
    val steps: Int = 20,
    val cfg: Float = 7f,
    val denoiseStrength: Float = 0.6f,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val usageCount: Int = 0
)

@Entity(tableName = "generation_tags")
data class TagEntity(
    @PrimaryKey
    val name: String,
    val color: String? = null,
    val usageCount: Int = 0
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}
