package org.gemini.ui.forge.service.chat

/**
 * AI 对话异常类型分类枚举
 */
enum class ChatErrorType {
    RATE_LIMIT_429,     // 官方配额受限或并发超限
    OVERLOADED_503,     // 服务过载或临时不可用
    TIMEOUT,            // 网络/套接字连接超时
    SAFETY_BLOCKED,     // 触发 Google 安全合规拦截
    INVALID_PROTOCOL,   // 协议/参数错误 (如 Interactions API 格式不匹配)
    UNKNOWN             // 未知/未明确分类的异常
}

/**
 * 针对分类异常的推荐自愈行动
 */
enum class ChatErrorAction {
    FALLBACK_FLASH_MODEL,     // 平滑降级到轻量版 Gemini Flash 模型
    SWITCH_GENERATE_CONTENT,  // 降级为 GenerateContent 标准协议
    RETRY,                    // 原地重新发起请求
    EDIT_PROMPT               // 提示用户调整提问词
}

/**
 * 结构化分类诊断结果
 */
data class ClassifiedChatError(
    val type: ChatErrorType,
    val friendlyTitle: String,
    val friendlyAdvice: String,
    val originalMessage: String,
    val recommendedAction: ChatErrorAction
)

/**
 * AI 对话网络与服务端异常智能诊断与自愈分析器
 */
object ChatErrorClassifier {

    /**
     * 对原始异常信息进行特征匹配，提取错误类型与推荐自愈策略
     */
    fun classify(rawErrorMessage: String?): ClassifiedChatError {
        val msg = rawErrorMessage.orEmpty()
        val lower = msg.lowercase()

        return when {
            // 429 配额或速率超限
            lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota exceeded") || lower.contains("rate limit") -> {
                ClassifiedChatError(
                    type = ChatErrorType.RATE_LIMIT_429,
                    friendlyTitle = "官方配额受限 (429 Resource Exhausted)",
                    friendlyAdvice = "当前所选模型的并发请求已达到官方上限或额度耗尽。建议临时切换为消耗更低的 Gemini 3.8 Flash 轻量模型以继续流畅会话。",
                    originalMessage = msg,
                    recommendedAction = ChatErrorAction.FALLBACK_FLASH_MODEL
                )
            }
            // 503 或服务过载
            lower.contains("503") || lower.contains("unavailable") || lower.contains("overloaded") || lower.contains("high demand") -> {
                ClassifiedChatError(
                    type = ChatErrorType.OVERLOADED_503,
                    friendlyTitle = "模型服务临时过载 (503 Service Unavailable)",
                    friendlyAdvice = "Google 官方节点当前负载极高，可能正在排队。该错误通常为瞬态波动，稍等片刻即可一键重试。",
                    originalMessage = msg,
                    recommendedAction = ChatErrorAction.RETRY
                )
            }
            // 超时
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("sockettimeout") || lower.contains("connectexception") -> {
                ClassifiedChatError(
                    type = ChatErrorType.TIMEOUT,
                    friendlyTitle = "网络连接超时 (Network Timeout)",
                    friendlyAdvice = "与 Google AI 服务器建立连接超时。请核对本地网络与代理设置是否通畅后点击重试。",
                    originalMessage = msg,
                    recommendedAction = ChatErrorAction.RETRY
                )
            }
            // 安全合规拦截
            lower.contains("safety") || lower.contains("blocked") || lower.contains("harmful") || lower.contains("finish_reason_safety") -> {
                ClassifiedChatError(
                    type = ChatErrorType.SAFETY_BLOCKED,
                    friendlyTitle = "安全审核拦截 (Content Safety Blocked)",
                    friendlyAdvice = "生成内容被模型的安全与合规过滤器判定为敏感。建议调整提示词文案后重新发送。",
                    originalMessage = msg,
                    recommendedAction = ChatErrorAction.EDIT_PROMPT
                )
            }
            // 协议或 Schema 异常
            lower.contains("interactions") || lower.contains("invalid_argument") || lower.contains("400") || lower.contains("schema") -> {
                ClassifiedChatError(
                    type = ChatErrorType.INVALID_PROTOCOL,
                    friendlyTitle = "协议入参或会话异常 (Protocol Schema Error)",
                    friendlyAdvice = "当前高级会话协议校验未通过。建议尝试切换回 GenerateContent 标准协议重新发起。",
                    originalMessage = msg,
                    recommendedAction = ChatErrorAction.SWITCH_GENERATE_CONTENT
                )
            }
            // 兜底
            else -> {
                ClassifiedChatError(
                    type = ChatErrorType.UNKNOWN,
                    friendlyTitle = "服务响应异常 (API Error)",
                    friendlyAdvice = "未能成功获取 AI 模型的回复。您可以核验网络通信档案或直接点击重新生成。",
                    originalMessage = msg.ifBlank { "未捕获到服务详细错误正文（可点击顶部工具栏查看网络通信档案核验原始响应）" },
                    recommendedAction = ChatErrorAction.RETRY
                )
            }
        }
    }
}
