package org.gemini.ui.forge.service.tool.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolHub
import org.gemini.ui.forge.service.tool.mcp.exclusive.*
import org.gemini.ui.forge.service.tool.shared.*

/**
 * MCP 专属工具注册中心
 *
 * 聚合全量供 MCP 协议客户端调用的业务工具（包含 34 个跨通道共享工具、10 个 MCP 专属人机与 UI 自动化工具，
 * 以及 DiscoverToolsTool 统一被动发现元工具），并向全局 [ToolHub] 注册本源。
 *
 * 【架构红线】
 * 本文件属于 MCP 通道层，严禁任何形式反向 import [org.gemini.ui.forge.service.tool.ai.*]，
 * 彻底杜绝 MCP 与 AI 专属域的跨域耦合。
 */
object McpToolRegistry {

    private val _tools = MutableStateFlow<List<ToolDefinition>>(emptyList())
    val tools: StateFlow<List<ToolDefinition>> = _tools.asStateFlow()

    /** 供底层活跃服务监听 tools/list_changed 通知的回调 */
    var onToolsChangedListener: (() -> Unit)? = null

    init {
        registerBuiltinTools()
        // 向中立聚合中心登记 MCP 领域数据源
        ToolHub.registerSource("mcp") { _tools.value }
    }

    private fun registerBuiltinTools() {
        val builtin = listOf(
            // 元工具：统一工具发现
            DiscoverToolsTool(),

            // 模板与工程管理共享工具
            ListTemplatesTool(),
            GetTemplateTool(),
            CreateTemplateTool(),
            AddBlockTool(),
            UpdateBlockTool(),
            MoveBlockTool(),
            DeleteBlockTool(),
            SetBlockReferenceImageTool(),
            AnalyzeReferenceGenerateTemplateTool(),
            ImportAssetSheetPipelineTool(),

            // 图像生成与资产处理共享工具
            GenerateImageTool(),
            GenerateBlockAssetTool(),
            GenerateAllAssetsTool(),
            ChatRefineImageTool(),
            RemoveBackgroundTool(),
            BakeNinePatchTool(),

            // 离屏渲染与视觉比对共享工具
            ComposePageScreenshotTool(),
            CompareWithReferenceTool(),
            ScreenshotWindowTool(),
            RenderTemplateOverlayTool(),
            InspectBlockWithReferenceTool(),
            ExecuteUiActionSequenceTool(),

            // 会话通信与运行感知共享工具
            ListChatSessionsTool(),
            GetTrafficRecordTool(),
            GetRuntimeStatusTool(),
            CheckEnvTool(),
            GetJobStatusTool(),
            CancelJobTool(),

            // 提示词资产管理共享工具
            ListPromptsTool(),
            GetPromptTool(),
            SavePromptTool(),
            ResetPromptTool(),
            ListMattingPresetsTool(),
            InvokeGeminiTextTool(),

            // MCP 专属：人机交互与决策确认工具 (4 个)
            AskUserConfirmationTool(),
            AskUserChoiceTool(),
            AskUserQuestionTool(),
            NotifyUserTool(),

            // MCP 专属：桌面 UI 自动化驱动与路线图感知工具 (6 个)
            GetUiRoadmapTool(),
            ClickUiNodeTool(),
            SetUiInputTextTool(),
            GetSemanticTreeTool(),
            TriggerInitialVerificationTool(),
            SetProjectGeneratingStatusTool()
        )
        _tools.value = builtin
    }

    fun registerTool(tool: ToolDefinition) {
        if (_tools.value.none { it.name == tool.name }) {
            _tools.value = _tools.value + tool
            onToolsChangedListener?.invoke()
        }
    }

    fun registerTools(newTools: List<ToolDefinition>) {
        val existingNames = _tools.value.map { it.name }.toSet()
        val toAdd = newTools.filter { it.name !in existingNames }
        if (toAdd.isNotEmpty()) {
            _tools.value = _tools.value + toAdd
            onToolsChangedListener?.invoke()
        }
    }

    fun findTool(name: String): ToolDefinition? =
        _tools.value.firstOrNull { it.name == name }
}
