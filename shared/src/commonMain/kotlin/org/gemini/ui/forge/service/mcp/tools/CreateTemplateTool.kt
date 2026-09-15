package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 创建全新空白 UI 项目模板工具
 */
class CreateTemplateTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "create_template"

    override val description: String =
        "创建一个全新的空白游戏 UI 模板工程。自动生成带有指定画布尺寸的默认页面与工程骨架。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "要创建的项目模板名称（唯一标识符，英文或中文）")
            })
            put("canvasWidth", buildJsonObject {
                put("type", "number")
                put("description", "画布宽度，默认 1080.0")
            })
            put("canvasHeight", buildJsonObject {
                put("type", "number")
                put("description", "画布高度，默认 1920.0")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")

        val width = arguments["canvasWidth"]?.jsonPrimitive?.floatOrNull ?: 1080f
        val height = arguments["canvasHeight"]?.jsonPrimitive?.floatOrNull ?: 1920f

        onProgress?.invoke(0.3f, "正在初始化工程骨架...")
        val defaultPage = UIPage(
            id = "page_main",
            nameStr = "Main Page",
            width = width,
            height = height,
            blocks = emptyList()
        )
        val projectState = ProjectState(
            pages = listOf(defaultPage),
            globalStyle = "Modern Game UI, crisp edges, high dynamic range, clean layout"
        )

        onProgress?.invoke(0.7f, "正在保存模板至本地磁盘...")
        repository.saveTemplate(projectName, projectState)
        onProgress?.invoke(1.0f, "模板工程已就绪")

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectName)
            put("canvasWidth", width)
            put("canvasHeight", height)
            put("pageCount", 1)
        }.toString()

        return McpToolResult.text(resultJson)
    }
}
