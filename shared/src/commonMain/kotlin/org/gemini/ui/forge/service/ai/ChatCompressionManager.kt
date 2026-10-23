package org.gemini.ui.forge.service.ai

import org.gemini.ui.forge.model.app.ApiFlavor
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.chat.VisualChatMessage
import org.gemini.ui.forge.model.chat.VisualChatSession
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.getCurrentTimeMillis

/**
 * 长对话上下文压缩指标快照
 */
data class CompressionStats(
    val totalCount: Int,
    val compressedCount: Int,
    val uncompressedCount: Int,
    val estimatedTokensSaved: Int,
    val hasMemoryContext: Boolean
)

/**
 * UI Studio 长对话智能体上下文压缩与修剪控制器
 *
 * 遵循《AI 智能体通信协议与多层载荷解耦架构规范 (Four-Tier AI Payload Decoupling Specification)》：
 * 1. 【系统人设纯净守恒】：提炼过程与后续生成中，systemInstruction 保持 100% 静态纯净，绝不将动态记忆注入人设；
 * 2. 【记忆数据属性隔离】：提炼产出的设计决策与拓扑结构作为结构化数据存入 [VisualChatSession.designMemoryContext]；
 * 3. 【双通信协议深度适配】：
 *    - [ApiFlavor.GENERATE_CONTENT]：标记旧消息 isCompressed=true，请求 history 仅下发未压缩消息，提问 payload 挂载结晶记忆；
 *    - [ApiFlavor.INTERACTIONS]：执行交互树换锚（Pivot），将 latestInteractionId 置空，在保留本地完整记录的前提下，云端以新根轻量展开。
 */
object ChatCompressionManager {
    private const val TAG = "ChatCompressionManager"

    /** 触发长对话建议/自动压缩的未压缩消息阈值 */
    const val AUTO_COMPRESS_MESSAGE_THRESHOLD = 14

    /** 压缩后保留的最新高保真消息数（默认 4 条，即最近 2 轮对答） */
    const val PRESERVE_RECENT_ROUNDS = 4

    /**
     * 判断当前会话是否建议执行长对话压缩
     */
    fun shouldCompress(session: VisualChatSession): Boolean {
        val uncompressedCount = session.messages.count { !it.isCompressed }
        return uncompressedCount >= AUTO_COMPRESS_MESSAGE_THRESHOLD
    }

    /**
     * 计算当前会话的上下文压缩健康度指标
     */
    fun calculateStats(session: VisualChatSession): CompressionStats {
        val total = session.messages.size
        val compressed = session.messages.count { it.isCompressed }
        val uncompressed = total - compressed
        val compressedChars = session.messages.filter { it.isCompressed }.sumOf {
            it.prompt.length + it.textZh.length + it.textEn.length
        }
        val estimatedSaved = compressedChars / 2
        return CompressionStats(
            totalCount = total,
            compressedCount = compressed,
            uncompressedCount = uncompressed,
            estimatedTokensSaved = estimatedSaved,
            hasMemoryContext = !session.designMemoryContext.isNullOrBlank()
        )
    }

    /**
     * 执行双通信协议适配的长对话上下文压缩与语义结晶
     *
     * @param session 当前会话实体
     * @param apiFlavor 当前生效的通信协议模式
     * @param chatEngine 推理引擎实例
     * @param apiKey 有效 API Key
     * @param customSystemPrompt 项目专属系统人设
     * @return 压缩后更新的 [VisualChatSession]
     */
    suspend fun compressSession(
        session: VisualChatSession,
        apiFlavor: ApiFlavor,
        chatEngine: GeminiChatEngine,
        apiKey: String,
        customSystemPrompt: String = ""
    ): VisualChatSession {
        val totalMsgs = session.messages
        if (totalMsgs.size <= PRESERVE_RECENT_ROUNDS) {
            AppLogger.d(TAG, "消息数量不足 ${PRESERVE_RECENT_ROUNDS} 条，无需压缩")
            return session
        }

        val splitIndex = totalMsgs.size - PRESERVE_RECENT_ROUNDS
        val messagesToCompress = totalMsgs.subList(0, splitIndex)
        val preservedMessages = totalMsgs.subList(splitIndex, totalMsgs.size)

        AppLogger.i(TAG, "正在提炼压缩前序 ${messagesToCompress.size} 条历史消息, 当前协议: $apiFlavor")

        // 构建紧凑对话历史流，截断超长文本以保证摘要 Prompt 聚焦
        val conversationText = messagesToCompress.joinToString("\n") { msg ->
            val role = if (msg.role.equals("user", ignoreCase = true)) "USER" else "AI"
            "[$role]: ${msg.effectiveText.take(500).trim()}"
        }

        val summarizePrompt = """
            你是一位资深的 UI/UX 架构与上下文提炼专家。请对以下前序多轮 UI 界面设计与交互讨论进行结构化权威提炼：
            ---
            $conversationText
            ---
            【提炼要求】：
            1. 核心设计风格与色彩体系：提炼整体视觉基调、主色调、强调色与圆角阴影要求；
            2. 关键图元拓扑：提炼已创建或调整的核心组件名、图元 ID 结构及空间位置关系；
            3. 用户明确偏好与禁忌：提炼用户强调的规范约束（如特定分辨率、网格对齐、不可变动项）；
            4. 当前推进阶段：提炼下一阶段待完成的核心交互任务；
            5. 输出规范：严格使用清晰精炼的 Markdown 分级列表输出，去除冗余废话，字数控制在 350 字以内。
        """.trimIndent()

        val summaryResult = try {
            chatEngine.executeChat(
                request = GeminiChatRequest(
                    apiKey = apiKey,
                    model = GeminiModel.GEMINI_3_8_FLASH,
                    apiFlavor = ApiFlavor.GENERATE_CONTENT, // 摘要提炼统一使用高性价比 REST 模式
                    prompt = summarizePrompt,
                    systemInstruction = "你是一个高效的信息压缩与 UI 设计上下文提炼专家。输出内容务必严谨、结构清晰。"
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "调用 Gemini 提炼会话摘要失败", e)
            null
        }

        val summaryMarkdown = if (summaryResult != null && summaryResult.isSuccess && summaryResult.text.isNotBlank()) {
            summaryResult.text.trim()
        } else {
            session.designMemoryContext ?: "（前序长对话包含 ${messagesToCompress.size} 条交互，涵盖基础布局规划与图元装配）"
        }

        // 标记前序被压缩的历史消息（本地完整保留供查阅，isCompressed 标记为 true）
        val markedCompressedMessages = messagesToCompress.map { it.copy(isCompressed = true) }
        val now = getCurrentTimeMillis()

        return when (apiFlavor) {
            ApiFlavor.GENERATE_CONTENT -> {
                // generateContent 模式：更新本地会话的 designMemoryContext
                session.copy(
                    messages = markedCompressedMessages + preservedMessages,
                    designMemoryContext = summaryMarkdown,
                    updatedAt = now
                )
            }
            ApiFlavor.INTERACTIONS -> {
                // Interactions API 模式：执行交互树换锚（Pivot）！
                // 将 latestInteractionId 置空，下一轮请求将作为全新根节点开启，云端历史归零并挂载记忆桥梁
                session.copy(
                    messages = markedCompressedMessages + preservedMessages,
                    designMemoryContext = summaryMarkdown,
                    latestInteractionId = null,
                    updatedAt = now
                )
            }
        }
    }
}
