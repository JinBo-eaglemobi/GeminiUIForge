package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

/**
 * 视觉一致性比对与热力差异分析工具（驱动 AI 自我纠偏迭代的核心工具）
 */
class CompareWithReferenceTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "compare_with_reference"

    override val description: String =
        "将 AI 拼装渲染的页面截图与设计参考原图进行像素级一致性比对。输出全局差异分数（0.0 代表完全一致）、Top-N 差异严重区域坐标，并生成带有红色标记的热力差异图 (ImageContent)。"

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
                put("description", "生成的整页截图绝对物理路径")
            })
            put("referencePath", buildJsonObject {
                put("type", "string")
                put("description", "原始设计参考大图的绝对物理路径")
            })
            put("tolerance", buildJsonObject {
                put("type", "integer")
                put("description", "RGB 容差阈值 (0~255)，默认 16")
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
        val tolerance = arguments["tolerance"]?.jsonPrimitive?.intOrNull ?: 16

        onProgress?.invoke(0.1f, "正在读取比对源图片...")
        val shotBytes = org.gemini.ui.forge.utils.fetchImageBytes(shotPath)
            ?: return McpToolResult.error("无法读取截图图片 (支持本地文件与 http/https 链接): $shotPath")
        val refBytes = org.gemini.ui.forge.utils.fetchImageBytes(refPath)
            ?: return McpToolResult.error("无法读取参考图片 (支持本地文件与 http/https 链接): $refPath")

        val imgShot = Image.makeFromEncoded(shotBytes)
        val imgRef = Image.makeFromEncoded(refBytes)

        // 缩放到统一分析基准尺寸（如 512x512 以保证瞬时高速分析与内存友好）
        val baseW = 512
        val baseH = 512

        onProgress?.invoke(0.3f, "正在执行统一像素基准缩放 ($baseW×$baseH)...")
        val bmpShot = Bitmap().apply { allocN32Pixels(baseW, baseH) }
        val bmpRef = Bitmap().apply { allocN32Pixels(baseW, baseH) }

        val canvasShot = Canvas(bmpShot)
        val canvasRef = Canvas(bmpRef)
        val paint = Paint()

        canvasShot.drawImageRect(imgShot, Rect.makeWH(baseW.toFloat(), baseH.toFloat()), paint)
        canvasRef.drawImageRect(imgRef, Rect.makeWH(baseW.toFloat(), baseH.toFloat()), paint)

        onProgress?.invoke(0.5f, "正在执行逐像素 RGB 差值运算与 16x16 网格分析...")
        val bmpDiff = Bitmap().apply { allocN32Pixels(baseW, baseH) }
        val canvasDiff = Canvas(bmpDiff)
        // 底层先画原参考图
        canvasDiff.drawImageRect(imgRef, Rect.makeWH(baseW.toFloat(), baseH.toFloat()), paint)

        var totalDiffPixels = 0
        val totalPixels = baseW * baseH

        // 16x16 网格统计
        val gridCount = 16
        val cellW = baseW / gridCount
        val cellH = baseH / gridCount
        val gridDiffs = Array(gridCount) { IntArray(gridCount) }

        val diffHighlightPaint = Paint().apply {
            color = 0x88FF0000.toInt() // 差异区域半透明红色高亮
        }

        for (y in 0 until baseH) {
            val gy = (y / cellH).coerceAtMost(gridCount - 1)
            for (x in 0 until baseW) {
                val gx = (x / cellW).coerceAtMost(gridCount - 1)

                val cShot = bmpShot.getColor(x, y)
                val cRef = bmpRef.getColor(x, y)

                val rDiff = abs(Color.getR(cShot) - Color.getR(cRef))
                val gDiff = abs(Color.getG(cShot) - Color.getG(cRef))
                val bDiff = abs(Color.getB(cShot) - Color.getB(cRef))

                if (rDiff > tolerance || gDiff > tolerance || bDiff > tolerance) {
                    totalDiffPixels++
                    gridDiffs[gy][gx]++
                }
            }
        }

        // 绘制前 Top 差异网格红框
        val diffBoxPaint = Paint().apply {
            color = 0xFFFF1744.toInt()
            mode = PaintMode.STROKE
            strokeWidth = 2f
        }

        val topDiffRegions = mutableListOf<JsonObject>()
        for (gy in 0 until gridCount) {
            for (gx in 0 until gridCount) {
                val diffCount = gridDiffs[gy][gx]
                if (diffCount > (cellW * cellH * 0.15)) { // 超过 15% 像素不符视为明显差异区块
                    val rx = gx * cellW.toFloat()
                    val ry = gy * cellH.toFloat()
                    val rw = cellW.toFloat()
                    val rh = cellH.toFloat()
                    canvasDiff.drawRect(Rect.makeXYWH(rx, ry, rw, rh), diffHighlightPaint)
                    canvasDiff.drawRect(Rect.makeXYWH(rx, ry, rw, rh), diffBoxPaint)

                    topDiffRegions.add(buildJsonObject {
                        put("gridX", gx)
                        put("gridY", gy)
                        put("diffPixelCount", diffCount)
                    })
                }
            }
        }

        val diffRatio = totalDiffPixels.toFloat() / totalPixels.toFloat()
        onProgress?.invoke(0.8f, "差异比率: ${(diffRatio * 100).toInt()}%，正在生成热力差异图...")

        val diffImage = Image.makeFromBitmap(bmpDiff)
        val diffPng = diffImage.encodeToData(EncodedImageFormat.PNG)
            ?: return McpToolResult.error("生成差异图失败")

        @OptIn(ExperimentalEncodingApi::class)
        val diffBase64 = Base64.encode(diffPng.bytes)

        onProgress?.invoke(1.0f, "比对分析就绪")
        val summaryText = buildJsonObject {
            put("differenceScore", diffRatio)
            put("isVisuallyConsistent", diffRatio < 0.08f) // 差异小于 8% 视为高保真一致
            put("diffPixelCount", totalDiffPixels)
            put("totalPixels", totalPixels)
            put("flaggedGridCount", topDiffRegions.size)
        }.toString()

        return McpToolResult.image(
            base64Data = diffBase64,
            mimeType = "image/png",
            message = "📊 视觉比对报告: $summaryText"
        )
    }
}
