package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.manager.PromptManager
import org.gemini.ui.forge.model.ui.BaseBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.service.mcp.McpInteractionBridge
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 获取应用运行环境状态工具
 */
class GetRuntimeStatusTool(
    private val configManager: ConfigManager = ConfigManager(),
    private val repository: TemplateRepository = TemplateRepository(),
    private val promptManager: PromptManager = PromptManager(LocalFileStorage())
) : ToolDefinition {

    override val name: String = "get_runtime_status"

    override val description: String =
        "获取应用程序当前的整体运行状态，包括软件版本、UI 界面连接状态、当前打开的活跃工作区（工程名称、当前活动视图、当前画布选中的组件 selectedBlock 及其内部子组件摘要、当前激活的容器视口 activeContainer）、已配置的 API 密钥脱敏信息、工作区工程总数及提示词定制统计。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        onProgress?.invoke(0.2f, "正在收集运行时健康数据...")

        val templates = try {
            repository.getTemplates()
        } catch (_: Exception) {
            emptyList()
        }

        val geminiKey = configManager.loadKey("GEMINI_API_KEY")?.ifBlank { null }
            ?: configManager.loadGlobalGeminiKey()?.ifBlank { null }
        val nanobananaKey = configManager.loadKey("NANOBANANA_API_KEY")?.ifBlank { null }
        val baseUrl = configManager.loadKey("GEMINI_BASE_URL")?.ifBlank { null }
            ?: "https://generativelanguage.googleapis.com"

        fun maskKey(key: String?): String {
            if (key.isNullOrBlank()) return "UNSET"
            if (key.length <= 8) return "******"
            return "${key.take(4)}...${key.takeLast(4)}"
        }

        var customizedPromptsCount = 0
        promptManager.promptMetas.forEach { meta ->
            try {
                if (promptManager.isCustomized(meta.id)) {
                    customizedPromptsCount++
                }
            } catch (_: Exception) {}
        }

        fun encodeBlockSummary(block: BaseBlock): JsonObject = buildJsonObject {
            put("id", block.id)
            put("type", block.type.name)
            put("bounds", buildJsonObject {
                put("left", block.bounds.left)
                put("top", block.bounds.top)
                put("width", block.bounds.width)
                put("height", block.bounds.height)
            })
            if (block is UIStudioBlock && !block.assetHash.isNullOrBlank()) {
                put("assetHash", block.assetHash)
            }
            val isContainer = block.isPureContainer || block.type == UIBlockType.CONTAINER || block.children.isNotEmpty()
            put("isContainer", isContainer)
            put("childrenCount", block.children.size)
            if (block.children.isNotEmpty()) {
                put("children", buildJsonArray {
                    block.children.forEach { child ->
                        add(buildJsonObject {
                            put("id", child.id)
                            put("type", child.type.name)
                            put("bounds", buildJsonObject {
                                put("left", child.bounds.left)
                                put("top", child.bounds.top)
                                put("width", child.bounds.width)
                                put("height", child.bounds.height)
                            })
                            if (child is UIStudioBlock && !child.assetHash.isNullOrBlank()) {
                                put("assetHash", child.assetHash)
                            }
                            put("childrenCount", child.children.size)
                        })
                    }
                })
            }
        }

        val studioVm = McpUiActionPipeline.getActiveUiStudioViewModel()
        val workspaceVm = McpUiActionPipeline.getActiveViewModel()

        val resultJson = buildJsonObject {
            put("version", ProjectConfig.VERSION)
            put("uiReachable", McpInteractionBridge.isUiReachable())
            put("currentScreen", UiRoadmapRegistry.currentScreen.value.name)
            put("isUiFollowEnabled", UiRoadmapRegistry.isUiFollowEnabled.value)
            put("isAiExecuting", UiRoadmapRegistry.isAiExecuting.value)
            put("generatingProjectsCount", UiRoadmapRegistry.generatingProjects.value.size)
            put("templatesCount", templates.size)
            put("customizedPromptsCount", customizedPromptsCount)
            put("totalRegisteredPrompts", promptManager.promptMetas.size)
            if (studioVm != null) {
                val st = studioVm.state
                put("activeWorkspace", buildJsonObject {
                    put("mode", "UI_STUDIO")
                    put("projectName", st.project?.projectId ?: "")
                    put("currentViewId", st.currentView?.id ?: "")
                    put("currentViewName", st.currentView?.nameStr ?: "")
                    put("currentViewWidth", st.currentView?.width ?: st.canvasWidth.toInt())
                    put("currentViewHeight", st.currentView?.height ?: st.canvasHeight.toInt())
                    put("selectedBlockId", st.selectedBlockId)
                    val selBlock = st.selectedBlock
                    if (selBlock != null) {
                        put("selectedBlock", encodeBlockSummary(selBlock))
                    } else {
                        put("selectedBlock", JsonNull)
                    }
                    put("activeContainerId", st.activeContainerId)
                    val actContainer = st.activeContainerBlock
                    if (actContainer != null) {
                        put("activeContainer", encodeBlockSummary(actContainer))
                    } else {
                        put("activeContainer", JsonNull)
                    }
                })
            } else if (workspaceVm != null) {
                val st = workspaceVm.state.value
                put("activeWorkspace", buildJsonObject {
                    put("mode", "PROJECT_WORKSPACE")
                    put("projectName", st.projectName)
                    put("currentPageName", st.currentPage?.nameStr ?: "")
                    put("selectedBlockId", st.selectedBlockId)
                    put("selectedBlockIds", buildJsonArray { st.selectedBlockIds.forEach { add(it) } })
                    put("editingGroupId", st.editingGroupId)
                    val selBlock = st.selectedBlock
                    if (selBlock != null) {
                        put("selectedBlock", encodeBlockSummary(selBlock))
                    } else {
                        put("selectedBlock", JsonNull)
                    }
                })
            } else {
                put("activeWorkspace", JsonNull)
            }
            put("apiCredentials", buildJsonObject {
                put("geminiKeyConfigured", !geminiKey.isNullOrBlank())
                put("geminiKeyMasked", maskKey(geminiKey))
                put("nanobananaKeyConfigured", !nanobananaKey.isNullOrBlank())
                put("nanobananaKeyMasked", maskKey(nanobananaKey))
                put("geminiBaseUrl", baseUrl)
            })
        }

        return ToolResult.success(resultJson.toString())
    }
}

