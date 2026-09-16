package org.gemini.ui.forge.service.mcp

/**
 * MCP 通信指令与业务工具中文说明字典
 * 供调试监控界面实时展示每次交互的操作含义与业务意图
 */
object McpCommandDictionary {

    private val dictionary: Map<String, String> = mapOf(
        // 协议级基础交互指令
        "initialize" to "客户端初次建立连接，协商协议版本 (2024-11-05) 与客户端能力 (Capabilities)",
        "notifications/initialized" to "客户端确认协议握手完成，正式进入通信就绪状态",
        "tools/list" to "客户端查询当前服务器注册开放的所有可用 MCP 业务工具清单及参数 Schema",
        "ping" to "连通性轻量心跳探测",
        "disconnect" to "管理员手动终止该客户端的会话连接",

        // 18 个核心业务工具
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
        "get_traffic_record" to "读取大模型推理时上行发送的 Prompt 文本与下行返回的原始通信报文"
    )

    /**
     * 获取指定方法或工具的中文功能解释
     */
    fun getCommandDescription(methodOrTool: String): String {
        val cleanName = methodOrTool.trim().removePrefix("tools/call: ").removePrefix("tools/call:").trim()
        return dictionary[cleanName] ?: run {
            when {
                cleanName.startsWith("tools/call") -> "调用 MCP 业务工具执行具体操作"
                cleanName.startsWith("tools/") -> "MCP 工具子系统协议交互"
                cleanName.startsWith("resources/") -> "MCP 资源子系统查询交互"
                cleanName.startsWith("prompts/") -> "MCP 提示词子系统交互"
                else -> "MCP 标准协议交互指令"
            }
        }
    }

    /**
     * 获取所有支持的指令字典条目 (用于帮助手册展示)
     */
    fun getAllEntries(): List<Pair<String, String>> = dictionary.toList()
}
