package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.ui.uistudio.model.UIStudioDataErrorType
import org.gemini.ui.forge.ui.uistudio.utils.StudioDataDiagnostics

/**
 * UI Studio 页面数据完整性与架构异常诊断工具 (diagnose_view_data)
 *
 * 供外部 MCP 客户端或内置 AI 架构师以结构化方式查询指定视图或全工程的数据完整性问题（如冗余 children、悬空引用等），并提供具体修复指引。
 */
class DiagnoseViewDataTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "diagnose_view_data"

    override val description: String =
        "诊断 UI Studio 视图页面的数据完整性与架构规范合规性。检测包括：1. INLINE_PAGE_NESTED (界面级复合单元被内嵌在页面中，未拆分为独立页面引用)；2. CONTRACT_CHILD_INVALID (官方复合组件契约违规或非白名单携带 children)；3. REFERENCE_WITH_CHILDREN (引用图元仍含冗余历史子图元)；4. TARGET_PAGE_NOT_FOUND (引用的独立页面不存在)；5. ROOT_CONFIG_INVALID (页面根控制器/动效配置非法或悬空)。支持按 viewName/viewId 定向筛选或扫描全工程，返回详尽的问题清单及 edit_view_layout 工具的具体修复指引。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 30_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标工程名称。若缺省则自动使用当前活跃工作区的工程。")
            })
            put("viewName", buildJsonObject {
                put("type", "string")
                put("description", "可选：指定要诊断的目标视图页面名称（例如 'MainPage'、'slots_window'）。若缺省则扫描全工程所有视图。")
            })
            put("viewId", buildJsonObject {
                put("type", "string")
                put("description", "可选：指定要诊断的目标视图页面 ID。若缺省则扫描全工程所有视图。")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        onProgress?.invoke(0.2f, "正在定位目标工程状态...")

        val reqProjectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val viewName = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()
        val viewId = arguments["viewId"]?.jsonPrimitive?.contentOrNull?.trim()

        val activeVm = McpUiActionPipeline.getActiveUiStudioViewModel()
        val activeProj = activeVm?.state?.project

        val targetProject: ProjectState? = if (reqProjectName.isNullOrBlank()) {
            activeProj ?: repository.getTemplates().firstOrNull()?.second
        } else {
            if (activeProj?.projectId?.equals(reqProjectName, ignoreCase = true) == true) {
                activeProj
            } else {
                repository.getTemplateByName(reqProjectName)
            }
        }

        if (targetProject == null) {
            return ToolResult.error("未找到目标工程状态。请确认项目名称是否正确，或当前是否已打开有效工程。")
        }

        onProgress?.invoke(0.5f, "正在对工程 [${targetProject.projectId}] 进行深度数据完整性扫描...")

        val allErrors = StudioDataDiagnostics.diagnoseProject(targetProject)

        // 根据 viewName 或 viewId 定向过滤
        val filteredErrors = allErrors.filter { err ->
            var match = true
            if (!viewId.isNullOrBlank() && !err.pageId.equals(viewId, ignoreCase = true)) {
                match = false
            }
            if (!viewName.isNullOrBlank() && !err.pageName.equals(viewName, ignoreCase = true) && !err.pageId.equals(viewName, ignoreCase = true)) {
                match = false
            }
            match
        }

        onProgress?.invoke(0.9f, "正在组装结构化诊断报告与修复指引...")

        val issuesArray = buildJsonArray {
            filteredErrors.forEach { err ->
                add(buildJsonObject {
                    put("pageId", err.pageId)
                    put("pageName", err.pageName)
                    put("blockId", err.blockId)
                    if (err.blockName != null) {
                        put("blockName", err.blockName)
                    }
                    if (err.targetPageId != null) {
                        put("targetPageId", err.targetPageId)
                    }
                    put("errorType", err.errorType.name)
                    put("message", err.message)
                    put("repairGuidance", when (err.errorType) {
                        UIStudioDataErrorType.INLINE_PAGE_NESTED ->
                            "界面级复合单元必须拆分为独立页面！请使用 edit_view_layout 的 operations 流水线：① 以 REPLACE_PAGE 模式新建目标独立页面 (例如 '${err.blockId.replaceFirstChar { it.uppercase() }}View')，将该组件的 children 作为新页面的顶层 blocks 迁入；② 在当前页面中将该组件改为 'type: \"COMPONENT\", targetPageId: \"<新建页面ID>\", children: []' 引用实例。画布横幅亦提供一键自动拆分修复入口。"
                        UIStudioDataErrorType.CONTRACT_CHILD_INVALID ->
                            "该图元违反官方复合组件契约：仅 BUTTON/PROGRESS_BAR/SLIDER/SCROLL_BAR 契约子节点与 LIST 预置 item 允许携带 children，且契约子节点必须为叶子。请移除多余 children 或将其重构成叶子图元。"
                        UIStudioDataErrorType.CONTROLLER_ON_NON_ROOT ->
                            "控制器与动效属于 Root-Only 契约属性，严禁挂载在任何图元子节点上。请移至所属独立页面的根节点声明。"
                        UIStudioDataErrorType.ROOT_CONFIG_INVALID ->
                            "页面根级控制器(controllers)或动效(transitions)配置格式不合规或 target 未命中页面内图元。请检查必填 name/pages/items 字段并确保动效 target 与页面图元 ID 一致。"
                        UIStudioDataErrorType.REFERENCE_WITH_CHILDREN ->
                            "该图元为组件实例，其子组件必须在独立页面 [${err.targetPageId}] 中维护。若需修复并清理宿主冗余子图元，可使用 edit_view_layout 工具对该视图发起排版，保持 targetPageId 不变并将 children 设为空数组，或通过 PATCH 仅保留引用外壳。"
                        UIStudioDataErrorType.TARGET_PAGE_NOT_FOUND ->
                            "引用的独立页面 [${err.targetPageId}] 在工程中不存在。请检查页面 ID 是否拼写错误，或通过 edit_view_layout 的 REPLACE_PAGE 模式新建该页面，或修改 targetPageId 指向已有合法视图。"
                    })
                })
            }
        }

        val inlineNestedCount = filteredErrors.count { it.errorType == UIStudioDataErrorType.INLINE_PAGE_NESTED }
        val refWithChildrenCount = filteredErrors.count { it.errorType == UIStudioDataErrorType.REFERENCE_WITH_CHILDREN }
        val targetNotFoundCount = filteredErrors.count { it.errorType == UIStudioDataErrorType.TARGET_PAGE_NOT_FOUND }
        val otherContractCount = filteredErrors.size - inlineNestedCount - refWithChildrenCount - targetNotFoundCount

        val filterDesc = when {
            !viewName.isNullOrBlank() -> "视图 [$viewName]"
            !viewId.isNullOrBlank() -> "视图 ID [$viewId]"
            else -> "全工程所有视图"
        }

        val summaryText = if (filteredErrors.isEmpty()) {
            "工程 [${targetProject.projectId}] $filterDesc 数据完全健康，未检测到任何架构违规或冗余数据！"
        } else {
            "在工程 [${targetProject.projectId}] $filterDesc 中检测到 ${filteredErrors.size} 处数据异常 (内嵌子页面违规: $inlineNestedCount, 冗余children: $refWithChildrenCount, 悬空引用: $targetNotFoundCount, 其它契约异常: $otherContractCount)。"
        }

        val resultJson = buildJsonObject {
            put("isHealthy", filteredErrors.isEmpty())
            put("projectName", targetProject.projectId)
            if (!viewName.isNullOrBlank() || !viewId.isNullOrBlank()) {
                put("targetViewFilter", viewName ?: viewId ?: "")
            }
            put("totalIssues", filteredErrors.size)
            put("summary", summaryText)
            put("issues", issuesArray)
        }

        onProgress?.invoke(1.0f, "诊断完成")
        return ToolResult.text(resultJson.toString())
    }
}
