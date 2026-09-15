package org.gemini.ui.forge.service.mcp

import kotlinx.serialization.json.JsonObject

/**
 * MCP 工具结果内容项
 */
sealed class McpContent {
    /** 纯文本内容项 */
    data class Text(val text: String) : McpContent()

    /**
     * 图像内容项（支持原生回传给大模型做视觉审查与比对）
     * @param base64Data 未经 URI 头包裹的纯 Base64 字符串
     * @param mimeType 图像 MIME 类型（如 image/png、image/webp、image/jpeg）
     */
    data class Image(val base64Data: String, val mimeType: String = "image/png") : McpContent()
}

/**
 * MCP 工具结构化执行结果
 */
data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
) {
    companion object {
        fun text(text: String, isError: Boolean = false): McpToolResult =
            McpToolResult(listOf(McpContent.Text(text)), isError)

        fun image(base64Data: String, mimeType: String = "image/png", message: String? = null): McpToolResult =
            McpToolResult(
                buildList {
                    if (!message.isNullOrBlank()) add(McpContent.Text(message))
                    add(McpContent.Image(base64Data, mimeType))
                }
            )

        fun error(errorMessage: String): McpToolResult =
            McpToolResult(listOf(McpContent.Text(errorMessage)), isError = true)
    }
}

/**
 * MCP 工具行为特征提示注解 (Tool Annotations)
 * 供 AI 客户端执行器决策人机审批、安全沙盒与重试策略。
 */
data class McpToolAnnotations(
    /** 是否为纯只读查询工具（无副作用，可直接免确认执行） */
    val readOnlyHint: Boolean = false,
    /** 是否可能对工作区造成破坏性修改（如删除模块、覆盖模板） */
    val destructiveHint: Boolean = false,
    /** 重复多次执行是否具有幂等性 */
    val idempotentHint: Boolean = false,
    /** 是否涉及与开放互联网大模型或第三方服务交互 */
    val openWorldHint: Boolean = false
)

/**
 * MCP 工具定义抽象契约。
 *
 * 业务层工具仅依赖此纯 Kotlin 接口，与底层 MCP SDK 具体类型完全解耦，
 * 避免未来 SDK 升级带来破坏性接口变更。
 */
interface McpToolDefinition {
    /** 工具唯一标识符（符合 snake_case，如 list_templates） */
    val name: String

    /** 工具功能描述（供 AI 模型理解意图与参数上下文） */
    val description: String

    /** 输入参数的 JSON Schema 定义 */
    val inputSchema: JsonObject

    /** 工具行为注解提示（默认全 false） */
    val annotations: McpToolAnnotations get() = McpToolAnnotations()

    /** 单次调用超时时限（毫秒），默认 60 秒；耗时生图/比对工具可调高 */
    val timeoutMs: Long get() = 60_000L

    /**
     * 执行具体业务调用
     *
     * @param arguments AI 客户端传入的结构化实参
     * @param onProgress 可选的进度通知回调（progress: 0.0f ~ 1.0f, message: 阶段说明）
     * @return 返回给 AI 的结构化多模态内容结果
     */
    suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)? = null
    ): McpToolResult
}
