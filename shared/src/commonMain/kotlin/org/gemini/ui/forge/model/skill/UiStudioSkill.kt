package org.gemini.ui.forge.model.skill

import kotlinx.serialization.Serializable

/**
 * UI Studio 专属技能定义实体
 *
 * @param id 技能唯一标识
 * @param name 技能显示名称
 * @param description 技能作用描述
 * @param triggerKeywords 意图自动触发关键词列表
 * @param systemInstructionAddition 技能专属系统人设指令补丁
 * @param toolNames 挂载的本地工具名称列表 (如 "apply_view_layout")
 * @param isEnabled 是否默认启用
 */
@Serializable
data class UiStudioSkill(
    val id: String,
    val name: String,
    val description: String,
    val triggerKeywords: List<String> = emptyList(),
    val systemInstructionAddition: String = "",
    val toolNames: List<String> = emptyList(),
    val isEnabled: Boolean = true
)

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
