package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiAction
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.PatternOverlapEvaluator
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.extractImageSubset
import org.gemini.ui.forge.utils.fetchImageBytes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MCP 工具：针对指定模块进行单图元隔离比对（隐藏其他图元 -> 居中放大 -> 开启参考图半透明叠加 -> 捕获画面）
 */
class InspectBlockWithReferenceTool : McpToolDefinition {
    override val name: String = "inspect_block_with_reference"
    override val description: String = "一键隔离并详查单个图元与参考图的绝对贴合度：自动隐藏其它所有干扰模块、居中视口、开启参考底图半透明叠加对比（支持指定透明度），并由真实 UI 渲染生成实机截图回传。"


    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "可选：目标模板名称，缺省时作用于当前已打开的活跃工程")
            }
            putJsonObject("blockId") {
                put("type", "string")
                put("description", "要聚焦详查的目标模块 ID")
            }
            putJsonObject("overlayOpacity") {
                put("type", "number")
                put("description", "参考图半透明叠加透明度 (0.1 ~ 1.0)，默认 0.5")
            }
            putJsonObject("calibrate") {
                put("type", "boolean")
                put("description", "是否顺带在真实 UI 上触发一次微观吸附校对，默认 false")
            }
            putJsonObject("cropRegionOnly") {
                put("type", "boolean")
                put("description", "是否仅截取该校准模块及其外扩范围的局部高清图像 (默认 true，避免全屏大图造成细节模糊)")
            }
            putJsonObject("padding") {
                put("type", "integer")
                put("description", "局部截图外扩边距像素 (默认 32)")
            }
            putJsonObject("evaluateOverlap") {
                put("type", "boolean")
                put("description", "是否执行多维计算机视觉图案重叠综合评估 (CAS, SSIM, EdgeIoU, 差值消隐, 自愈位移，默认 true)")
            }
        }
        putJsonArray("required") {
            add("blockId")
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
        val blockId = arguments["blockId"]?.jsonPrimitive?.content ?: return McpToolResult.error("缺少必填参数 'blockId'")
        val opacity = arguments["overlayOpacity"]?.jsonPrimitive?.floatOrNull ?: 0.5f
        val calibrate = arguments["calibrate"]?.jsonPrimitive?.booleanOrNull ?: false
        val cropRegionOnly = arguments["cropRegionOnly"]?.jsonPrimitive?.booleanOrNull ?: true
        val padding = arguments["padding"]?.jsonPrimitive?.intOrNull ?: 32
        val evaluateOverlap = arguments["evaluateOverlap"]?.jsonPrimitive?.booleanOrNull ?: true

        val actions = mutableListOf<McpUiAction>()

        // 1. 选中该模块
        actions.add(McpUiAction(type = "SELECT_BLOCK", blockId = blockId))
        // 2. 隔离该模块（隐藏其他模块）
        actions.add(McpUiAction(type = "ISOLATE_BLOCK", blockId = blockId))
        // 3. 居中视口
        actions.add(McpUiAction(type = "FOCUS_BLOCK_ON_CANVAS", blockId = blockId))
        // 4. 开启参考底图半透明叠加模式
        actions.add(McpUiAction(type = "SET_REFERENCE_MODE", mode = "OVERLAY"))
        // 5. 设置参考底图叠加透明度
        actions.add(McpUiAction(type = "SET_REFERENCE_OPACITY", opacity = opacity))

        // 可选：触发微观吸附校准
        if (calibrate) {
            actions.add(McpUiAction(type = "TRIGGER_CALIBRATE", blockId = blockId, engineMode = "BASELINE_SNAPPER"))
        }

        // 等待 200ms 保证渲染落定
        actions.add(McpUiAction(type = "WAIT_MS", delayMs = 200L))

        onProgress?.invoke(0.2f, "正在执行模块隔离与参考图对比管线: $blockId")
        val result = McpUiActionPipeline.executeSequence(
            projectName = projectName,
            actions = actions,
            captureScreenshot = true,
            screenshotTargetBlockId = if (cropRegionOnly) blockId else null,
            screenshotPadding = padding
        )

        @OptIn(ExperimentalEncodingApi::class)
        var evalJson: JsonObject? = null

        if (evaluateOverlap && result.screenshotBase64 != null) {
            try {
                val vm = McpUiActionPipeline.getActiveViewModel()
                val project = vm?.state?.value?.project
                val page = project?.pages?.firstOrNull()
                val refLargePath = page?.sourceImageUri?.getAbsolutePath() ?: project?.referenceImages?.firstOrNull()?.getAbsolutePath()
                val targetBlock = page?.blocks?.let { blocks ->
                    fun find(list: List<UIBlock>): UIBlock? {
                        for (b in list) {
                            if (b.id == blockId) return b
                            val sub = find(b.children)
                            if (sub != null) return sub
                        }
                        return null
                    }
                    find(blocks)
                }

                if (targetBlock != null && !refLargePath.isNullOrBlank()) {
                    val refLargeBytes = fetchImageBytes(refLargePath)
                    if (refLargeBytes != null) {
                        val cropBounds = targetBlock.cropRect ?: targetBlock.toAbsoluteBounds()
                        val refCropBytes = extractImageSubset(
                            refLargeBytes,
                            cropBounds,
                            page.width,
                            page.height,
                            isPng = true
                        )

                        if (refCropBytes != null) {
                            val shotBytes = Base64.decode(result.screenshotBase64)
                            val evalResult = PatternOverlapEvaluator.evaluate(
                                renderBytes = shotBytes,
                                referenceBytes = refCropBytes,
                                generateDiffImage = true
                            )

                            var diffCachedPath: String? = null
                            if (evalResult.diffImageBytes != null) {
                                val diffImg = Image.makeFromEncoded(evalResult.diffImageBytes)
                                val compact = compressToCompactImage(diffImg, 90)
                                diffCachedPath = ImageCacheManager.saveCache("diff_$blockId", compact)
                            }

                            evalJson = buildJsonObject {
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
                                if (diffCachedPath != null) {
                                    put("cachedDiffHeatmapPath", diffCachedPath)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                // 评估异常不阻塞主体截图
            }
        }

        val responseJson = buildJsonObject {
            put("allSuccess", result.allSuccess)
            put("inspectedBlockId", blockId)
            put("opacity", opacity)
            put("calibrated", calibrate)
            put("cropRegionOnly", cropRegionOnly)
            if (result.errorMessage != null) {
                put("error", result.errorMessage)
            }
            if (result.screenshotCachedPath != null) {
                put("cachedScreenshotPath", result.screenshotCachedPath)
            }
            if (evalJson != null) {
                put("overlapEvaluation", evalJson)
            }
            putJsonArray("reports") {
                for (r in result.reports) {
                    addJsonObject {
                        put("step", r.stepIndex)
                        put("action", r.actionType)
                        put("success", r.isSuccess)
                        put("message", r.message)
                    }
                }
            }
        }

        return if (result.screenshotBase64 != null) {
            McpToolResult.image(
                base64Data = result.screenshotBase64,
                mimeType = "image/png",
                message = responseJson.toString()
            )
        } else {
            McpToolResult.text(responseJson.toString())
        }
    }
}
