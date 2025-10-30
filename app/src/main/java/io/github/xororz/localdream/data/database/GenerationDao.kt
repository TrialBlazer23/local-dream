package io.github.xororz.localdream.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generations ORDER BY timestamp DESC")
    fun getAllGenerations(): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteGenerations(): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE modelId = :modelId ORDER BY timestamp DESC")
    fun getGenerationsByModel(modelId: String): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE id = :id")
    suspend fun getGenerationById(id: Long): GenerationEntity?

    @Query("SELECT * FROM generations WHERE prompt LIKE '%' || :query || '%' OR negativePrompt LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchGenerations(query: String): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE :tag IN (SELECT value FROM json_each(tags)) ORDER BY timestamp DESC")
    fun getGenerationsByTag(tag: String): Flow<List<GenerationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneration(generation: GenerationEntity): Long

    @Update
    suspend fun updateGeneration(generation: GenerationEntity)

    @Delete
    suspend fun deleteGeneration(generation: GenerationEntity)

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteGenerationById(id: Long)

    @Query("UPDATE generations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM generations")
    suspend fun getGenerationCount(): Int

    @Query("SELECT * FROM generations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentGenerations(limit: Int): List<GenerationEntity>

    @Query("SELECT DISTINCT modelId, modelName FROM generations ORDER BY timestamp DESC")
    fun getUsedModels(): Flow<List<ModelInfo>>

    @Query("DELETE FROM generations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldGenerations(cutoffTime: Long)
}

data class ModelInfo(
    val modelId: String,
    val modelName: String
)

@Dao
interface PromptPresetDao {
    @Query("SELECT * FROM prompt_presets ORDER BY isFavorite DESC, usageCount DESC, timestamp DESC")
    fun getAllPresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets WHERE category = :category ORDER BY isFavorite DESC, usageCount DESC, timestamp DESC")
    fun getPresetsByCategory(category: String): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets WHERE isFavorite = 1 ORDER BY usageCount DESC, timestamp DESC")
    fun getFavoritePresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): PromptPresetEntity?

    @Query("SELECT * FROM prompt_presets WHERE name LIKE '%' || :query || '%' OR prompt LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPresets(query: String): Flow<List<PromptPresetEntity>>

    @Query("SELECT DISTINCT category FROM prompt_presets ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PromptPresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PromptPresetEntity)

    @Delete
    suspend fun deletePreset(preset: PromptPresetEntity)

    @Query("UPDATE prompt_presets SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsageCount(id: Long)

    @Query("UPDATE prompt_presets SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM generation_tags ORDER BY usageCount DESC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM generation_tags WHERE name LIKE '%' || :query || '%' ORDER BY usageCount DESC")
    fun searchTags(query: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("UPDATE generation_tags SET usageCount = usageCount + 1 WHERE name = :tagName")
    suspend fun incrementTagUsage(tagName: String)

    @Delete
    suspend fun deleteTag(tag: TagEntity)
}
