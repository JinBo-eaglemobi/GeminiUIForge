package org.gemini.ui.forge.service.tool

/**
 * 工具行为特征提示注解中立契约 (Tool Annotations)。
 *
 * 供 AI 客户端执行器或 MCP 宿主决策人机审批、安全沙盒与重试策略。
 */
data class ToolAnnotations(
    /** 是否为纯只读查询工具（无副作用，可直接免确认执行） */
    val readOnlyHint: Boolean = false,
    /** 是否可能对工作区造成破坏性修改（如删除模块、覆盖模板） */
    val destructiveHint: Boolean = false,
    /** 重复多次执行是否具有幂等性 */
    val idempotentHint: Boolean = false,
    /** 是否涉及与开放互联网大模型或第三方服务交互 */
    val openWorldHint: Boolean = false
)
