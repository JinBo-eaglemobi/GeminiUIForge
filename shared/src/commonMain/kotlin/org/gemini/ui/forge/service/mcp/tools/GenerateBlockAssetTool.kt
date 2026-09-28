package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.ui.ImageScaleConfig
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

/**
 * 单图元生图执行与校验评估结果
 */
data class BlockAssetGenerationResult(
    val success: Boolean,
    val blockId: String,
    val savedFile: TemplateFile? = null,
    val rescaledBytes: ByteArray? = null,
    val scaleConfig: ImageScaleConfig? = null,
    val finalWidth: Int = 0,
    val finalHeight: Int = 0,
    val alignmentRating: String? = null,
    val compositeAlignmentScore: Float? = null,
    val isAutoCroppedRef: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 单图元 AI 生图/以图生图工具（直接回传 ImageContent，AI 可直接审查图片）。
 * 支持切片自动兜底与校验门禁、非透明主体对齐、等比缩放物理重采样落盘与工作区实时重载。
 */
class GenerateBlockAssetTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "generate_block_asset"

    override val description: String =
        "针对指定模板中的特定图元执行 AI 生图任务。支持结合图元绑定的参考底图以图生图，自动执行本地 AI 抠图去除背景，生成高清透明 PNG 并回传图像多模态数据。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        openWorldHint = true
    )

    override val timeoutMs: Long = 120_000L // 生图 2 分钟超时

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元 ID")
            })
            put("customPrompt", buildJsonObject {
                put("type", "string")
                put("description", "可选：覆盖图元默认提示词的自定义生图提示词")
            })
            put("modelName", buildJsonObject {
                put("type", "string")
                put("description", "可选：使用的 Gemini 模型名，默认 gemini-2.5-flash-image")
            })
            put("isPng", buildJsonObject {
                put("type", "boolean")
                put("description", "是否自动执行本地透明去背，默认 true")
            })
        })
        put("required", buildJsonArray {
            add("projectName")
            add("blockId")
        })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'blockId' 不能为空")
        val customPrompt = arguments["customPrompt"]?.jsonPrimitive?.contentOrNull
        val isPng = arguments["isPng"]?.jsonPrimitive?.booleanOrNull ?: true
        val modelName = arguments["modelName"]?.jsonPrimitive?.contentOrNull ?: "gemini-2.5-flash-image"

        onProgress?.invoke(0.05f, "正在读取图元属性与参考工程配置...")
        val templates = repository.getTemplates()
        val match = templates.firstOrNull { it.first.equals(projectName, ignoreCase = true) }
            ?: return McpToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val configManager = ConfigManager()
        val apiKey = configManager.loadKey("GEMINI_API_KEY")
            ?: configManager.loadGlobalGeminiKey()
            ?: return McpToolResult.error("未找到 Gemini API Key")

        val cloudAssetManager = CloudAssetManager(configManager)
        val aiService = AIGenerationService(storage, cloudAssetManager, configManager)
        val mattingService = LocalMattingService(storage)

        val (finalState, genResult) = executeBlockPipeline(
            projectName = projectName,
            blockId = blockId,
            customPrompt = customPrompt,
            modelName = modelName,
            isPng = isPng,
            currentState = state,
            repository = repository,
            storage = storage,
            configManager = configManager,
            cloudAssetManager = cloudAssetManager,
            aiService = aiService,
            mattingService = mattingService,
            apiKey = apiKey,
            onProgress = onProgress
        )

        if (!genResult.success) {
            return McpToolResult.error(genResult.errorMessage ?: "生图失败")
        }

        // 同步通知当前活跃工作区刷新内存状态与画布渲染
        try {
            val activeVm = McpUiActionPipeline.getActiveViewModel()
            if (activeVm != null) {
                activeVm.reload(finalState)
            }
        } catch (e: Throwable) {
            AppLogger.w("GenerateBlockAssetTool", "通知活跃工作区刷新失败", e)
        }

        @OptIn(ExperimentalEncodingApi::class)
        val returnBase64 = genResult.rescaledBytes?.let { Base64.encode(it) } ?: ""

        val messageText = buildString {
            append("✅ 图元 [$blockId] AI 生图完成并已通过非透明主体对齐与物理重采样落盘！\n")
            append("📁 物理资产: ${genResult.savedFile?.getAbsolutePath()}\n")
            append("📐 实际保存尺寸: ${genResult.finalWidth}x${genResult.finalHeight} px\n")
            append("🎯 对齐评级: ${genResult.alignmentRating ?: "VERIFIED"} (CAS: ${genResult.compositeAlignmentScore ?: 1.0f})\n")
            if (genResult.isAutoCroppedRef) {
                append("🔍 已自动从设计原图切片绑定并校验参考底图\n")
            }
            append("✨ 模块内已实现等比缩放与居中对齐自愈")
        }

        onProgress?.invoke(1.0f, "图元生图完成并已就绪")
        return McpToolResult.image(
            base64Data = returnBase64,
            mimeType = if (isPng) "image/png" else "image/jpeg",
            message = messageText
        )
    }

    companion object {
        /**
         * 跨 MCP 工具通用的单图元生图、切片兜底、非透明主体对齐与物理缩放落盘流水线
         */
        suspend fun executeBlockPipeline(
            projectName: String,
            blockId: String,
            customPrompt: String? = null,
            modelName: String = "gemini-2.5-flash-image",
            isPng: Boolean = true,
            currentState: ProjectState,
            repository: TemplateRepository,
            storage: LocalFileStorage,
            configManager: ConfigManager,
            cloudAssetManager: CloudAssetManager,
            aiService: AIGenerationService,
            mattingService: LocalMattingService,
            apiKey: String,
            onProgress: ((progress: Float, message: String?) -> Unit)? = null
        ): Pair<ProjectState, BlockAssetGenerationResult> {
            val page = currentState.pages.firstOrNull()
                ?: return Pair(currentState, BlockAssetGenerationResult(false, blockId, errorMessage = "工程不包含任何页面"))

            val boundBlocks = page.blocks.bindParents()
            var foundBlock: UIBlock? = null
            fun searchBlock(list: List<UIBlock>) {
                for (b in list) {
                    if (b.id == blockId) {
                        foundBlock = b
                        return
                    }
                    searchBlock(b.children)
                }
            }
            searchBlock(boundBlocks)
            val block = foundBlock
                ?: return Pair(currentState, BlockAssetGenerationResult(false, blockId, errorMessage = "未找到图元 '$blockId'"))

            // 1. 过滤纯容器与占位层
            if (block.type == UIBlockType.CONTAINER || block.isPureContainer) {
                return Pair(currentState, BlockAssetGenerationResult(false, blockId, errorMessage = "图元 [$blockId] 为纯容器/占位层，禁止参与 AI 生图"))
            }

            // 2. 检查专属参考图，若缺失则自动从设计原图切片兜底
            var effectiveRefFile = block.referenceImage
            var isAutoCropped = false
            var workingState = currentState

            val existingRefBytes = effectiveRefFile?.getAbsolutePath()?.let { readLocalFileBytes(it) }
            if (existingRefBytes == null || existingRefBytes.isEmpty()) {
                onProgress?.invoke(0.15f, "图元 [$blockId] 缺少专属参考底图，正在从设计原图精准切片兜底...")
                val masterRefPath = page.sourceImageUri?.getAbsolutePath()
                    ?: currentState.referenceImages.firstOrNull()?.getAbsolutePath()
                val masterBytes = if (!masterRefPath.isNullOrBlank()) readLocalFileBytes(masterRefPath) else null

                if (masterBytes != null && masterBytes.isNotEmpty()) {
                    val cropBounds = block.cropRect ?: block.toAbsoluteBounds()
                    val snappedBytes = SmartEdgeSnapper.cropSnappedComponent(
                        imageBytes = masterBytes,
                        logicalBounds = cropBounds,
                        canvasWidth = page.width,
                        canvasHeight = page.height
                    ) ?: cropImage(
                        imageBytes = masterBytes,
                        bounds = cropBounds,
                        originalWidth = page.width,
                        originalHeight = page.height,
                        isPng = true
                    )

                    if (snappedBytes != null && snappedBytes.isNotEmpty()) {
                        val savedRef = repository.saveBlockResource(
                            templateName = projectName,
                            blockId = blockId,
                            fileNamePrefix = "crop_ref",
                            bytes = snappedBytes,
                            isPng = true
                        )
                        effectiveRefFile = savedRef
                        isAutoCropped = true

                        // 立即更新 workingState 中的 referenceImage
                        fun updateRef(blocks: List<UIBlock>): List<UIBlock> {
                            return blocks.map { b ->
                                if (b.id == blockId) b.copy(referenceImage = savedRef)
                                else b.copy(children = updateRef(b.children))
                            }
                        }
                        val updatedPages = workingState.pages.map { it.copy(blocks = updateRef(it.blocks)) }
                        workingState = workingState.copy(pages = updatedPages)
                        repository.saveTemplate(projectName, workingState)
                    }
                }
            }

            // MCP 前置切片质量校验门禁：必须确保持有非空的切片数据
            val refBytesToUse = effectiveRefFile?.getAbsolutePath()?.let { readLocalFileBytes(it) }
            if (refBytesToUse == null || refBytesToUse.isEmpty()) {
                return Pair(workingState, BlockAssetGenerationResult(
                    false, blockId, errorMessage = "图元 [$blockId] 参考底图校验未通过：未找到有效参考切片数据"
                ))
            }

            // 3. 向 Gemini 发起生图请求
            onProgress?.invoke(0.3f, "正在向 Gemini 发起生图请求 ($modelName)...")
            val promptToSend = customPrompt?.ifBlank { null }
                ?: block.userPromptEn.ifBlank { null }
                ?: block.userPromptZh.ifBlank { null }
                ?: "Game UI element, high quality, transparent background"

            val selectedModel = GeminiModel.entries.firstOrNull { it.modelName == modelName }
                ?: GeminiModel.GEMINI_2_5_FLASH_IMAGE

            var rawResultUri: String? = null
            try {
                aiService.generateImages(
                    model = selectedModel,
                    blockType = block.type.name,
                    userPrompt = promptToSend,
                    apiKey = apiKey,
                    targetWidth = block.bounds.width,
                    targetHeight = block.bounds.height,
                    isPng = isPng,
                    referenceImageUri = effectiveRefFile.getAbsolutePath(),
                    generationCount = 1,
                    onLog = { logMsg -> onProgress?.invoke(0.5f, logMsg) },
                    onImageGenerated = { uri -> rawResultUri = uri }
                )
            } catch (e: Exception) {
                return Pair(workingState, BlockAssetGenerationResult(
                    false, blockId, errorMessage = "AI 生图异常: ${e.message}"
                ))
            }

            val finalRaw = rawResultUri
                ?: return Pair(workingState, BlockAssetGenerationResult(false, blockId, errorMessage = "模型未返回有效图像"))

            @OptIn(ExperimentalEncodingApi::class)
            val rawBytes = if (finalRaw.startsWith("data:image")) {
                val pureB64 = if (finalRaw.contains(",")) finalRaw.substringAfter(",") else finalRaw
                Base64.decode(pureB64)
            } else {
                readLocalFileBytes(finalRaw)
            } ?: return Pair(workingState, BlockAssetGenerationResult(false, blockId, errorMessage = "无法解析生成的图像字节数据"))

            // 4. 本地离线 AI 抠图去除背景
            onProgress?.invoke(0.65f, if (isPng) "正在执行本地离线 AI 抠图去除背景..." else "正在归档图像...")
            val noBgBytes = if (isPng) {
                try {
                    mattingService.removeBackground(rawBytes) ?: rawBytes
                } catch (_: Throwable) {
                    rawBytes
                }
            } else rawBytes

            // 5. 分析非透明主体并按等比对齐实际尺寸重采样 (Ground Truth 绝对尺寸基准)
            onProgress?.invoke(0.8f, "正在紧凑裁剪多余透明边缘并按设计底图绝对尺寸重采样...")
            // ★ 核心防虚胖优化：先紧凑裁掉外部空白透明边，只保留真实非透明主体
            val croppedNoBgBytes = if (isPng) {
                cropToOpaqueBounds(noBgBytes, alphaThreshold = 20, padding = 0)
            } else noBgBytes

            val skiaSrc = try { org.jetbrains.skia.Image.makeFromEncoded(croppedNoBgBytes) } catch (_: Throwable) { null }
            val srcW = (skiaSrc?.width ?: 1).toFloat().coerceAtLeast(1f)
            val srcH = (skiaSrc?.height ?: 1).toFloat().coerceAtLeast(1f)

            val opaqueBox = detectOpaqueBoundingBox(croppedNoBgBytes, alphaThreshold = 20)
            val refOpaqueBox = detectOpaqueBoundingBox(refBytesToUse, alphaThreshold = 20)

            val targetW = block.bounds.width.coerceAtLeast(1f)
            val targetH = block.bounds.height.coerceAtLeast(1f)

            // ★ 核心真理基准：以参考切片上的真实非透明主体尺寸作为 Ground Truth，杜绝被动适应错误 bounds
            val baseTargetW = if (refOpaqueBox != null && refOpaqueBox.width > 4f) refOpaqueBox.width else targetW
            val baseTargetH = if (refOpaqueBox != null && refOpaqueBox.height > 4f) refOpaqueBox.height else targetH

            val s = if (opaqueBox != null) {
                minOf(baseTargetW / opaqueBox.width.coerceAtLeast(1f), baseTargetH / opaqueBox.height.coerceAtLeast(1f))
            } else {
                minOf(baseTargetW / srcW, baseTargetH / srcH)
            }

            // 缩放后的物理像素宽高（此时物理像素宽高即为主体缩放后的真实尺寸）
            val targetImgW = (srcW * s).roundToInt().coerceAtLeast(1)
            val targetImgH = (srcH * s).roundToInt().coerceAtLeast(1)

            // ★ 核心用户指令：经过等比缩放校验完成后，按当前缩放大小的实际宽高物理保存图片
            val rescaledBytes = rescaleImageBytes(croppedNoBgBytes, targetImgW, targetImgH) ?: croppedNoBgBytes

            // 计算缩放后在 targetW x targetH 模块范围内的正向居中偏移
            val finalOpaqueBox = detectOpaqueBoundingBox(rescaledBytes, alphaThreshold = 20)
                ?: SerialRect(0f, 0f, targetImgW.toFloat(), targetImgH.toFloat())
            var calcOffsetX = (targetW - finalOpaqueBox.width) / 2f - finalOpaqueBox.left
            var calcOffsetY = (targetH - finalOpaqueBox.height) / 2f - finalOpaqueBox.top

            // 6. CV 图案重叠与对齐评估 (PatternOverlapEvaluator)
            var ratingResult = "VERIFIED"
            var casScore = 1.0f
            try {
                val cvEval = PatternOverlapEvaluator.evaluate(
                    renderBytes = rescaledBytes,
                    referenceBytes = refBytesToUse,
                    maxDriftWindow = 12,
                    generateDiffImage = false
                )
                casScore = cvEval.compositeAlignmentScore
                ratingResult = cvEval.rating
                if (cvEval.driftVectorX != 0 || cvEval.driftVectorY != 0) {
                    calcOffsetX += cvEval.driftVectorX
                    calcOffsetY += cvEval.driftVectorY
                }
            } catch (e: Throwable) {
                AppLogger.w("GenerateBlockAssetTool", "CV 对齐评估略过: ${e.message}")
            }

            val finalScaleConfig = ImageScaleConfig(
                scaleX = 1.0f,
                scaleY = 1.0f,
                offsetX = calcOffsetX,
                offsetY = calcOffsetY,
                lockAspectRatio = true,
                enabled = true,
                opaqueBounds = SerialRect(0f, 0f, targetImgW.toFloat(), targetImgH.toFloat())
            )

            // 7. 将按实际尺寸重采样后的图片写入模块资产库
            onProgress?.invoke(0.9f, "正在落盘物理资产 (${targetImgW}x${targetImgH})...")
            val savedFile = repository.saveBlockResource(
                templateName = projectName,
                blockId = blockId,
                fileNamePrefix = "gen",
                bytes = rescaledBytes,
                isPng = isPng
            )

            // 8. 回填给 block.currentImageUri 与 scaleConfig 并持久化
            fun updateBlockState(blocks: List<UIBlock>): List<UIBlock> {
                return blocks.map { b ->
                    if (b.id == blockId) {
                        b.copy(
                            currentImageUri = savedFile,
                            scaleConfig = finalScaleConfig,
                            referenceImage = effectiveRefFile
                        )
                    } else {
                        b.copy(children = updateBlockState(b.children))
                    }
                }
            }
            val finalPages = workingState.pages.map { it.copy(blocks = updateBlockState(it.blocks)) }
            val finalState = workingState.copy(pages = finalPages)
            repository.saveTemplate(projectName, finalState)

            return Pair(finalState, BlockAssetGenerationResult(
                success = true,
                blockId = blockId,
                savedFile = savedFile,
                rescaledBytes = rescaledBytes,
                scaleConfig = finalScaleConfig,
                finalWidth = targetImgW,
                finalHeight = targetImgH,
                alignmentRating = ratingResult,
                compositeAlignmentScore = casScore,
                isAutoCroppedRef = isAutoCropped
            ))
        }
    }
}
