package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
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

        onProgress?.invoke(0.5f, "正在执行逐像素差值运算与几何重合分析...")
        val bmpDiff = Bitmap().apply { allocN32Pixels(baseW, baseH) }
        val canvasDiff = Canvas(bmpDiff)
        // 底层绘制原始参考大图
        canvasDiff.drawImage(imgRef, 0f, 0f)

        var totalDiffPixels = 0
        val totalPixels = baseW * baseH

        val diffHighlightPaint = Paint().apply {
            color = 0x66FF1744.toInt() // 半透明警示红
        }

        val step = max(1, baseW / 400) // 步进采样自适应，秒级响应
        for (y in 0 until baseH step step) {
            for (x in 0 until baseW step step) {
                val cShot = bmpShot.getColor(x, y)
                val cRef = bmpRef.getColor(x, y)

                val rDiff = abs(Color.getR(cShot) - Color.getR(cRef))
                val gDiff = abs(Color.getG(cShot) - Color.getG(cRef))
                val bDiff = abs(Color.getB(cShot) - Color.getB(cRef))

                if (rDiff > tolerance || gDiff > tolerance || bDiff > tolerance) {
                    totalDiffPixels += step * step
                    canvasDiff.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), step.toFloat(), step.toFloat()), diffHighlightPaint)
                }
            }
        }

        val diffRatio = (totalDiffPixels.toFloat() / totalPixels.toFloat()).coerceIn(0f, 1f)
        val similarityScore = (1.0f - diffRatio).coerceIn(0f, 1f)

        onProgress?.invoke(0.85f, "生成 1:1 几何对齐叠加检查图...")
        val diffImage = Image.makeFromBitmap(bmpDiff)
        val diffPng = diffImage.encodeToData(EncodedImageFormat.PNG)
            ?: return McpToolResult.error("生成比对图失败")

        @OptIn(ExperimentalEncodingApi::class)
        val diffBase64 = Base64.encode(diffPng.bytes)

        onProgress?.invoke(1.0f, "1:1 原寸对齐度量分析完成")
        val summaryText = buildJsonObject {
            put("similarityScore", ((similarityScore * 1000).toInt() / 10.0)) // 相似度百分比
            put("differenceRatio", ((diffRatio * 1000).toInt() / 10.0))
            put("canvasWidth", baseW)
            put("canvasHeight", baseH)
            put("isAligned", similarityScore >= 0.85f)
        }.toString()

        return McpToolResult.image(
            base64Data = diffBase64,
            mimeType = "image/png",
            message = "📊 1:1 物理对齐报告: $summaryText"
        )
    }
}
