package org.gemini.ui.forge.service.mcp

/**
 * MCP 通信指令类别分类枚举
 */
enum class McpCommandCategory(val displayName: String) {
    ALL("全部"),
    PROTOCOL("协议核心"),
    TEMPLATE("模板与架构"),
    ASSET("资产与生成"),
    INTERACTION("人机交互"),
    AI_PROMPT("AI与提示词"),
    RUNTIME("系统与感知"),
    UI_ACTION("前端真实UI驱动")
}

/**
 * MCP 指令/工具结构化展示条目
 */
data class McpCommandEntry(
    val name: String,
    val category: McpCommandCategory,
    val description: String,
    val isTool: Boolean = true
)

/**
 * MCP 通信指令与业务工具说明字典
 * 支持连接 [McpToolRegistry] 动态响应并获取所有实时生效的工具，
 * 供调试监控界面与帮助手册实时展示每次交互的操作含义与业务意图。
 */
object McpCommandDictionary {

    /** 协议级基础交互指令映射 */
    private val protocolCommands: Map<String, String> = mapOf(
        "initialize" to "客户端初次建立连接，协商协议版本 (2024-11-05) 与客户端能力 (Capabilities)",
        "notifications/initialized" to "客户端确认协议握手完成，正式进入通信就绪状态",
        "tools/list" to "客户端查询当前服务器注册开放的所有可用 MCP 业务工具清单及参数 Schema",
        "ping" to "连通性轻量心跳探测",
        "disconnect" to "管理员手动终止该客户端的会话连接"
    )

    /** 业务工具的高质感中文简注兜底（若工具自身的 description 包含详细 schema 说明，可优先融合） */
    private val toolShortNotes: Map<String, String> = mapOf(
        "analyze_reference_generate_template" to "【UI模板生成核心】调用 Gemini 视觉多模态大模型分析设计参考图，提取页面架构、图元相对坐标与双语提示词，并直接生成完整工程模板",
        "create_template" to "在工程库中新建一个 UI 模板工程，初始化画布基准尺寸 (宽/高) 与根容器图元",
        "get_template" to "获取指定模板的完整 ProjectState 架构树（包含所有页面、图元坐标、属性及资源路径）",
        "list_templates" to "列出本地工程库中的所有 UI 模板及其创建时间、页面规模与基础元数据",
        "generate_block_asset" to "使用 Gemini 图像生成引擎为指定图元渲染高质量美术资产并自动绑定落盘",
        "generate_all_assets" to "全量批量并发执行：为模板中所有尚未烘焙的图元渲染 AI 视觉美术资产",
        "chat_refine_image" to "针对现有图元图像发起多轮自然语言微调设计会话，重塑光影细节或局部重绘",
        "set_block_reference_image" to "为图元设置专属生图参考底图（支持本地文件、http/https 网络图片或从大图按坐标精准裁切）",
        "update_block" to "精确修改指定图元的属性配置、显示文本、生图提示词 (Prompt) 或组件类型",
        "move_block" to "调整指定图元的相对层级关系（置顶、置底、上移、下移）或改变其局部相对矩形 (Bounds)",
        "delete_block" to "从工程当前页面的层级树中安全移除指定的图元节点",
        "bake_nine_patch" to "对图元资产执行九宫格拉伸安全区烘焙，保证大屏缩放时边缘与圆角不发生形变",
        "remove_background" to "调用本地离线 Python AI 引擎 (rembg) 对指定图像执行高精度透明抠图去背",
        "compare_with_reference" to "将实机页面截图与原始设计参考图执行逐像素对齐与网格差异比对分析",
        "compose_page_screenshot" to "通过 Skia 引擎离屏渲染拼装整页所有图元，生成高清晰度的完整全景预览截图",
        "screenshot_window" to "执行操作系统级原生屏幕截图，获取当前软件界面的视觉呈现",
        "check_env" to "诊断本地运行环境（Python, rembg, pillow 依赖自检与版本状态）",
        "list_chat_sessions" to "读取指定图元的多轮视觉微调设计历史会话记录与思维链状态",
        "get_traffic_record" to "读取大模型推理时上行发送的 Prompt 文本与下行返回的原始通信报文",
        "ask_user_confirmation" to "【安全门禁】向桌面操作者弹出拟真二元确认弹窗，等待用户显式授权或拒绝",
        "ask_user_choice" to "【决策单选】向桌面操作者弹出带有圆角微卡片选项的决策弹窗，等待用户做出一项选择",
        "notify_user" to "【轻量通知】向桌面端全局分发浮动 Toast 气泡或系统通知，向用户汇报当前进展",
        "invoke_gemini_text" to "【大模型文本直调】直接调用 Gemini 大语言模型生成结构化文本、分析代码或翻译",
        "list_prompts" to "【提示词资产】列出应用内置的所有高质量提示词模板元数据及其类别",
        "get_prompt" to "【提示词读取】读取指定系统内置或用户自定义的高阶提示词模板内容",
        "save_prompt" to "【提示词保存】持久化保存用户微调或全新编写的高级提示词模板",
        "reset_prompt" to "【提示词重置】将指定的提示词恢复为出厂官方初始版本",
        "list_matting_presets" to "【抠图预设】列出高质量透明抠图与材质风格预设清单",
        "get_runtime_status" to "【系统感知】感知当前桌面端运行状态（内存、活跃模板、MCP 节点、窗口尺寸）",
        "execute_ui_action_sequence" to "【真实UI模拟】在当前 Compose 活跃工作区中，在 UI 线程真实分发执行一系列连贯的人工交互指令",
        "inspect_block_with_reference" to "【一键隔离比对】针对指定图元一键隔离（隐藏其余模块）-> 聚焦画布居中 -> 开启参考图半透明叠加 -> 捕获画面"
    )

    /**
     * 获取指定方法或工具的中文功能解释
     */
    fun getCommandDescription(methodOrTool: String): String {
        val cleanName = methodOrTool.trim().removePrefix("tools/call: ").removePrefix("tools/call:").trim()

        // 1. 优先查阅协议级
        protocolCommands[cleanName]?.let { return it }

        // 2. 查阅本地精炼中文释义表
        toolShortNotes[cleanName]?.let { return it }

        // 3. 动态查阅当前注册的 MCP 工具定义
        val dynamicTool = McpToolRegistry.findTool(cleanName)
        if (dynamicTool != null && dynamicTool.description.isNotBlank()) {
            return dynamicTool.description
        }

        // 4. 模糊协议前缀兜底
        return when {
            cleanName.startsWith("tools/call") -> "调用 MCP 业务工具执行具体操作"
            cleanName.startsWith("tools/") -> "MCP 工具子系统协议交互"
            cleanName.startsWith("resources/") -> "MCP 资源子系统查询交互"
            cleanName.startsWith("prompts/") -> "MCP 提示词子系统交互"
            else -> "MCP 标准协议交互指令"
        }
    }

    /**
     * 自动归类工具所属分类
     */
    fun categorizeCommand(name: String): McpCommandCategory {
        return when {
            protocolCommands.containsKey(name) -> McpCommandCategory.PROTOCOL
            name in setOf("create_template", "get_template", "list_templates", "update_block", "move_block", "delete_block", "analyze_reference_generate_template", "set_block_reference_image") -> McpCommandCategory.TEMPLATE
            name in setOf("generate_block_asset", "generate_all_assets", "chat_refine_image", "remove_background", "bake_nine_patch", "compose_page_screenshot", "compare_with_reference", "screenshot_window", "list_matting_presets") -> McpCommandCategory.ASSET
            name in setOf("ask_user_confirmation", "ask_user_choice", "notify_user") -> McpCommandCategory.INTERACTION
            name in setOf("invoke_gemini_text", "list_prompts", "get_prompt", "save_prompt", "reset_prompt") -> McpCommandCategory.AI_PROMPT
            name in setOf("check_env", "list_chat_sessions", "get_traffic_record", "get_runtime_status") -> McpCommandCategory.RUNTIME
            name in setOf("execute_ui_action_sequence", "inspect_block_with_reference") -> McpCommandCategory.UI_ACTION
            else -> McpCommandCategory.ALL
        }
    }

    /**
     * 获取所有实时支持的指令与工具条目（协议指令 + 动态注册中心工具全量融合）
     */
    fun getAllDynamicEntries(): List<McpCommandEntry> {
        val result = mutableListOf<McpCommandEntry>()

        // 1. 注入协议级指令
        for ((cmd, desc) in protocolCommands) {
            result.add(
                McpCommandEntry(
                    name = cmd,
                    category = McpCommandCategory.PROTOCOL,
                    description = desc,
                    isTool = false
                )
            )
        }

        // 2. 从 McpToolRegistry 动态读取所有已注册的工具
        val currentRegisteredTools = McpToolRegistry.tools.value
        val handledToolNames = mutableSetOf<String>()

        for (tool in currentRegisteredTools) {
            val shortNote = toolShortNotes[tool.name]
            val desc = if (!shortNote.isNullOrBlank()) {
                shortNote
            } else {
                tool.description
            }
            result.add(
                McpCommandEntry(
                    name = tool.name,
                    category = categorizeCommand(tool.name),
                    description = desc,
                    isTool = true
                )
            )
            handledToolNames.add(tool.name)
        }

        // 3. 容错补齐：若有本地静态释义表中有记录但尚未在注册中心初始化的工具
        for ((toolName, desc) in toolShortNotes) {
            if (toolName !in handledToolNames) {
                result.add(
                    McpCommandEntry(
                        name = toolName,
                        category = categorizeCommand(toolName),
                        description = desc,
                        isTool = true
                    )
                )
            }
        }

        return result
    }

    /**
     * 兼容旧版调用，返回所有指令条目的 (name, description) 键值对
     */
    fun getAllEntries(): List<Pair<String, String>> =
        getAllDynamicEntries().map { it.name to it.description }
}
