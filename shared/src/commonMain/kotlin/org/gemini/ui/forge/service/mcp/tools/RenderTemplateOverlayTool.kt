package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.TemplateOverlayRenderer
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.compressToCompactImage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 离屏全景图元标注图渲染与获取工具
 * 纯本地毫秒级 Skia 渲染，零 AI 消耗，支持返回最新/初始标注图
 */
class RenderTemplateOverlayTool(
    private val repository: TemplateRepository = TemplateRepository()
) : McpToolDefinition {

    override val name: String = "render_template_overlay"

    override val description: String =
        "【离屏全景标注图渲染工具】调用纯本地 Skia 离屏渲染引擎（零 AI 消耗），将指定模板工程的所有图元按绝对坐标与层级绘制在原参考底图上（橙色容器外框、青色业务图元外框与尺寸药丸标签），并直接回传全景标注图 ImageContent 与物理落盘路径。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("stage", buildJsonObject {
                put("type", "string")
                put("description", "可选：要获取的标注图版本，'latest' (最新校对版) 或 'initial' (AI初始生成版)，默认 'latest'")
            })
            put("forceRerender", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否强制根据当前 template.json 重新离屏渲染落盘，默认 true")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val stage = arguments["stage"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "latest"
        val forceRerender = arguments["forceRerender"]?.jsonPrimitive?.booleanOrNull ?: true

        onProgress?.invoke(0.1f, "正在读取工程结构...")
        val match = repository.findTemplatePair(projectName)
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val overlayFile = if (stage == "initial") {
            TemplateOverlayRenderer.getInitialOverlayFile(projectName)
        } else {
            TemplateOverlayRenderer.getLatestOverlayFile(projectName)
        }

        if (forceRerender || !overlayFile.exists()) {
            onProgress?.invoke(0.3f, "正在通过本地 Skia 离屏渲染引擎生成全景标注图...")
            if (stage == "initial") {
                TemplateOverlayRenderer.renderAndSaveInitialAndLatest(projectName, state)
            } else {
                TemplateOverlayRenderer.renderAndSaveLatest(projectName, state)
            }
        }

        if (!overlayFile.exists()) {
            return McpToolResult.error("全景标注图生成失败，文件未落盘: ${overlayFile.getAbsolutePath()}")
        }

        onProgress?.invoke(0.8f, "正在打包标注图像数据...")
        val fileBytes = overlayFile.readBytes()
            ?: return McpToolResult.error("无法读取全景标注图文件: ${overlayFile.getAbsolutePath()}")

        val compact = compressToCompactImage(fileBytes, quality = 85)
        val base64Data = Base64.encode(compact.bytes)

        val firstPage = state.pages.firstOrNull()
        val absPath = overlayFile.getAbsolutePath()
        val message = "✅ 全景标注图 [$projectName ($stage)] Skia 离屏渲染完成 (${compact.bytes.size / 1024}KB)，物理落盘路径: $absPath，画布尺寸: ${firstPage?.width ?: 1080}x${firstPage?.height ?: 1920}"

        return McpToolResult.image(
            base64Data = base64Data,
            mimeType = compact.mimeType,
            message = message
        )
    }
}
