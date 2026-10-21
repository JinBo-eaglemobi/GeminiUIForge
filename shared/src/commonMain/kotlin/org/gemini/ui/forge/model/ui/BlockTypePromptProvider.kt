package org.gemini.ui.forge.model.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.readResourceBytesSync

/**
 * 图元组件生图提示词提供者。
 *
 * 【纯净律与架构规范】：
 * 提示词内容 100% 独立存储于外部文件 prompts/block_type_prompts.json，
 * UIBlockType 枚举自身零提示词内容硬编码，使用时按需懒加载并进入内存缓存。
 */
object BlockTypePromptProvider {
    private const val TAG = "BlockTypePromptProvider"
    private var promptMap: Map<String, String>? = null

    fun getPrompt(type: UIBlockType): String {
        promptMap?.let { return it[type.name] ?: it["DEFAULT"] ?: "" }
        return try {
            val bytes = readResourceBytesSync("prompts/block_type_prompts.json")
            if (bytes.isEmpty()) return ""
            val jsonText = bytes.decodeToString()
            val json = Json.parseToJsonElement(jsonText).jsonObject
            val map = json.mapValues { it.value.jsonPrimitive.content }
            promptMap = map
            map[type.name] ?: map["DEFAULT"] ?: ""
        } catch (e: Exception) {
            AppLogger.e(TAG, "按需加载 block_type_prompts.json 失败", e)
            ""
        }
    }

    /**
     * 更新或失效当前内存缓存（当外部定制提示词发生变更时同步）
     */
    fun updateCache(map: Map<String, String>?) {
        promptMap = map
    }
}
