package org.gemini.ui.forge.service.tool

import kotlinx.serialization.json.JsonObject

/**
 * 中立工具定义抽象契约。
 *
 * 业务层与专属工具仅依赖此纯 Kotlin 契约，彻底摆脱单边域命名色彩，
 * 双通道（AI Function Calling 与 MCP tools）共享同一抽象接口与执行语义。
 */
interface ToolDefinition {
    /** 工具唯一标识符（符合 snake_case，如 list_templates） */
    val name: String

    /** 工具功能描述（供 AI 模型理解意图与参数上下文） */
    val description: String

    /** 输入参数的 JSON Schema 定义 */
    val inputSchema: JsonObject

    /** 工具行为注解提示（默认全 false） */
    val annotations: ToolAnnotations get() = ToolAnnotations()

    /** 单次调用超时时限（毫秒），默认 60 秒；耗时生图/比对工具可调高 */
    val timeoutMs: Long get() = 60_000L

    /**
     * 执行具体业务调用
     *
     * @param arguments 调用方传入的结构化实参
     * @param onProgress 可选的进度通知回调（progress: 0.0f ~ 1.0f, message: 阶段说明）
     * @return 返回的结构化多模态内容结果
     */
    suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)? = null
    ): ToolResult
}
