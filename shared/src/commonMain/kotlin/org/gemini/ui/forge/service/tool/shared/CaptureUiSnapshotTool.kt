package org.gemini.ui.forge.service.tool.shared

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
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.UiGeometryHelper
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.findBlockById
import kotlin.io.encoding.Base64

/**
 * 应用界面原生快照截图工具（供 AI 调试、视觉审查与状态对账）
 *
 * 截取对象为应用内部 Compose 视图层 (离屏重绘，界面最小化或后台运行同样有效，绝不截取操作系统桌面)，
 * 支持按原生语义节点/图元 (targetNodeId) 或视口像素区域 (region) 精准裁剪特写，亦可缺省截取整窗全貌。
 */
class CaptureUiSnapshotTool : ToolDefinition {

    override val name: String = "capture_ui_snapshot"

    override val description: String =
        "获取应用程序界面/视图的实时画面快照 (ImageContent，离屏渲染，窗口最小化或后台运行同样有效，绝不截取系统桌面)。可选 targetNodeId (如画布图元 ID 或组件标识) 或 region {left,top,right,bottom} (屏幕绝对像素区域) 精准截取某个模块/组件/程序区域 (主窗与弹窗内容均支持)。"

    override val annotations: ToolAnnotations = ToolAnnotations(
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
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number"); put("description", "区域左侧 X 像素坐标") })
                    put("top", buildJsonObject { put("type", "number"); put("description", "区域顶部 Y 像素坐标") })
                    put("right", buildJsonObject { put("type", "number"); put("description", "区域右侧 X 像素坐标") })
                    put("bottom", buildJsonObject { put("type", "number"); put("description", "区域底部 Y 像素坐标") })
                })
            })
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        // 解析裁剪区域：原生语义节点/图元优先，其次显式像素区域，均缺省则整窗
        val nodeId = arguments["targetNodeId"]?.jsonPrimitive?.contentOrNull
        var blockFilePrefix: String? = null
        val nodeRegion: Pair<IntRect, String>? = nodeId?.let { id ->
            val cleanId = id.removePrefix("block_")
            val vm = McpUiActionPipeline.getActiveViewModel()
            val allBlocks = vm?.state?.value?.currentPage?.blocks?.bindParents() ?: emptyList()
            val targetBlock = allBlocks.findBlockById(cleanId) as? UIBlock
            if (targetBlock != null) {
                // 携带模块类型与唯一 ID，例如 block_button_btn_spin 或 block_btn_spin
                blockFilePrefix = "block_${targetBlock.type.name.lowercase()}_$cleanId"
                val blockInWindow = UiGeometryHelper.getBlockBoundsInWindow(targetBlock)
                val r = UiGeometryHelper.getBlockCropRegionInWindow(targetBlock, paddingPx = 32)
                r to "图元 [$cleanId] 视口区域 (${r.width}x${r.height}px)"
            } else {
                blockFilePrefix = "node_$cleanId"
                ComposeAccessibilityGateway.getNodeBounds(id)?.let { bounds ->
                    IntRect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()) to "语义节点 [$id] 区域 (${bounds.width.toInt()}x${bounds.height.toInt()}px)"
                } ?: return ToolResult.error("未找到目标节点或图元: $id")
            }
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
            ?: return ToolResult.error("应用窗口未就绪或区域无效，截图失败")

        onProgress?.invoke(0.7f, "正在压缩编码 (WEBP 优先) 并缓存到磁盘...")
        // 统一走公共工具压缩中枢：携带图元唯一 ID 与语义化名称，彻底消除无名匿名缓存
        val prefix = blockFilePrefix ?: if (region != null) "ui_region" else "ui_snapshot"
        val compact = compressToCompactImage(shotBytes)
        val vm = McpUiActionPipeline.getActiveViewModel()
        val currentProjectName = vm?.state?.value?.projectName?.takeIf { it.isNotBlank() }
        val cachedPath = ImageCacheManager.saveCache(prefix, compact, projectName = currentProjectName)

        val b64 = Base64.encode(compact.bytes)
        onProgress?.invoke(1.0f, "应用界面画面快照捕获就绪")

        return ToolResult.image(
            base64Data = b64,
            mimeType = compact.mimeType,
            message = "📸 应用界面快照完成 [$regionDesc] (${compact.extension.uppercase()} 压缩, ${compact.bytes.size / 1024}KB)，磁盘缓存: $cachedPath"
        )
    }
}
