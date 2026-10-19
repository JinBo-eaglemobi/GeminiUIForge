package org.gemini.ui.forge.model.chat

import kotlinx.serialization.Serializable

/**
 * 对话引用的上下文实体标签 (用于在消息气泡中持久化展示)
 */
@Serializable
data class ChatMentionTag(
    val title: String,
    val categoryName: String = "DOC",
    val path: String? = null
)

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
    val timestamp: Long = 0L,
    /** 大模型思维链思考推导过程文本（可折叠） */
    val thought: String? = null,
    /** Google 官方加密思考签名凭据 (用于多轮对话轻量化状态接力与防篡改) */
    val thoughtSignature: String? = null,
    /** Google Interactions API 交互轮次 ID (用于链式多轮会话继承) */
    val interactionId: String? = null,
    /** 本轮 AI 回答的历史变体列表 (用于支持 < 1/2 > 分页切换查看) */
    val variants: List<String> = emptyList(),
    /** 当前激活查看的变体索引 */
    val activeVariantIndex: Int = 0,
    /** 当前回复的模型名称 (如 "Gemini 3.8 Flash") */
    val modelName: String? = null,
    /** 推理生成耗时 (毫秒) */
    val durationMs: Long? = null,
    /** 输入 Token 统计 */
    val promptTokens: Int? = null,
    /** 输出 Token 统计 */
    val candidatesTokens: Int? = null,
    /** 总计消耗 Token */
    val totalTokens: Int? = null,
    /** 是否属于执行失败/请求异常状态 */
    val isError: Boolean = false,
    /** 错误详细描述信息 (用于错误卡片展示) */
    val errorMessage: String? = null,
    /** 本轮提问所附带引用的 @ 工程上下文实体列表 (用于在用户气泡中直观呈现) */
    val attachedMentions: List<ChatMentionTag> = emptyList()
) {
    /** 获取当前生效的有效展示文本 (优先取激活分支变体，并通过视图投影器提取人类可读内容，绝不倾倒纯 JSON) */
    val effectiveText: String
        get() {
            val raw = if (variants.isNotEmpty() && activeVariantIndex in variants.indices) {
                variants[activeVariantIndex]
            } else {
                prompt.ifBlank { textZh.ifBlank { textEn } }
            }
            return org.gemini.ui.forge.service.ai.ChatPayloadViewProjector.projectDisplayContent(raw).text
        }

    /** 获取当前生效的有效思维链思考过程 (优先取已提取的 thought，若无则从原始报文中动态投影) */
    val effectiveThought: String?
        get() {
            if (!thought.isNullOrBlank()) return thought
            val raw = if (variants.isNotEmpty() && activeVariantIndex in variants.indices) {
                variants[activeVariantIndex]
            } else {
                prompt.ifBlank { textZh.ifBlank { textEn } }
            }
            return org.gemini.ui.forge.service.ai.ChatPayloadViewProjector.projectDisplayContent(raw).thoughtText
        }
}

/**
 * 独立功能域会话对象
 *
 * @param id 会话唯一ID
 * @param scopeId 功能环境或模块所属ID（如 blockId / scopeKey）
 * @param title 会话展示标题
 * @param messages 对话消息时间线列表
 * @param designMemoryContext 提炼后的紧凑设计记忆与约束摘要
 * @param latestInteractionId 最近一次 Interactions API 交互 ID
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
    val latestInteractionId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
