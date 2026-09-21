package org.gemini.ui.forge.service.mcp.tools

import androidx.compose.ui.unit.IntRect
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.gemini.ui.forge.AppWindowHolder
import org.gemini.ui.forge.accessibility.ComposeAccessibilityGateway
import org.gemini.ui.forge.captureActiveScreenShot
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.compressToCompactImage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 应用主窗口截图工具（供 AI 调试与视觉状态确认）
 *
 * 截取对象恒为应用自身窗口 (离屏重绘，界面最小化/后台运行同样有效)，
 * 支持按原生语义节点 (targetNodeId) 或窗口像素区域 (region) 精准裁剪。
 */
class ScreenshotWindowTool : McpToolDefinition {

    override val name: String = "screenshot_window"

    override val description: String =
        "获取应用程序自身窗口的实时画面截图 (ImageContent，离屏渲染，窗口最小化或后台运行同样有效，绝不截取系统桌面)。可选 targetNodeId (原生语义选择器，如画布模块 block_xxx / 按钮文本 / testTag / 数字ID，先经 get_semantic_tree 查询) 或 region {left,top,right,bottom} (屏幕绝对像素区域) 精准截取某个模块/组件/程序区域 (主窗与弹窗内容均支持)。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("targetNodeId", buildJsonObject {
                put("type", "string")
                put("description", "语义节点选择器 (可选，优先级最高)：支持数字ID、testTag(如 block_xxx)、可见文本或描述，按真实屏幕 bounds 裁剪")
            })
            put("region", buildJsonObject {
                put("type", "object")
                put("description", "屏幕绝对像素裁剪区域 (可选)：{ left, top, right, bottom }")
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        // 解析裁剪区域：原生语义节点优先，其次显式像素区域，均缺省则整窗
        val nodeId = arguments["targetNodeId"]?.jsonPrimitive?.contentOrNull
        val nodeRegion: Pair<IntRect, String>? = nodeId?.let { id ->
            ComposeAccessibilityGateway.getNodeBounds(id)?.let { bounds ->
                IntRect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()) to "语义节点 [$id] 区域 (${bounds.width.toInt()}x${bounds.height.toInt()}px)"
            } ?: return McpToolResult.error("未找到语义节点: $id (可先调用 get_semantic_tree 查询有效节点清单)")
        }
        val pixelRegion = (arguments["region"] as? JsonObject)?.let { jo ->
            fun field(fieldName: String) = jo[fieldName]?.jsonPrimitive?.doubleOrNull
            val l = field("left"); val t = field("top"); val r = field("right"); val b = field("bottom")
            if (l == null || t == null || r == null || b == null) null
            else IntRect(l.toInt(), t.toInt(), r.toInt(), b.toInt()) to "像素区域 [${l.toInt()},${t.toInt()} → ${r.toInt()},${b.toInt()}]"
        }
        val (region, regionDesc) = nodeRegion ?: pixelRegion ?: (null to "整窗画面")

        onProgress?.invoke(0.3f, "正在离屏渲染应用窗口 ($regionDesc)...")
        val shotBytes = AppWindowHolder.captureWindowBytes(region)
            // 兜底：窗口未就绪时降级为物理屏幕抓取 (仅整窗模式)
            ?: (if (region == null) captureActiveScreenShot() else null)
            ?: return McpToolResult.error("应用窗口未就绪或区域无效，截图失败")

        onProgress?.invoke(0.7f, "正在压缩编码 (WEBP 优先) 并缓存到磁盘...")
        // 统一走公共工具压缩中枢：WEBP 优先 → JPEG 降级 → PNG 兜底，并异步落盘缓存
        val prefix = if (nodeId != null) "window_node" else if (region != null) "window_region" else "window"
        val compact = compressToCompactImage(shotBytes)
        val cachedPath = ImageCacheManager.saveCache(prefix, compact)

        val b64 = Base64.encode(compact.bytes)
        onProgress?.invoke(1.0f, "应用窗口画面捕获就绪")

        return McpToolResult.image(
            base64Data = b64,
            mimeType = compact.mimeType,
            message = "📸 应用窗口截图完成 [$regionDesc] (${compact.extension.uppercase()} 压缩, ${compact.bytes.size / 1024}KB)，磁盘缓存: $cachedPath"
        )
    }
}
