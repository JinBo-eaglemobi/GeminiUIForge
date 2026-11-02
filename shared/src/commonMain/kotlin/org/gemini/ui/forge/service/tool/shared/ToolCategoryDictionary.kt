package org.gemini.ui.forge.service.tool.shared

/**
 * 工具业务分类字典与映射引擎
 *
 * 赋予大模型与 MCP 客户端按业务意图快速聚合与定向过滤工具的能力：
 * - asset: 素材资源与生图类工具
 * - view: 界面排版与图元操作类工具
 * - template: 工程项目与模板管理类工具
 * - discovery: 探索发现与规范阅读元工具类
 * - runtime: 系统运行态与实机视口探测类工具
 * - interaction: 用户问询交互与通知类工具
 */
object ToolCategoryDictionary {

    const val CATEGORY_ASSET = "asset"
    const val CATEGORY_VIEW = "view"
    const val CATEGORY_TEMPLATE = "template"
    const val CATEGORY_DISCOVERY = "discovery"
    const val CATEGORY_RUNTIME = "runtime"
    const val CATEGORY_INTERACTION = "interaction"

    /** 全部官方支持的分类列表 */
    val ALL_CATEGORIES = listOf(
        CATEGORY_ASSET,
        CATEGORY_VIEW,
        CATEGORY_TEMPLATE,
        CATEGORY_DISCOVERY,
        CATEGORY_RUNTIME,
        CATEGORY_INTERACTION
    )

    /**
     * 将用户或大模型输入的任意中英文分类关键词归一化为标准枚举
     */
    fun normalizeCategory(raw: String?): String? {
        val lower = raw?.trim()?.lowercase() ?: return null
        if (lower.isBlank()) return null
        return when (lower) {
            in setOf("asset", "assets", "素材", "资源", "图片", "image", "images", "切片", "抠图") -> CATEGORY_ASSET
            in setOf("view", "views", "layout", "layouts", "排版", "界面", "图元", "canvas", "block", "blocks", "画布") -> CATEGORY_VIEW
            in setOf("template", "templates", "project", "projects", "工程", "项目", "模板") -> CATEGORY_TEMPLATE
            in setOf("discovery", "search", "元工具", "探索", "发现", "skill", "skills", "技能", "file", "spec", "tool", "tools") -> CATEGORY_DISCOVERY
            in setOf("runtime", "system", "系统", "状态", "运行态", "视口", "截图", "status") -> CATEGORY_RUNTIME
            in setOf("interaction", "问询", "交互", "通知", "dialog", "choice", "confirm") -> CATEGORY_INTERACTION
            else -> lower
        }
    }

    /**
     * 判断指定工具归属的业务大类
     */
    fun getToolCategory(toolName: String): String {
        return when (toolName) {
            in setOf(
                "list_assets", "generate_image", "generate_block_asset", "generate_all_assets",
                "crop_image", "remove_background", "remove_image_background",
                "chat_refine_image", "bake_nine_patch", "set_block_reference_image",
                "inspect_block_with_reference", "compare_with_reference",
                "list_matting_presets"
            ) -> CATEGORY_ASSET
            in setOf(
                "apply_view_layout", "calculate_geometry", "add_block",
                "update_block", "move_block", "delete_block",
                "render_template_overlay"
            ) -> CATEGORY_VIEW
            in setOf(
                "list_templates", "get_template", "create_template",
                "import_asset_sheet_pipeline", "analyze_reference_generate_template"
            ) -> CATEGORY_TEMPLATE
            in setOf(
                "discover_tools", "search_tools", "list_tools", "search_skills",
                "read_skill_spec", "read_project_file", "get_current_time", "web_fetch"
            ) -> CATEGORY_DISCOVERY
            in setOf(
                "get_runtime_status", "check_environment", "get_job_status",
                "cancel_job", "list_chat_sessions", "get_traffic_record",
                "invoke_gemini_text", "compose_page_screenshot", "screenshot_window",
                "execute_ui_action_sequence", "get_ui_roadmap", "get_semantic_tree",
                "trigger_initial_verification", "set_project_generating_status"
            ) -> CATEGORY_RUNTIME
            in setOf(
                "ask_user_confirmation", "ask_user_choice", "ask_user_question",
                "notify_user", "click_ui_node", "set_ui_input_text"
            ) -> CATEGORY_INTERACTION
            else -> CATEGORY_DISCOVERY
        }
    }
}
