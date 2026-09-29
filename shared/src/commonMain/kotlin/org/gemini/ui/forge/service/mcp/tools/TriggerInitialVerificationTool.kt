package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CalibrationOptionsManager
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.service.detection.DetectionEngineRegistry
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.mcp.UiRoadmapRegistry
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.UIBlockLayoutNormalizer
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * 首次生成后自动触发程序化自愈与综合校验工具
 * 支持两种模式：
 * 1. UI 前台模式 (vm != null)：驱动工作区弹出校准与自愈检查结果并更新 State
 * 2. 离线无头模式 (vm == null)：直接基于 TemplateRepository 与 SmartEdgeSnapper 在后台完成全量背景纠偏、容器归一化与微观吸附，并将结果落盘保存
 */
class TriggerInitialVerificationTool : McpToolDefinition {
    private val repository get() = TemplateRepository()
    override val name: String = "trigger_initial_verification"
    override val description: String = "在首次生成模板或重大更新后触发自动校验与微观吸附自愈流程，自动将不符合生图条件的复合容器标记为纯容器 (isPureContainer)。支持工作区 UI 模式与后台纯离线模式。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "可选：目标模板工程名称，缺省时自动读取当前活跃工程或生成中工程；未处于工作区时用于纯离线自愈落盘")
            }
            putJsonObject("unlockWhenDone") {
                put("type", "boolean")
                put("description", "可选：完成自愈校验后是否解除大厅的生成中锁定状态，默认 false")
            }
            putJsonObject("onlyUnlock") {
                put("type", "boolean")
                put("description", "可选：仅解除大厅生成中锁定，不执行自愈重写，默认 false")
            }
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: UiRoadmapRegistry.generatingProjects.value.keys.firstOrNull()

        val onlyUnlock = arguments["onlyUnlock"]?.jsonPrimitive?.booleanOrNull ?: false
        val unlockWhenDone = arguments["unlockWhenDone"]?.jsonPrimitive?.booleanOrNull ?: false

        if (onlyUnlock) {
            return if (!projectName.isNullOrBlank()) {
                UiRoadmapRegistry.unmarkProjectGenerating(projectName)
                McpToolResult.text("已成功解除项目 '$projectName' 在大厅的生成中锁定状态。")
            } else {
                McpToolResult.error("未指定 projectName，无法解除锁定状态")
            }
        }

        val vm = McpUiActionPipeline.getActiveViewModel()

        if (vm != null) {
            return try {
                CalibrationOptionsManager.loadIfNeeded()
                vm.calibrateAllBlocks(
                    bindReference = true,
                    saveCropToDisk = CalibrationOptionsManager.alsoCrop
                )
                val targetProject = projectName ?: vm.state.value.projectName
                try {
                    val rerenderInitial = arguments["rerenderInitial"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (rerenderInitial) {
                        org.gemini.ui.forge.service.TemplateOverlayRenderer.renderAndSaveInitialAndLatest(targetProject, vm.state.value.project)
                    } else {
                        org.gemini.ui.forge.service.TemplateOverlayRenderer.renderAndSaveLatest(targetProject, vm.state.value.project)
                    }
                } catch (e: Throwable) {
                    println("[TriggerVerification] 更新标注图异常: ${e.message}")
                }
                McpToolResult.text("成功在 UI 工作区触发全量程序化校验与自愈对齐！不符合独立生图条件的复合模块已自动识别并标为纯容器。")
            } catch (e: Exception) {
                McpToolResult.error("触发校验流程异常: ${e.message}")
            }
        }

        // 离线无头自愈模式
        if (projectName.isNullOrBlank()) {
            return McpToolResult.error("当前未处于模板编辑工作区且未提供 projectName，无法执行离线自愈校验")
        }

        onProgress?.invoke(0.1f, "正在读取模板工程数据: $projectName")
        val state = repository.getTemplateByName(projectName)
            ?: return McpToolResult.error("未找到工程模板 '$projectName'")

        val page = state.pages.firstOrNull()
            ?: return McpToolResult.error("模板工程 '$projectName' 未包含有效页面")

        val refPath = page.sourceImageUri?.getAbsolutePath()
            ?: state.referenceImages.firstOrNull()?.getAbsolutePath()

        if (refPath.isNullOrBlank()) {
            return McpToolResult.error("工程页面未绑定参考底图，无法执行离线边缘吸附与自愈校准")
        }

        val refBytes = readLocalFileBytes(refPath)
            ?: return McpToolResult.error("未能读取参考底图物理文件: $refPath")

        onProgress?.invoke(0.3f, "正在执行离线父级绑定与边缘吸附自愈...")
        val pageW = page.width
        val pageH = page.height
        val aligner = DetectionEngineRegistry.getAligner(DetectionEngineMode.BASELINE_SNAPPER)

        val boundBlocks = page.blocks.bindParents()
        var calibratedCount = 0
        var containerCount = 0

        fun calibrateBlockRecursive(block: UIBlock): UIBlock {
            val newChildren = block.children.map { calibrateBlockRecursive(it) }
            val blockWithChildren = block.copy(children = newChildren)

            // 1. 顶层背景模块自愈：恒等于全屏画布尺寸
            if (blockWithChildren.type == UIBlockType.BACKGROUND && block.parent == null) {
                val fullBounds = SerialRect(0f, 0f, pageW, pageH)
                if (blockWithChildren.bounds != fullBounds) {
                    calibratedCount++
                    AppLogger.i("OfflineCalibrate", "🖼️ 顶层背景模块【${block.id}】离线自动校准为全屏画布尺寸")
                }
                return blockWithChildren.copy(bounds = fullBounds, cropRect = fullBounds)
            }

            // 2. 复合组容器模式：包含子组件时自适应贴合与原点归零
            if (blockWithChildren.children.isNotEmpty() && blockWithChildren.type != UIBlockType.REEL) {
                val normalized = UIBlockLayoutNormalizer.normalizeContainerAndChildren(blockWithChildren)
                calibratedCount++
                containerCount++
                return normalized
            }

            // 3. 叶子图元：微观边缘吸附
            val absBounds = blockWithChildren.toAbsoluteBounds()
            val snapRes = aligner.align(
                imageBytes = refBytes,
                logicalBounds = absBounds,
                blockType = blockWithChildren.type,
                canvasWidth = pageW,
                canvasHeight = pageH
            )

            return if (snapRes != null) {
                val local = blockWithChildren.toLocalBounds(snapRes.logicalRect)
                if (local != blockWithChildren.bounds) {
                    calibratedCount++
                }
                blockWithChildren.copy(
                    bounds = local,
                    cropRect = blockWithChildren.cropRect
                )
            } else {
                blockWithChildren
            }
        }

        val calibratedBlocks = boundBlocks.map { calibrateBlockRecursive(it) }
        val updatedPage = page.copy(blocks = calibratedBlocks)
        val updatedState = state.copy(pages = listOf(updatedPage) + state.pages.drop(1))

        onProgress?.invoke(0.8f, "正在持久化保存离线校对后的模板...")
        repository.saveTemplate(projectName, updatedState)

        // 纯本地离屏渲染更新最新图元标注图并落盘缓存
        try {
            org.gemini.ui.forge.service.TemplateOverlayRenderer.renderAndSaveLatest(projectName, updatedState)
        } catch (e: Exception) {
            println("[TriggerVerification] 更新标注图异常: ${e.message}")
        }

        UiRoadmapRegistry.unmarkProjectGenerating(projectName)

        val message = "成功在后台纯离线执行全量自愈与微观吸附！已校准 $calibratedCount 个图元，标记/规范化 $containerCount 个复合容器，数据已自动保存至 template.json。"
        return McpToolResult.text(message)
    }
}
