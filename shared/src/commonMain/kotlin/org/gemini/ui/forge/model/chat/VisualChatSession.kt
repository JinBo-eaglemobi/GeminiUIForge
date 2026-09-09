package org.gemini.ui.forge.model.chat

import kotlinx.serialization.Serializable

/**
 * 视觉对话消息项
 *
 * @param id 消息唯一ID
 * @param role 发送者角色：user 或 model
 * @param prompt 真实上传给 AI 或 AI 真实回复的完整纯正内容（所发即所存）
 * @param textZh 中文描述或提示词（兼容保留字段）
 * @param textEn 英文描述或提示词（兼容保留字段）
 * @param inputImageUris 输入参考图或上一轮选区图像 URI 列表
 * @param generatedImageUri 模型生成的图像本地或网络 URI
 * @param isCompressed 是否已被上下文提炼引擎压缩/归档
 * @param timestamp 时间戳
 */
@Serializable
data class VisualChatMessage(
    val id: String,
    val role: String,
    val prompt: String = "",
    val textZh: String = "",
    val textEn: String = "",
    val inputImageUris: List<String> = emptyList(),
    val generatedImageUri: String? = null,
    val isCompressed: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * 独立功能域会话对象
 *
 * @param id 会话唯一ID
 * @param scopeId 功能环境或模块所属ID（如 blockId / scopeKey）
 * @param title 会话展示标题
 * @param messages 对话消息时间线列表
 * @param designMemoryContext 提炼后的紧凑设计记忆与约束摘要
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 */
@Serializable
data class VisualChatSession(
    val id: String,
    val scopeId: String,
    val title: String,
    val messages: List<VisualChatMessage> = emptyList(),
    val designMemoryContext: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
