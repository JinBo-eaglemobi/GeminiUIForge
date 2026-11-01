package org.gemini.ui.forge.service.tool.ai.exclusive

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 工作区与项目文档只读阅读器
 */
class ReadProjectFileTool(
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "read_project_file"

    override val description: String =
        "安全只读检索当前项目私有存储目录下的文本文件、设计规范 Markdown 或 JSON 配置文件。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("relativePath", buildJsonObject {
                put("type", "string")
                put("description", "相对于应用存储根目录的路径，例如 'templates/Football/spec.md' 或 'config.json'")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("relativePath")) })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val relPath = arguments["relativePath"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'relativePath' 不能为空")

        // 安全防越权检查
        if (relPath.contains("..") || relPath.startsWith("/") || relPath.startsWith("\\")) {
            return ToolResult.error("非法的相对路径，禁止跨目录访问")
        }

        onProgress?.invoke(0.3f, "正在读取: $relPath...")
        val content = fileStorage.readFromFile(relPath)
            ?: return ToolResult.error("未找到文件或文件内容为空: $relPath")

        onProgress?.invoke(1.0f, "读取完成")
        return ToolResult.success(content)
    }
}
