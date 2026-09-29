package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 离屏 Skia 真实图元拼装整页截图渲染工具
 */
class ComposePageScreenshotTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "compose_page_screenshot"

    override val description: String =
        "通过 Skia 离屏渲染引擎，将指定模板页面的所有图元按绝对坐标（absoluteBounds）与贴图渲染拼装为一张完整的整页 PNG 截图。AI 可直接通过返回的 ImageContent 多模态查看拼装完成的整页视觉效果。"

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
            put("pageId", buildJsonObject {
                put("type", "string")
                put("description", "可选：要渲染的目标页面 ID，缺省时渲染第一页")
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
        val targetPageId = arguments["pageId"]?.jsonPrimitive?.contentOrNull

        onProgress?.invoke(0.1f, "正在读取工程页面...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(projectName, ignoreCase = true) }
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val page = (if (!targetPageId.isNullOrBlank()) state.pages.firstOrNull { it.id == targetPageId } else state.pages.firstOrNull())
            ?: return McpToolResult.error("工程未包含有效页面")

        val canvasW = page.width.toInt().coerceAtLeast(100)
        val canvasH = page.height.toInt().coerceAtLeast(100)

        onProgress?.invoke(0.2f, "正在初始化 Skia 离屏画布 ($canvasW×$canvasH)...")
        val surface = Surface.makeRasterN32Premul(canvasW, canvasH)
        val canvas = surface.canvas

        // 1. 绘制背景底色（沉稳深灰网格）
        val bgPaint = Paint().apply { color = 0xFF1E1E24.toInt() }
        canvas.drawRect(Rect.makeWH(canvasW.toFloat(), canvasH.toFloat()), bgPaint)

        // 2. 平铺绘制各个图元
        val allBlocks = mutableListOf<UIBlock>()
        fun collect(blocks: List<UIBlock>) {
            for (b in blocks) {
                allBlocks.add(b)
                collect(b.children)
            }
        }
        collect(page.blocks)

        onProgress?.invoke(0.4f, "正在按绝对坐标拼装渲染图元 (${allBlocks.size} 个)...")

        val imageCache = mutableMapOf<String, Image>()
        val placeholderPaint = Paint().apply {
            color = 0x33FFFFFF // 占位半透明
            mode = PaintMode.STROKE
            strokeWidth = 2f
        }

        allBlocks.forEachIndexed { idx, block ->
            val absBounds = block.absoluteBounds
            val left = absBounds.left
            val top = absBounds.top
            val w = absBounds.width
            val h = absBounds.height

            val imgPath = block.currentImageUri?.getAbsolutePath()
            if (imgPath != null) {
                val skiaImg = imageCache.getOrPut(imgPath) {
                    try {
                        val bytes = readLocalFileBytes(imgPath)
                        if (bytes != null) Image.makeFromEncoded(bytes) else null
                    } catch (_: Throwable) { null } ?: return@forEachIndexed
                }

                // 绘制位图贴图
                val dstRect = Rect.makeXYWH(left, top, w, h)
                val paint = Paint().apply { isAntiAlias = true }
                canvas.drawImageRect(skiaImg, dstRect, paint)
            } else {
                // 占位线框
                val placeholderRect = Rect.makeXYWH(left, top, w, h)
                canvas.drawRect(placeholderRect, placeholderPaint)
            }
        }

        onProgress?.invoke(0.8f, "正在压缩编码整页图像 (WEBP 优先)...")
        val imageSnapshot = surface.makeImageSnapshot()
        // 统一走公共工具压缩中枢：已持有 Skia Image 直接走重载，避免 PNG 中转编码
        val compact = compressToCompactImage(imageSnapshot, 90)
        val cachedPath = ImageCacheManager.saveCache("compose_page", compact, projectName = projectName)

        @OptIn(ExperimentalEncodingApi::class)
        val base64 = Base64.encode(compact.bytes)

        onProgress?.invoke(1.0f, "整页拼装截图完成")
        return McpToolResult.image(
            base64Data = base64,
            mimeType = compact.mimeType,
            message = "✅ 页面 [${page.id}] Skia 离屏拼装完成 (${compact.extension.uppercase()} 压缩, ${compact.bytes.size / 1024}KB)，已落盘物理缓存: $cachedPath"
        )
    }
}
