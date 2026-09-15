package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.looseJson

/**
 * 获取指定模板的完整结构（自动脱敏本地敏感路径）
 */
class GetTemplateTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "get_template"

    override val description: String =
        "按模板名称获取指定 UI 模板的完整页面与图元树结构（图元坐标、提示词、属性配置等）。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("name", buildJsonObject {
                put("type", "string")
                put("description", "模板唯一名称，如通过 list_templates 返回的 name 字段")
            })
        })
        put("required", buildJsonArray { add("name") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'name' 不能为空")

        onProgress?.invoke(0.3f, "正在从本地工程库加载模板 '$name'...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?: return McpToolResult.error("未找到名称为 '$name' 的模板")

        onProgress?.invoke(1.0f, "模板结构读取就绪")
        return McpToolResult.text(looseJson.encodeToString(match.second))
    }
}
