package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.manager.PromptPresetManager
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 查询内置抠图/改图/高频生图场景预设方案列表工具
 */
class ListMattingPresetsTool(
    private val presetManager: PromptPresetManager = PromptPresetManager(LocalFileStorage())
) : McpToolDefinition {

    override val name: String = "list_matting_presets"

    override val description: String =
        "查询 AI 工作室内置的专业抠图、改图、材质转换与高频生图场景预设提示词方案（包含中英文预设名称、场景描述与标准提示词）。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
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
    ): McpToolResult {
        onProgress?.invoke(0.3f, "正在加载抠图与材质微调预设清单...")
        val presets = presetManager.loadPresets()

        val presetsArray = buildJsonArray {
            presets.forEach { preset ->
                add(buildJsonObject {
                    put("id", preset.id)
                    put("nameZh", preset.nameZh)
                    put("descriptionZh", preset.descriptionZh)
                    put("promptZh", preset.promptZh)
                    put("nameEn", preset.nameEn)
                    put("descriptionEn", preset.descriptionEn)
                    put("promptEn", preset.promptEn)
                })
            }
        }

        val resultJson = buildJsonObject {
            put("count", presets.size)
            put("presets", presetsArray)
        }

        return McpToolResult.success(resultJson.toString())
    }
}
