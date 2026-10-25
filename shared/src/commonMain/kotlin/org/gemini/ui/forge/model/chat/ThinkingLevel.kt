package org.gemini.ui.forge.model.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.service.ai.GeminiThinkingConfig

/**
 * 跨代际兼容的思考模式等级定义
 *
 * 针对 Google Gemini API 从 2.0 到 3.x / 3.7+ 的演进：
 * 2.0 系列采用 `thinkingBudget` (整数)；
 * 3.x / 3.7+ 系列采用 `thinkingLevel` ("LOW" / "MEDIUM" / "HIGH")；
 * 纯生图模型不传该字段（避免 400 报错）。
 */
@Serializable
enum class ThinkingLevel(
    val displayName: String,
    val levelKey: String,
    val budgetTokens: Int,
    val description: String,
    val shortLabel: String
) {
    DEFAULT("默认模式 (Default)", "AUTO", -1, "大模型官方默认推理设置，自动平衡思考与响应", "默认"),
    LOW("轻度思考 (Low)", "LOW", 1024, "轻量思维链分析，适合快速构思与常规设计", "轻度"),
    MEDIUM("标准思考 (Medium)", "MEDIUM", 4096, "平衡思考与耗时，适合复杂布局与美学推导", "标准"),
    HIGH("深度推理 (High)", "HIGH", 16384, "深度多步思维链分析，适合高难度复杂结构与游戏全案", "深度"),
    OFF("关闭思考", "DISABLED", 0, "跳过思维链推导，极速响应出词", "关闭");

    /**
     * 转换为引擎层 GeminiThinkingConfig（跨代际兼容）。
     * 若模型不支持思考（如纯生图系列），强制返回 null 拦截，调用端不得下发 thinkingConfig。
     */
    fun toGeminiThinkingConfig(model: GeminiModel): GeminiThinkingConfig? {
        val name = model.modelName.lowercase()
        // 生图模型坚决不传 thinkingConfig，彻底防止 400 Invalid argument 报错
        if (!model.supportsThinking || name.contains("image") || name.contains("nano-banana")) return null
        val isGemini3x = name.contains("3.") || name.contains("3-") || name.contains("gemini-3")

        return when (this) {
            OFF -> GeminiThinkingConfig(thinkingBudget = 0, thinkingLevel = null)
            DEFAULT -> if (isGemini3x) {
                GeminiThinkingConfig(thinkingBudget = null, thinkingLevel = "low")
            } else {
                GeminiThinkingConfig(thinkingBudget = -1, thinkingLevel = null)
            }
            LOW, MEDIUM, HIGH -> if (isGemini3x) {
                GeminiThinkingConfig(thinkingBudget = null, thinkingLevel = levelKey.lowercase())
            } else {
                GeminiThinkingConfig(thinkingBudget = budgetTokens, thinkingLevel = null)
            }
        }
    }

    /**
     * 根据目标模型代际特征自适应组装合规的 thinkingConfig JSON 节点。
     * 若模型不支持思考，强制返回 null 拦截，彻底防止 400 Invalid argument 报错。
     */
    fun toThinkingConfigJson(model: GeminiModel): JsonObject? {
        val config = toGeminiThinkingConfig(model) ?: return null
        return buildJsonObject {
            config.thinkingBudget?.let { put("thinkingBudget", it) }
            config.thinkingLevel?.let { put("thinkingLevel", it.uppercase()) }
        }
    }
}
