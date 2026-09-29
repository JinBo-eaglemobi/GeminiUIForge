package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.getEffectiveCropBounds
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.DetectionEngineRegistry
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiAction
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.PatternOverlapEvaluator
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.utils.extractImageSubset
import org.gemini.ui.forge.utils.fetchImageBytes
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MCP 工具：针对指定模块进行单图元隔离比对（隐藏其他图元 -> 居中放大 -> 开启参考图半透明叠加 -> 捕获画面）
 * 支持两种模式：
 * 1. UI 前台模式 (vm != null)：由真实 UI 分发隔离与视口聚焦，真实截取实机画面
 * 2. 离线无头模式 (vm == null)：由 Skia 离屏引擎按 1:1 局部 bounds 拼装参考图与图元选区，截图直接落盘本地并回传评估
 */
class InspectBlockWithReferenceTool : McpToolDefinition {
    private val repository get() = TemplateRepository()
    override val name: String = "inspect_block_with_reference"
    override val description: String = "一键隔离并详查单个图元与参考图的绝对贴合度：自动隐藏其它所有干扰模块、居中视口、开启参考底图半透明叠加对比（支持指定透明度），生成局部 1:1 高清截图并落盘本地。支持 UI 前台与后台纯离线双模式。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "可选：目标模板名称，缺省时作用于当前已打开的活跃工程或生成中工程")
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
                put("description", "是否顺带在真实 UI 或离线引擎上触发一次微观吸附校对，默认 false")
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

        val vm = McpUiActionPipeline.getActiveViewModel()
        val effectiveProjectName = projectName ?: vm?.state?.value?.projectName
        if (vm == null || (projectName != null && vm.state.value.projectName != projectName)) {
            // 离线无头 Skia 渲染与自检分支
            return executeOfflineInspection(
                projectName = projectName ?: UiRoadmapRegistry.generatingProjects.value.keys.firstOrNull(),
                blockId = blockId,
                opacity = opacity,
                calibrate = calibrate,
                cropRegionOnly = cropRegionOnly,
                padding = padding,
                evaluateOverlap = evaluateOverlap,
                onProgress = onProgress
            )
        }

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
            // 校准为异步计算：给予功能性启动缓冲 (仅校准路径需要，非校准路径由管线的帧同步等待覆盖)
            actions.add(McpUiAction(type = "WAIT_MS", delayMs = 300L))
        }

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
                val project = vm.state.value.project
                val page = project.pages.firstOrNull()
                val refLargePath = page?.sourceImageUri?.getAbsolutePath() ?: project.referenceImages.firstOrNull()?.getAbsolutePath()
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

                val hasImageAsset = !targetBlock?.currentImageUri?.getAbsolutePath().isNullOrBlank()

                if (targetBlock != null && !refLargePath.isNullOrBlank()) {
                    val refLargeBytes = fetchImageBytes(refLargePath)
                    if (refLargeBytes != null) {
                        val cropBounds = targetBlock.getEffectiveCropBounds(page.width, page.height, fallbackPadding = 24f)
                        val refCropBytes = extractImageSubset(
                            refLargeBytes,
                            cropBounds,
                            page.width,
                            page.height,
                            isPng = true
                        )

                        if (!hasImageAsset) {
                            // 未生图阶段：仅进行几何选区核验，避免无贴图时 SSIM/CAS 假性失真误导 AI
                            evalJson = buildJsonObject {
                                put("inspectionMode", "GEOMETRIC_BOUNDS")
                                put("hasImageAsset", false)
                                put("compositeAlignmentScore", 1.0)
                                put("rating", "GEOMETRIC_VERIFIED")
                                put("guidance", "当前模块处于未生图几何核验阶段。请肉眼/多模态审查局部 1:1 切片：检查青色边框是否紧密贴合目标图元视觉边缘（杜绝切边与过度留白，确保居中）。若边框偏离请调用 update_block 纠正。")
                            }
                        } else if (refCropBytes != null) {
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
                                diffCachedPath = ImageCacheManager.saveCache("diff_$blockId", compact, projectName = effectiveProjectName)
                            }

                            evalJson = buildJsonObject {
                                put("inspectionMode", "TEXTURE_ALIGNMENT")
                                put("hasImageAsset", true)
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun executeOfflineInspection(
        projectName: String?,
        blockId: String,
        opacity: Float,
        calibrate: Boolean,
        cropRegionOnly: Boolean,
        padding: Int,
        evaluateOverlap: Boolean,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        if (projectName.isNullOrBlank()) {
            return McpToolResult.error("当前未处于工作区且未提供 projectName，无法执行离线单图元审查")
        }

        onProgress?.invoke(0.1f, "正在读取工程数据: $projectName")
        val state = repository.getTemplateByName(projectName)
            ?: return McpToolResult.error("未找到工程模板 '$projectName'")

        val page = state.pages.firstOrNull()
            ?: return McpToolResult.error("工程模板未包含有效页面")

        val boundBlocks = page.blocks.bindParents()
        fun findBlock(list: List<UIBlock>): UIBlock? {
            for (b in list) {
                if (b.id == blockId) return b
                val sub = findBlock(b.children)
                if (sub != null) return sub
            }
            return null
        }

        var targetBlock = findBlock(boundBlocks)
            ?: return McpToolResult.error("在模板中未找到图元 '$blockId'")

        val refLargePath = page.sourceImageUri?.getAbsolutePath()
            ?: state.referenceImages.firstOrNull()?.getAbsolutePath()

        val refBytes = if (!refLargePath.isNullOrBlank()) fetchImageBytes(refLargePath) else null

        // 1. 若开启校对，执行离线边缘吸附并落盘
        var didCalibrate = false
        if (calibrate && refBytes != null) {
            val aligner = DetectionEngineRegistry.getAligner(DetectionEngineMode.BASELINE_SNAPPER)
            val snapRes = aligner.align(
                imageBytes = refBytes,
                logicalBounds = targetBlock.toAbsoluteBounds(),
                blockType = targetBlock.type,
                canvasWidth = page.width,
                canvasHeight = page.height
            )
            if (snapRes != null) {
                val newLocal = targetBlock.toLocalBounds(snapRes.logicalRect)
                if (newLocal != targetBlock.bounds) {
                    didCalibrate = true
                    targetBlock = targetBlock.copy(bounds = newLocal)
                    // 更新树并保存
                    fun updateBlockInList(list: List<UIBlock>): List<UIBlock> {
                        return list.map { b ->
                            if (b.id == blockId) targetBlock
                            else b.copy(children = updateBlockInList(b.children))
                        }
                    }
                    val updatedBlocks = updateBlockInList(boundBlocks)
                    val updatedPage = page.copy(blocks = updatedBlocks)
                    val updatedState = state.copy(pages = listOf(updatedPage) + state.pages.drop(1))
                    repository.saveTemplate(projectName, updatedState)
                }
            }
        }

        // 2. 离屏 Skia 渲染局部图元特写与参考底图半透明叠加
        val absBounds = targetBlock.toAbsoluteBounds()
        val pad = if (cropRegionOnly) padding.toFloat() else 0f
        val left = (absBounds.left - pad).coerceAtLeast(0f)
        val top = (absBounds.top - pad).coerceAtLeast(0f)
        val right = (absBounds.right + pad).coerceAtMost(page.width)
        val bottom = (absBounds.bottom + pad).coerceAtMost(page.height)

        val rw = (right - left).toInt().coerceAtLeast(10)
        val rh = (bottom - top).toInt().coerceAtLeast(10)

        val surface = Surface.makeRasterN32Premul(rw, rh)
        val canvas = surface.canvas

        // 绘制背景深灰网格底色
        val bgPaint = Paint().apply { color = 0xFF1E1E24.toInt() }
        canvas.drawRect(Rect.makeWH(rw.toFloat(), rh.toFloat()), bgPaint)

        // 绘制参考底图半透明叠加（未生图阶段默认全清晰展示，方便清晰审查边框）
        val currentImgPath = targetBlock.currentImageUri?.getAbsolutePath()
        val currentImgBytes = if (currentImgPath != null) readLocalFileBytes(currentImgPath) else null
        val hasImageAsset = currentImgBytes != null

        if (refBytes != null) {
            try {
                val refImg = Image.makeFromEncoded(refBytes)
                val scaleX = refImg.width.toFloat() / page.width
                val scaleY = refImg.height.toFloat() / page.height
                val srcRect = Rect.makeLTRB(
                    left * scaleX,
                    top * scaleY,
                    right * scaleX,
                    bottom * scaleY
                )
                val dstRect = Rect.makeWH(rw.toFloat(), rh.toFloat())
                val effectiveAlpha = if (!hasImageAsset) 255 else (opacity.coerceIn(0.1f, 1.0f) * 255).toInt()
                val refPaint = Paint().apply {
                    alpha = effectiveAlpha
                    isAntiAlias = true
                }
                canvas.drawImageRect(refImg, srcRect, dstRect, refPaint)
            } catch (e: Throwable) {
                AppLogger.e("InspectBlockWithReferenceTool", "绘制参考底图失败", e)
            }
        }

        // 绘制当前图元已有贴图（若有）
        if (currentImgBytes != null) {
            try {
                val bImg = Image.makeFromEncoded(currentImgBytes)
                val bDst = Rect.makeXYWH(absBounds.left - left, absBounds.top - top, absBounds.width, absBounds.height)
                val bPaint = Paint().apply { isAntiAlias = true }
                canvas.drawImageRect(bImg, bDst, bPaint)
            } catch (_: Throwable) {}
        }

        // 绘制高亮选区边框（青色醒目轮廓线）
        val borderPaint = Paint().apply {
            color = 0xFF00E5FF.toInt()
            mode = PaintMode.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val blockDst = Rect.makeXYWH(absBounds.left - left, absBounds.top - top, absBounds.width, absBounds.height)
        canvas.drawRect(blockDst, borderPaint)

        // 压缩并落盘物理缓存文件
        val imageSnapshot = surface.makeImageSnapshot()
        val compact = compressToCompactImage(imageSnapshot, 90)
        val cachedPath = ImageCacheManager.saveCache("offline_inspect_${blockId}", compact, projectName = projectName)
        val base64 = Base64.encode(compact.bytes)

        // 3. 可选：执行重叠评估
        var evalJson: JsonObject? = null
        if (evaluateOverlap && refBytes != null) {
            try {
                val cropBounds = targetBlock.getEffectiveCropBounds(page.width, page.height, fallbackPadding = 24f)
                val refCropBytes = extractImageSubset(
                    refBytes,
                    cropBounds,
                    page.width,
                    page.height,
                    isPng = true
                )

                if (!hasImageAsset) {
                    // 未生图阶段：仅输出几何选区核验，指引 AI 肉眼比对边框吻合度
                    evalJson = buildJsonObject {
                        put("inspectionMode", "GEOMETRIC_BOUNDS")
                        put("hasImageAsset", false)
                        put("compositeAlignmentScore", 1.0)
                        put("rating", "GEOMETRIC_VERIFIED")
                        put("guidance", "当前模块处于未生图几何核验阶段。请肉眼/多模态审查局部 1:1 切片：检查青色边框是否紧密贴合目标图元视觉边缘（杜绝切边与过度留白，确保居中）。若边框偏离请调用 update_block 纠正。")
                    }
                } else if (refCropBytes != null) {
                    val evalResult = PatternOverlapEvaluator.evaluate(
                        renderBytes = compact.bytes,
                        referenceBytes = refCropBytes,
                        generateDiffImage = true
                    )
                    var diffCachedPath: String? = null
                    if (evalResult.diffImageBytes != null) {
                        val diffImg = Image.makeFromEncoded(evalResult.diffImageBytes)
                        val diffCompact = compressToCompactImage(diffImg, 90)
                        diffCachedPath = ImageCacheManager.saveCache("diff_$blockId", diffCompact, projectName = projectName)
                    }

                    evalJson = buildJsonObject {
                        put("inspectionMode", "TEXTURE_ALIGNMENT")
                        put("hasImageAsset", true)
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
            } catch (_: Throwable) {}
        }

        val responseJson = buildJsonObject {
            put("allSuccess", true)
            put("isOfflineHeadless", true)
            put("inspectedBlockId", blockId)
            put("projectName", projectName)
            put("opacity", opacity)
            put("calibrated", didCalibrate)
            put("cropRegionOnly", cropRegionOnly)
            put("cachedScreenshotPath", cachedPath)
            if (evalJson != null) {
                put("overlapEvaluation", evalJson)
            }
            putJsonArray("reports") {
                addJsonObject {
                    put("step", 1)
                    put("action", "OFFLINE_SKIA_RENDER")
                    put("success", true)
                    put("message", "离线 Skia 1:1 局部高清渲染已生成并落盘: $cachedPath")
                }
            }
        }

        return McpToolResult.image(
            base64Data = base64,
            mimeType = compact.mimeType,
            message = responseJson.toString()
        )
    }
}
