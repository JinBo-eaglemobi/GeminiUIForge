package org.gemini.ui.forge.manager

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.looseJson

/**
 * 抠图/改图场景预设提示词项
 */
@Serializable
data class MattingPreset(
    val id: String,
    val nameZh: String,
    val descriptionZh: String,
    val promptZh: String,
    val nameEn: String,
    val descriptionEn: String,
    val promptEn: String
)

/**
 * 专业场景预设提示词方案管理器
 */
class PromptPresetManager(private val storage: LocalFileStorage) {
    private val TAG = "PromptPresetManager"
    private val PRESETS_FILE = "prompts/matting_presets.json"

    /**
     * 加载所有抠图/改图预设方案。
     * 优先从外部本地缓存读取，不存在时读取 Jar 内置出厂资源。
     */
    @OptIn(InternalResourceApi::class)
    suspend fun loadPresets(): List<MattingPreset> {
        // 1. 尝试从外部缓存加载
        try {
            if (storage.exists(PRESETS_FILE)) {
                val content = storage.readFromFile(PRESETS_FILE)
                if (!content.isNullOrBlank()) {
                    return looseJson.decodeFromString<List<MattingPreset>>(content)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read external presets: $PRESETS_FILE", e)
        }

        // 2. 读取内置出厂资源
        return try {
            val jsonBytes = readResourceBytes(PRESETS_FILE)
            looseJson.decodeFromString<List<MattingPreset>>(jsonBytes.decodeToString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read default resource presets: $PRESETS_FILE", e)
            emptyList()
        }
    }
}
