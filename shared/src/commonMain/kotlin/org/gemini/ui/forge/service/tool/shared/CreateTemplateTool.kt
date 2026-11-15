package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

/**
 * 创建全新空白 UI Studio 项目工程工具（统一复用 TemplateRepository.createStudioProject 公用函数）
 */
class CreateTemplateTool(
    private val repository: TemplateRepository = TemplateRepository()
) : ToolDefinition {

    override val name: String = "create_template"

    override val description: String =
        "创建一个全新的空白游戏 UI Studio 工程。自动生成带有指定画布尺寸的默认主页面 (MainPage) 与工程骨架。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "要创建的项目工程名称（唯一标识符）")
            })
            put("canvasWidth", buildJsonObject {
                put("type", "number")
                put("description", "画布宽度，默认 1280.0")
            })
            put("canvasHeight", buildJsonObject {
                put("type", "number")
                put("description", "画布高度，默认 720.0")
            })
            put("initialViewId", buildJsonObject {
                put("type", "string")
                put("description", "可选：初始主页面 ID，默认 'MainPage'")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.error("参数 'projectName' 不能为空")

        val width = arguments["canvasWidth"]?.jsonPrimitive?.floatOrNull ?: 1280f
        val height = arguments["canvasHeight"]?.jsonPrimitive?.floatOrNull ?: 720f
        val initialViewId = arguments["initialViewId"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { "MainPage" } ?: "MainPage"

        onProgress?.invoke(0.3f, "正在初始化并持久化 UI Studio 工程骨架...")
        val projectState = repository.createStudioProject(
            projectName = projectName,
            canvasWidth = width,
            canvasHeight = height,
            initialViewId = initialViewId,
            persistToDisk = true
        )
        onProgress?.invoke(1.0f, "工程已就绪")

        val resultJson = buildJsonObject {
            put("success", true)
            put("projectName", projectState.projectId)
            put("initialViewId", initialViewId)
            put("canvasWidth", width)
            put("canvasHeight", height)
        }.toString()

        return ToolResult.text(resultJson)
    }
}
