package io.github.xororz.localdream.data

/**
 * Predefined parameter presets for different quality levels
 */
data class ParameterPreset(
    val name: String,
    val description: String,
    val steps: Int,
    val cfg: Float,
    val icon: String = "⚡" // Emoji icon for UI
)

object ParameterPresets {
    val DRAFT = ParameterPreset(
        name = "Draft",
        description = "Fast preview (5-10s)",
        steps = 10,
        cfg = 5f,
        icon = "⚡"
    )
    
    val STANDARD = ParameterPreset(
        name = "Standard",
        description = "Balanced quality (15-25s)",
        steps = 20,
        cfg = 7f,
        icon = "⭐"
    )
    
    val HIGH = ParameterPreset(
        name = "High",
        description = "High quality (30-45s)",
        steps = 30,
        cfg = 8f,
        icon = "💎"
    )
    
    val ULTRA = ParameterPreset(
        name = "Ultra",
        description = "Maximum quality (60-90s)",
        steps = 50,
        cfg = 9f,
        icon = "🔥"
    )
    
    val ALL_PRESETS = listOf(DRAFT, STANDARD, HIGH, ULTRA)
    
    /**
     * Get preset by name
     */
    fun getPresetByName(name: String): ParameterPreset? {
        return ALL_PRESETS.find { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * Get recommended preset based on device and runtime
     */
    fun getRecommendedPreset(isNPU: Boolean, hasHighRAM: Boolean): ParameterPreset {
        return when {
            isNPU && hasHighRAM -> HIGH // NPU with good RAM can handle high quality
            isNPU -> STANDARD // NPU with normal RAM
            hasHighRAM -> STANDARD // CPU/GPU with good RAM
            else -> DRAFT // CPU/GPU with limited RAM
        }
    }
}
