package org.gemini.ui.forge.model.api

import kotlinx.serialization.Serializable

/**
 * AI 聊天交互会话消息实体
 *
 * @property role 消息发送者角色（"user" 表示用户提问，"model" 表示 AI 大模型回复）
 * @property text 消息正文文本内容
 */
@Serializable
data class ChatMessage(
    val role: String, // "user" 或 "model"
    val text: String
)