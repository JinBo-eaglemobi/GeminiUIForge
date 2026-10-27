package org.gemini.ui.forge.ui.uistudio.model

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.chat.ThinkingLevel

/**
 * AI 智能设计助理用户偏好持久化数据模型。
 *
 * 核心职能：
 * 记录用户在 UI Studio 工作区底栏选择的模型、思考推理等级与通信协议架构，
 * 支持在项目私有目录 (`templates/{proj}/agent_pref.json`) 与全局偏好中双向持久化，
 * 实现重新打开项目或重启应用后 100% 保持一致。
 */
@Serializable
data class AgentPreference(
    val modelName: String = GeminiModel.GEMINI_3_8_FLASH.modelName,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.DEFAULT,
    val apiFlavor: ApiFlavor = ApiFlavor.INTERACTIONS,
    val temperature: Float = 0.7f
)
