package io.github.xororz.localdream.data.database

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GenerationRepository(context: Context) {
    private val database = LocalDreamDatabase.getDatabase(context)
    private val generationDao = database.generationDao()
    private val historyDir = File(context.filesDir, "history").apply {
        if (!exists()) mkdirs()
    }

    fun getAllGenerations(): Flow<List<GenerationEntity>> = generationDao.getAllGenerations()

    fun getFavoriteGenerations(): Flow<List<GenerationEntity>> = generationDao.getFavoriteGenerations()

    fun getGenerationsByModel(modelId: String): Flow<List<GenerationEntity>> = 
        generationDao.getGenerationsByModel(modelId)

    fun searchGenerations(query: String): Flow<List<GenerationEntity>> = 
        generationDao.searchGenerations(query)

    suspend fun getGenerationById(id: Long): GenerationEntity? = 
        generationDao.getGenerationById(id)

    suspend fun saveGeneration(
        bitmap: Bitmap,
        prompt: String,
        negativePrompt: String,
        modelId: String,
        modelName: String,
        steps: Int,
        cfg: Float,
        seed: Long,
        width: Int,
        height: Int,
        runtime: String,
        generationTime: String? = null,
        denoiseStrength: Float? = null,
        inputImagePath: String? = null,
        maskImagePath: String? = null,
        isInpaintMode: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val imageFile = File(historyDir, "gen_${timestamp}.png")
        val thumbnailFile = File(historyDir, "thumb_${timestamp}.jpg")

        // Save full image
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Save thumbnail
        val thumbnail = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * 0.25f).toInt(),
            (bitmap.height * 0.25f).toInt(),
            true
        )
        FileOutputStream(thumbnailFile).use { out ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        thumbnail.recycle()

        val generation = GenerationEntity(
            imagePath = imageFile.absolutePath,
            thumbnailPath = thumbnailFile.absolutePath,
            prompt = prompt,
            negativePrompt = negativePrompt,
            modelId = modelId,
            modelName = modelName,
            steps = steps,
            cfg = cfg,
            seed = seed,
            width = width,
            height = height,
            runtime = runtime,
            generationTime = generationTime,
            timestamp = timestamp,
            denoiseStrength = denoiseStrength,
            inputImagePath = inputImagePath,
            maskImagePath = maskImagePath,
            isInpaintMode = isInpaintMode
        )

        generationDao.insertGeneration(generation)
    }

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) = 
        generationDao.updateFavorite(id, isFavorite)

    suspend fun deleteGeneration(id: Long) = withContext(Dispatchers.IO) {
        val generation = generationDao.getGenerationById(id)
        generation?.let {
            // Delete image files
            File(it.imagePath).delete()
            it.thumbnailPath?.let { path -> File(path).delete() }
            generationDao.deleteGenerationById(id)
        }
    }

    suspend fun getGenerationCount(): Int = generationDao.getGenerationCount()

    suspend fun getRecentGenerations(limit: Int = 10): List<GenerationEntity> = 
        generationDao.getRecentGenerations(limit)

    fun getUsedModels(): Flow<List<ModelInfo>> = generationDao.getUsedModels()

    suspend fun cleanupOldGenerations(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        generationDao.deleteOldGenerations(cutoffTime)
    }
}

class PromptPresetRepository(context: Context) {
    private val database = LocalDreamDatabase.getDatabase(context)
    private val presetDao = database.promptPresetDao()

    fun getAllPresets(): Flow<List<PromptPresetEntity>> = presetDao.getAllPresets()

    fun getPresetsByCategory(category: String): Flow<List<PromptPresetEntity>> = 
        presetDao.getPresetsByCategory(category)

    fun getFavoritePresets(): Flow<List<PromptPresetEntity>> = presetDao.getFavoritePresets()

    fun searchPresets(query: String): Flow<List<PromptPresetEntity>> = 
        presetDao.searchPresets(query)

    fun getAllCategories(): Flow<List<String>> = presetDao.getAllCategories()

    suspend fun getPresetById(id: Long): PromptPresetEntity? = presetDao.getPresetById(id)

    suspend fun savePreset(
        name: String,
        prompt: String,
        negativePrompt: String,
        category: String = "General",
        steps: Int = 20,
        cfg: Float = 7f,
        denoiseStrength: Float = 0.6f
    ): Long {
        val preset = PromptPresetEntity(
            name = name,
            prompt = prompt,
            negativePrompt = negativePrompt,
            category = category,
            steps = steps,
            cfg = cfg,
            denoiseStrength = denoiseStrength
        )
        return presetDao.insertPreset(preset)
    }

    suspend fun updatePreset(preset: PromptPresetEntity) = presetDao.updatePreset(preset)

    suspend fun deletePreset(preset: PromptPresetEntity) = presetDao.deletePreset(preset)

    suspend fun incrementUsageCount(id: Long) = presetDao.incrementUsageCount(id)

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) = 
        presetDao.updateFavorite(id, isFavorite)
}
