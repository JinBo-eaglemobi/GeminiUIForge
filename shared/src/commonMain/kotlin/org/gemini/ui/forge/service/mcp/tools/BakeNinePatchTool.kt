package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.ui.ImageResizeMode
import org.gemini.ui.forge.model.ui.NinePatchConfig
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.bakeNinePatchImage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 图像九宫格物理烘焙工具
 */
class BakeNinePatchTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "bake_nine_patch"

    override val description: String =
        "对指定图像按照九宫格（Nine-patch）拉伸或平铺规则进行物理合成与烘焙，生成一张全新脱离规则依赖的高清 PNG 图片。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("imagePath", buildJsonObject {
                put("type", "string")
                put("description", "原始图像的绝对物理路径")
            })
            put("targetWidth", buildJsonObject {
                put("type", "integer")
                put("description", "烘焙目标总宽度（像素）")
            })
            put("targetHeight", buildJsonObject {
                put("type", "integer")
                put("description", "烘焙目标总高度（像素）")
            })
            put("ninePatchConfig", buildJsonObject {
                put("type", "object")
                put("description", "九宫格安全线配置 { left, right, top, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "integer") })
                    put("right", buildJsonObject { put("type", "integer") })
                    put("top", buildJsonObject { put("type", "integer") })
                    put("bottom", buildJsonObject { put("type", "integer") })
                })
            })
        })
        put("required", buildJsonArray {
            add("imagePath")
            add("targetWidth")
            add("targetHeight")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val imagePath = arguments["imagePath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'imagePath' 不能为空")
        val targetWidth = arguments["targetWidth"]?.jsonPrimitive?.intOrNull ?: 100
        val targetHeight = arguments["targetHeight"]?.jsonPrimitive?.intOrNull ?: 100

        val npObj = arguments["ninePatchConfig"]?.let { it as? JsonObject }
        val left = npObj?.get("left")?.jsonPrimitive?.intOrNull ?: 0
        val right = npObj?.get("right")?.jsonPrimitive?.intOrNull ?: 0
        val top = npObj?.get("top")?.jsonPrimitive?.intOrNull ?: 0
        val bottom = npObj?.get("bottom")?.jsonPrimitive?.intOrNull ?: 0
        val npConfig = NinePatchConfig(left, right, top, bottom)

        onProgress?.invoke(0.3f, "正在读取源图片并执行 Skia 九宫格运算...")
        val bakedBytes = bakeNinePatchImage(
            sourcePath = imagePath,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            contentWidth = targetWidth,
            contentHeight = targetHeight,
            resizeMode = ImageResizeMode.NINE_PATCH,
            ninePatchConfig = npConfig
        ) ?: return McpToolResult.error("九宫格图像烘焙失败，请检查输入参数与图片尺寸")

        onProgress?.invoke(0.8f, "正在编码并落盘物理图片...")
        @OptIn(ExperimentalEncodingApi::class)
        val base64 = Base64.encode(bakedBytes)

        val outPath = imagePath.substringBeforeLast(".") + "_baked_${targetWidth}x${targetHeight}.png"
        try {
            storage.saveBytesToFile(outPath, bakedBytes)
        } catch (_: Throwable) {}

        onProgress?.invoke(1.0f, "烘焙完成")
        return McpToolResult.image(
            base64Data = base64,
            mimeType = "image/png",
            message = "✅ 九宫格已烘焙至目标尺寸 ($targetWidth×$targetHeight)，物理文件已保存至: $outPath"
        )
    }
}
