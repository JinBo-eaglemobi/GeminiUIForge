package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.looseJson

/**
 * 获取指定 UI Studio 工程的完整结构（视图页面与图元树配置）
 */
class GetTemplateTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "get_template"

    override val description: String =
        "按工程项目名称获取指定 UI Studio 工程的完整视图与图元树结构（图元坐标、素材绑定、引用拓扑等），支持按视图 ID (viewName) 定向提取。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程项目唯一名称（例如 'New_UI_Project'）")
            })
            put("viewName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标视图页面唯一 ID（如 'MainPage'、'slots_window'），若传入则定向提取该视图的详细图元树与分辨率摘要")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val rawProjectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
        val activeStudioProj = McpUiActionPipeline.getActiveUiStudioViewModel()?.state?.project
        val projectName = if (!rawProjectName.isNullOrBlank()) {
            rawProjectName
        } else {
            activeStudioProj?.projectId ?: return ToolResult.error("缺少必填参数 'projectName'")
        }

        val viewName = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()

        onProgress?.invoke(0.3f, "正在加载工程 '$projectName'...")
        val template = if (activeStudioProj != null && repository.sanitizeName(activeStudioProj.projectId).equals(repository.sanitizeName(projectName), ignoreCase = true)) {
            activeStudioProj
        } else {
            repository.getTemplateByName(projectName)
                ?: return ToolResult.error("未找到名称为 '$projectName' 的工程（已检索原始工程名与下划线目录）")
        }

        onProgress?.invoke(0.8f, "正在解析 UI Studio 视图与图元树...")
        val studioPages = template.studioPages

        if (!viewName.isNullOrBlank()) {
            val matchedPage = studioPages.firstOrNull { page ->
                page.id.equals(viewName, ignoreCase = true)
            } ?: return ToolResult.error(
                "在工程 '${template.projectId}' 中未找到 ID 为 '$viewName' 的视图页面。当前工程可用视图列表: ${studioPages.map { it.id }}"
            )

            val targetedResult = buildJsonObject {
                put("projectId", template.projectId)
                put("targetView", buildJsonObject {
                    put("id", matchedPage.id)
                    put("width", matchedPage.width)
                    put("height", matchedPage.height)
                    put("blockCount", matchedPage.blocks.size)
                    put("blocks", looseJson.encodeToJsonElement(matchedPage.blocks))
                })
                put("allViews", buildJsonArray {
                    studioPages.forEach { p ->
                        add(buildJsonObject {
                            put("id", p.id)
                            put("width", p.width)
                            put("height", p.height)
                            put("blockCount", p.blocks.size)
                        })
                    }
                })
            }
            onProgress?.invoke(1.0f, "视图 '${matchedPage.id}' 结构提炼就绪")
            return ToolResult.text(targetedResult.toString())
        }

        onProgress?.invoke(1.0f, "工程全量结构读取就绪")
        return ToolResult.text(looseJson.encodeToString(template))
    }
}
