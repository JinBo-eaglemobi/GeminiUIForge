package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.service.canvas.CanvasScreenshotService
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.studio.StudioAssetManagerService
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.LocalFileStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 离屏 Skia 真实图元拼装整页截图渲染工具（100% 复用 CanvasScreenshotService.renderPageToImage 公用函数）
 */
class ComposePageScreenshotTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "compose_page_screenshot"

    override val description: String =
        "通过 Skia 离屏渲染引擎，将指定 UI Studio 工程视图页面的所有图元（含引用的子页面）按绝对坐标与贴图渲染拼装为一张完整的整页截图。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "工程项目名称")
            })
            put("pageId", buildJsonObject {
                put("type", "string")
                put("description", "可选：要渲染的目标视图 ID（如 'MainPage'），缺省时渲染首个视图")
            })
        })
        put("required", buildJsonArray { add("projectName") })
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val targetPageId = arguments["pageId"]?.jsonPrimitive?.contentOrNull?.trim()

        onProgress?.invoke(0.2f, "正在加载工程视图与素材库...")
        val activeProj = McpUiActionPipeline.getActiveUiStudioViewModel()?.state?.project
        val sanitized = repository.sanitizeName(projectName)
        val state = if (activeProj != null && repository.sanitizeName(activeProj.projectId).equals(sanitized, ignoreCase = true)) {
            activeProj
        } else {
            repository.getTemplateByName(projectName)
                ?: return ToolResult.error("未找到工程 '$projectName'")
        }

        val page = if (!targetPageId.isNullOrBlank()) {
            state.studioPages.find { it.id.equals(targetPageId, ignoreCase = true) }
        } else {
            state.studioPages.firstOrNull()
        } ?: return ToolResult.error("在工程 '$projectName' 中未找到目标视图 '${targetPageId ?: "MainPage"}'")

        val assetLib = StudioAssetManagerService.loadLibrary(sanitized, storage, preferActiveMemory = true)
        val assetMap = assetLib.items.associateBy { it.hash }

        onProgress?.invoke(0.6f, "正在调用公用 Skia 离屏渲染引擎绘制视图 [${page.id}]...")
        val compactImage = CanvasScreenshotService().renderPageToImage(
            page = page,
            availablePages = state.studioPages,
            assetPathResolver = { hash -> if (hash != null) assetMap[hash]?.relativePath else null }
        ) ?: return ToolResult.error("离屏渲染视图 [${page.id}] 失败")

        val cachedPath = ImageCacheManager.saveCache(
            prefix = "page_render_${page.id}",
            image = compactImage,
            projectName = sanitized
        )

        val base64 = Base64.encode(compactImage.bytes)
        onProgress?.invoke(1.0f, "整页离屏渲染完成")
        return ToolResult.image(
            base64Data = base64,
            mimeType = compactImage.mimeType,
            message = "🎨 视图 [${page.id}] (${page.width.toInt()}×${page.height.toInt()}) 离屏拼装渲染完成 (${compactImage.bytes.size / 1024}KB)，缓存路径: $cachedPath"
        )
    }
}
