package org.gemini.ui.forge.service

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.utils.AppLogger

/**
 * AI 提示词管理器
 * 负责从本地缓存或应用资源中加载 Prompt 模板
 */
class PromptManager(private val storage: LocalFileStorage) {
    private val TAG = "PromptManager"
    private val PROMPTS_DIR = "prompts"

    /**
     * 获取提示词内容
     * 逻辑：优先从本地存储的 prompts/ 目录下读取；若不存在，则从 Resource 中读取并保存到本地。
     * @param functionName 功能名称，对应文件名（不含扩展名）
     * @return 提示词内容
     */
    @OptIn(InternalResourceApi::class)
    suspend fun getPrompt(functionName: String): String {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"

        // 1. 尝试从本地缓存读取
        try {
            if (storage.exists(relativePath)) {
                val cachedContent = storage.readFromFile(relativePath)
                if (!cachedContent.isNullOrBlank()) {
                    return cachedContent
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read cached prompt: $fileName", e)
        }

        // 2. 本地不存在，从 Resource 读取
        val resourcePath = "prompts/$fileName"
        val defaultContent = try {
            readResourceBytes(resourcePath).decodeToString()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read default prompt from resource: $resourcePath", e)
            ""
        }

        // 3. 将默认内容写入本地缓存以便后续修改
        if (defaultContent.isNotBlank()) {
            try {
                storage.saveToFile(relativePath, defaultContent)
                AppLogger.i(TAG, "Initialized prompt cache: $fileName")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to save prompt to cache: $fileName", e)
            }
        }

        return defaultContent
    }

    /**
     * 清除本地缓存的提示词，强制下次从资源中重置
     */
    suspend fun resetPrompt(functionName: String): Boolean {
        val fileName = "$functionName.txt"
        val relativePath = "$PROMPTS_DIR/$fileName"
        return storage.deleteFile(relativePath)
    }
}
