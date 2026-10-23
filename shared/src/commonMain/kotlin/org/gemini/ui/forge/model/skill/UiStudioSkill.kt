package org.gemini.ui.forge.model.skill

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UI Studio 技能定义实体（标准 AI 智能体规范）
 *
 * 采用业界标准命名规范：
 * - [instructions]: 技能提示词指令正文 (Markdown Instructions)
 * - [tools]: 挂载工具名称列表
 * - [triggers]: 意图自动触发关键词列表
 * - [enabled]: 默认启用状态 (默认 true)
 *
 * 保持对旧 JSON 命名与旧属性的完全向后兼容。
 */
@Serializable
data class UiStudioSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String = "",
    val tools: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
    val enabled: Boolean = true,

    // 历史命名序列化兼容
    @SerialName("systemInstructionAddition")
    val legacyInstructionAddition: String? = null,
    @SerialName("toolNames")
    val legacyToolNames: List<String>? = null,
    @SerialName("triggerKeywords")
    val legacyTriggerKeywords: List<String>? = null,
    @SerialName("isEnabled")
    val legacyIsEnabled: Boolean? = null
) {
    val effectiveInstructions: String
        get() = instructions.ifBlank { legacyInstructionAddition.orEmpty() }

    val effectiveTools: List<String>
        get() = tools.ifEmpty { legacyToolNames.orEmpty() }

    val effectiveTriggers: List<String>
        get() = triggers.ifEmpty { legacyTriggerKeywords.orEmpty() }

    val isSkillEnabled: Boolean
        get() = legacyIsEnabled ?: enabled

    // 历史字段调用兼容
    val systemInstructionAddition: String get() = effectiveInstructions
    val toolNames: List<String> get() = effectiveTools
    val triggerKeywords: List<String> get() = effectiveTriggers
    val isEnabled: Boolean get() = isSkillEnabled
}

/**
 * 技能激活模式枚举
 */
enum class SkillActivationMode {
    /** 智能自动路由模式（根据输入意图或拖入文档自动激活技能） */
    AUTO,
    /** 手动强制启用 */
    ALWAYS_ON,
    /** 禁用所有技能（纯文本聊天） */
    DISABLED
}
