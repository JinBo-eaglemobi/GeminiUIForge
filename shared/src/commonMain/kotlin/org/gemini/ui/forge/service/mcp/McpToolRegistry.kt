package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.gemini.ui.forge.service.mcp.tools.*

/**
 * MCP 工具注册中心单例。
 */
object McpToolRegistry {
    private val _tools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val tools: StateFlow<List<McpToolDefinition>> = _tools.asStateFlow()

    /** 供底层活跃服务监听 tools/list_changed 通知的回调 */
    var onToolsChangedListener: (() -> Unit)? = null

    init {
        registerBuiltinTools()
    }

    private fun registerBuiltinTools() {
        val builtin = listOf(
            // 原有只读工具
            ListTemplatesTool(),
            GetTemplateTool(),
            CheckEnvTool(),
            // A 组：模板生成与编辑工具
            AnalyzeReferenceGenerateTemplateTool(),
            CreateTemplateTool(),
            UpdateBlockTool(),
            MoveBlockTool(),
            DeleteBlockTool(),
            SetBlockReferenceImageTool(),
            // B 组：资产生成与图像处理工具
            GenerateBlockAssetTool(),
            GenerateAllAssetsTool(),
            ChatRefineImageTool(),
            RemoveBackgroundTool(),
            BakeNinePatchTool(),
            // C 组：离屏渲染与视觉闭环比对工具
            ComposePageScreenshotTool(),
            CompareWithReferenceTool(),
            ScreenshotWindowTool(),
            RenderTemplateOverlayTool(),
            // D 组：会话复盘与通信档案工具
            ListChatSessionsTool(),
            GetTrafficRecordTool(),
            // E 组：人机交互与人工授权门禁工具
            AskUserConfirmationTool(),
            AskUserChoiceTool(),
            NotifyUserTool(),
            // F 组：AI 直调工具
            InvokeGeminiTextTool(),
            // G 组：提示词资产与预设管理工具
            ListPromptsTool(),
            GetPromptTool(),
            SavePromptTool(),
            ResetPromptTool(),
            ListMattingPresetsTool(),
            // H 组：运行感知工具
            GetRuntimeStatusTool(),
            // I 组：前端真实 UI 动作执行与隔离比对工具
            ExecuteUiActionSequenceTool(),
            InspectBlockWithReferenceTool(),
            // J 组：UI 路线图感知与真实人工交互跟随工具
            GetUiRoadmapTool(),
            ClickUiNodeTool(),
            SetUiInputTextTool(),
            GetSemanticTreeTool(),
            TriggerInitialVerificationTool(),
            SetProjectGeneratingStatusTool()
        )
        _tools.value = builtin
    }

    fun registerTool(tool: McpToolDefinition) {
        if (_tools.value.none { it.name == tool.name }) {
            _tools.value = _tools.value + tool
            onToolsChangedListener?.invoke()
        }
    }

    fun registerTools(newTools: List<McpToolDefinition>) {
        val existingNames = _tools.value.map { it.name }.toSet()
        val toAdd = newTools.filter { it.name !in existingNames }
        if (toAdd.isNotEmpty()) {
            _tools.value = _tools.value + toAdd
            onToolsChangedListener?.invoke()
        }
    }

    fun findTool(name: String): McpToolDefinition? =
        _tools.value.firstOrNull { it.name == name }
}
