package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.fetchImageBytes
import org.jetbrains.skia.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 纯离线视觉与几何重合度量分析工具
 * 彻底废除错误的 512x512 暴力缩放，以 1:1 原寸对齐基准度量真实视觉与边框重合度
 */
class CompareWithReferenceTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "compare_with_reference"

    override val description: String =
        "【离线视觉与几何对齐分析工具】将实机渲染截图与设计参考原图进行 1:1 原寸绝对对齐比对，度量图元几何重合度与真实位置偏移，并生成带有精准边框标记的叠加热力检查图 (ImageContent)。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val timeoutMs: Long = 60_000L

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("screenshotPath", buildJsonObject {
                put("type", "string")
                put("description", "生成的整页截图绝对物理路径或网络 URL")
            })
            put("referencePath", buildJsonObject {
                put("type", "string")
                put("description", "原始设计参考大图的绝对物理路径或网络 URL")
            })
            put("tolerance", buildJsonObject {
                put("type", "integer")
                put("description", "RGB 容差阈值 (0~255)，默认 24")
            })
        })
        put("required", buildJsonArray {
            add("screenshotPath")
            add("referencePath")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val shotPath = arguments["screenshotPath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'screenshotPath' 不能为空")
        val refPath = arguments["referencePath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'referencePath' 不能为空")
        val tolerance = arguments["tolerance"]?.jsonPrimitive?.intOrNull ?: 24

        onProgress?.invoke(0.1f, "正在读取原寸比对图像源...")
        val shotBytes = fetchImageBytes(shotPath)
            ?: return McpToolResult.error("无法读取截图图片: $shotPath")
        val refBytes = fetchImageBytes(refPath)
            ?: return McpToolResult.error("无法读取参考图片: $refPath")

        val imgShot = Image.makeFromEncoded(shotBytes)
        val imgRef = Image.makeFromEncoded(refBytes)

        // 严格以参考原图的真实物理分辨率为绝对基准 (杜绝变形压扁)
        val baseW = imgRef.width
        val baseH = imgRef.height

        onProgress?.invoke(0.3f, "以参考图原寸 ($baseW×$baseH) 建立高保真物理分析画布...")
        val bmpShot = Bitmap().apply { allocN32Pixels(baseW, baseH) }
        val bmpRef = Bitmap().apply { allocN32Pixels(baseW, baseH) }

        val canvasShot = Canvas(bmpShot)
        val canvasRef = Canvas(bmpRef)
        val paint = Paint().apply { isAntiAlias = true }

        // 保持参考图原生 1:1，截图等比适应映射
        canvasRef.drawImage(imgRef, 0f, 0f)
        canvasShot.drawImageRect(imgShot, Rect.makeWH(baseW.toFloat(), baseH.toFloat()), paint)

        onProgress?.invoke(0.5f, "正在执行多维计算机视觉几何与纹理重叠度量 (差值、SSIM、边缘IoU、NCC)...")
        val evalResult = org.gemini.ui.forge.utils.PatternOverlapEvaluator.evaluate(
            renderBytes = shotBytes,
            referenceBytes = refBytes,
            generateDiffImage = true
        )

        onProgress?.invoke(0.85f, "生成 1:1 几何对齐叠加检查图...")
        val diffBytes = evalResult.diffImageBytes ?: shotBytes
        val diffImage = Image.makeFromEncoded(diffBytes)
        // 统一走公共工具压缩中枢（quality 92 兼顾热力对比度的像素级可读性）
        val compact = compressToCompactImage(diffImage, 92)
        val cachedPath = ImageCacheManager.saveCache("compare", compact)

        @OptIn(ExperimentalEncodingApi::class)
        val diffBase64 = Base64.encode(compact.bytes)

        onProgress?.invoke(1.0f, "1:1 原寸对齐多维度量分析完成")
        val summaryText = buildJsonObject {
            put("compositeAlignmentScore", evalResult.compositeAlignmentScore)
            put("rating", evalResult.rating)
            put("ssim", evalResult.ssim)
            put("edgeIoU", evalResult.edgeIoU)
            put("zeroDiffRate", evalResult.zeroDiffRate)
            put("meanAbsoluteError", evalResult.meanAbsoluteError)
            put("nccScore", evalResult.nccScore)
            putJsonObject("driftVector") {
                put("dx", evalResult.driftVectorX)
                put("dy", evalResult.driftVectorY)
            }
            put("canvasWidth", baseW)
            put("canvasHeight", baseH)
            put("isAligned", evalResult.compositeAlignmentScore >= 0.78f)
        }.toString()

        return McpToolResult.image(
            base64Data = diffBase64,
            mimeType = compact.mimeType,
            message = "📊 1:1 计算机视觉多维对齐报告: $summaryText | 差值热力图 (${compact.extension.uppercase()}) 已缓存: $cachedPath"
        )
    }
}
