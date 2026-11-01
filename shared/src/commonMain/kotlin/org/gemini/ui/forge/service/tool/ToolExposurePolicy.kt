package org.gemini.ui.forge.service.tool

/**
 * 统一工具暴露策略（双通道唯一策略源）
 *
 * 规范主动声明层（MCP tools/list 与 AI Function Calling 声明）的暴露范围。
 * 核心工具直接开箱可见，非核心工具通过唯一的被动查询工具 [discover_tools] 发现，
 * 但执行层全量通畅，不受此名单限制。
 */
object ToolExposurePolicy {

    /**
     * 双通道统一的核心工具名单。
     * 改动此集合，MCP 与 AI Function Calling 主动声明下次请求同步生效。
     */
    val CORE_TOOL_NAMES: Set<String> = setOf(
        // 1. 唯一全量被动发现元工具
        "discover_tools",
        // 2. UI Studio 核心交互与生图工具
        "apply_view_layout",
        "list_assets",
        "generate_image",
        "generate_block_asset",
        "crop_image",
        "remove_image_background",
        "get_runtime_status",
        // 3. 通用上下文与计算基础工具
        "get_current_time",
        "calculate_geometry",
        "web_fetch",
        "read_project_file",
        "search_skills",
        "read_skill_spec"
    )

    /**
     * AI 通道硬性排除的黑名单（防止递归自调用死循环或纯底层私有探测）。
     * MCP 客户端不受此限制。
     */
    val EXCLUDED_AI_TOOL_NAMES: Set<String> = setOf(
        "invoke_gemini_text",
        "trigger_initial_verification"
    )

    /**
     * 判定工具是否属于核心暴露集
     */
    fun isCoreExposed(name: String): Boolean =
        name in CORE_TOOL_NAMES

    /**
     * 判定工具是否对 AI 声明可见
     */
    fun isExposedToAi(name: String): Boolean =
        isCoreExposed(name) && name !in EXCLUDED_AI_TOOL_NAMES
}
