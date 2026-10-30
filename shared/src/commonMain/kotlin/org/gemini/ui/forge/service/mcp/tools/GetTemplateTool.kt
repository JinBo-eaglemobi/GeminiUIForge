package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.looseJson

/**
 * 获取指定模板工程的完整结构（页面图元树、提示词与属性配置）
 */
class GetTemplateTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "get_template"

    override val description: String =
        "按工程项目名称获取指定 UI 模板的完整页面与图元树结构（图元坐标、提示词、属性配置等），支持按视图名称 viewName 定向提取。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "目标工程项目唯一名称，如通过 list_templates 返回的 projectName 字段（例如 'New_UI_Project222'）")
            })
            put("viewName", buildJsonObject {
                put("type", "string")
                put("description", "可选：目标视图/页面名称（如 'Main'），若传入则优先定位并重点提炼该视图的详细图元树与分辨率摘要")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("缺少必填参数 'projectName'")

        val viewName = arguments["viewName"]?.jsonPrimitive?.contentOrNull?.trim()

        onProgress?.invoke(0.3f, "正在从本地工程库加载模板工程 '$projectName'...")
        val template = repository.getTemplateByName(projectName)
            ?: return McpToolResult.error("未找到名称为 '$projectName' 的工程模板（已检索原始工程名与下划线目录）")

        onProgress?.invoke(0.8f, "正在解析模板页面与图元树...")

        // 如果调用者指定了具体的 viewName，则定向提炼该视图的图元树
        if (!viewName.isNullOrBlank()) {
            val matchedPage = template.pages.firstOrNull { page ->
                page.nameStr.equals(viewName, ignoreCase = true) ||
                        page.id.equals(viewName, ignoreCase = true)
            }
            if (matchedPage != null) {
                val targetedResult = buildJsonObject {
                    put("projectName", projectName)
                    put("targetView", buildJsonObject {
                        put("id", matchedPage.id)
                        put("name", matchedPage.nameStr)
                        put("width", matchedPage.width)
                        put("height", matchedPage.height)
                        put("blockCount", matchedPage.blocks.size)
                        put("blocks", looseJson.encodeToJsonElement(matchedPage.blocks))
                    })
                    put("allPagesSummary", buildJsonArray {
                        template.pages.forEach { p ->
                            add(buildJsonObject {
                                put("id", p.id)
                                put("name", p.nameStr)
                                put("blockCount", p.blocks.size)
                            })
                        }
                    })
                }
                onProgress?.invoke(1.0f, "视图 '$viewName' 结构提炼就绪")
                return McpToolResult.text(targetedResult.toString())
            }
        }

        onProgress?.invoke(1.0f, "模板工程全量结构读取就绪")
        return McpToolResult.text(looseJson.encodeToString(template))
    }
}
