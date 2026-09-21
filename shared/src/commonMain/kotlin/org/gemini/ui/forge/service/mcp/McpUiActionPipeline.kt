package org.gemini.ui.forge.service.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.gemini.ui.forge.captureActiveScreenShot
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.service.detection.DetectionEngineMode
import org.gemini.ui.forge.utils.ImageCacheManager
import org.gemini.ui.forge.utils.compressToCompactImage
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MCP UI 操作指令定义
 */
@Serializable
data class McpUiAction(
    val type: String, // SELECT_BLOCK, ISOLATE_BLOCK, RESTORE_VISIBILITY, SET_REFERENCE_MODE, SET_REFERENCE_OPACITY, FOCUS_BLOCK_ON_CANVAS, TRIGGER_CALIBRATE, TOGGLE_OUTLINES, WAIT_MS
    val blockId: String? = null,
    val mode: String? = null,
    val opacity: Float? = null,
    val delayMs: Long? = null,
    val alsoCropAndBind: Boolean? = null,
    val engineMode: String? = null
)

/**
 * 单步执行结果报告
 */
@Serializable
data class McpUiStepReport(
    val stepIndex: Int,
    val actionType: String,
    val isSuccess: Boolean,
    val durationMs: Long,
    val message: String
)

/**
 * 完整指令流水线执行结果
 */
@Serializable
data class McpUiSequenceResult(
    val totalSteps: Int,
    val successfulSteps: Int,
    val allSuccess: Boolean,
    val reports: List<McpUiStepReport>,
    val screenshotBase64: String? = null,
    val screenshotCachedPath: String? = null,
    val errorMessage: String? = null
)

/**
 * 前端真实 UI 动作执行管线调度器 (单例)
 * 接收来自 MCP 的动作序列，真实调度当前活跃界面的 ViewModel 与状态机
 */
object McpUiActionPipeline {

    private var activeViewModel: ProjectWorkspaceViewModel? = null

    // 用于恢复图层显隐状态的快照映射: blockId -> isVisible
    private var visibilitySnapshot: Map<String, Boolean>? = null

    /**
     * 工作区界面进入时注册当前活动的 ViewModel 引用
     */
    fun registerActiveViewModel(viewModel: ProjectWorkspaceViewModel) {
        activeViewModel = viewModel
    }

    /**
     * 工作区界面销毁或离开时注销引用
     */
    fun unregisterActiveViewModel(viewModel: ProjectWorkspaceViewModel) {
        if (activeViewModel === viewModel) {
            activeViewModel = null
        }
    }

    /**
     * 获取当前活动的 ViewModel
     */
    fun getActiveViewModel(): ProjectWorkspaceViewModel? = activeViewModel

    /**
     * 顺序执行一系列 UI 真实操作动作
     */
    suspend fun executeSequence(
        projectName: String?,
        actions: List<McpUiAction>,
        captureScreenshot: Boolean = false
    ): McpUiSequenceResult = withContext(Dispatchers.Main) {
        val vm = activeViewModel
        if (vm == null) {
            return@withContext McpUiSequenceResult(
                totalSteps = actions.size,
                successfulSteps = 0,
                allSuccess = false,
                reports = emptyList(),
                errorMessage = "当前工作区未打开或未处于激活状态，无法模拟真实 UI 交互。"
            )
        }

        // 若指定了 projectName 且与当前打开的工程不一致，给出提示
        if (!projectName.isNullOrBlank() && vm.state.value.projectName != projectName) {
            return@withContext McpUiSequenceResult(
                totalSteps = actions.size,
                successfulSteps = 0,
                allSuccess = false,
                reports = emptyList(),
                errorMessage = "当前打开的工程为 '${vm.state.value.projectName}'，与指定的 '$projectName' 不符。"
            )
        }

        val reports = mutableListOf<McpUiStepReport>()
        var successCount = 0

        for ((index, action) in actions.withIndex()) {
            val startTime = org.gemini.ui.forge.getCurrentTimeMillis()
            var stepSuccess = true
            var stepMsg = "OK"

            try {
                when (action.type.uppercase()) {
                    "SELECT_BLOCK" -> {
                        val targetId = action.blockId
                        if (targetId.isNullOrBlank()) {
                            stepSuccess = false
                            stepMsg = "缺少 blockId"
                        } else {
                            vm.onBlockClicked(targetId)
                            stepMsg = "已选中图元: $targetId"
                        }
                    }

                    "ISOLATE_BLOCK" -> {
                        val targetId = action.blockId ?: vm.state.value.selectedBlockId
                        if (targetId.isNullOrBlank()) {
                            stepSuccess = false
                            stepMsg = "缺少 blockId 且当前无选中的图元"
                        } else {
                            // 1. 记录快照
                            val allBlocks = vm.state.value.currentPage?.blocks ?: emptyList()
                            val snapshot = mutableMapOf<String, Boolean>()
                            fun recordRecursive(list: List<UIBlock>) {
                                for (b in list) {
                                    snapshot[b.id] = b.isVisible
                                    recordRecursive(b.children)
                                }
                            }
                            recordRecursive(allBlocks)
                            visibilitySnapshot = snapshot

                            // 2. 找到目标模块及其所有祖先 ID，其余全部隐藏
                            val ancestorIds = mutableSetOf<String>()
                            fun findAncestors(list: List<UIBlock>, currentPath: List<String>): Boolean {
                                for (b in list) {
                                    if (b.id == targetId) {
                                        ancestorIds.addAll(currentPath)
                                        ancestorIds.add(b.id)
                                        return true
                                    }
                                    if (findAncestors(b.children, currentPath + b.id)) return true
                                }
                                return false
                            }
                            findAncestors(allBlocks, emptyList())

                            // 递归更新显隐：目标和其所有祖先保持 true，其余 false
                            fun updateVisibility(list: List<UIBlock>): List<UIBlock> {
                                return list.map { b ->
                                    val shouldVisible = ancestorIds.contains(b.id)
                                    b.copy(
                                        isVisible = shouldVisible,
                                        children = updateVisibility(b.children)
                                    )
                                }
                            }
                            val updatedBlocks = updateVisibility(allBlocks)
                            val pageId = vm.state.value.selectedPageId
                            if (pageId != null) {
                                vm.updateState { s ->
                                    val pages = s.project.pages.map { p ->
                                        if (p.id == pageId) p.copy(blocks = updatedBlocks) else p
                                    }
                                    s.copy(project = s.project.copy(pages = pages))
                                }
                            }
                            // 选中该目标
                            vm.onBlockClicked(targetId)
                            stepMsg = "已成功隔离图元 $targetId (其余模块已暂时隐藏)"
                        }
                    }

                    "RESTORE_VISIBILITY" -> {
                        val snapshot = visibilitySnapshot
                        if (snapshot == null) {
                            // 若无快照，直接将全部图元可见
                            vm.layoutEditor.toggleAllBlocksVisibility(true)
                            stepMsg = "未找到历史显隐快照，已默认一键开启全部图元可见"
                        } else {
                            val allBlocks = vm.state.value.currentPage?.blocks ?: emptyList()
                            fun restoreRecursive(list: List<UIBlock>): List<UIBlock> {
                                return list.map { b ->
                                    val origVis = snapshot[b.id] ?: true
                                    b.copy(isVisible = origVis, children = restoreRecursive(b.children))
                                }
                            }
                            val restoredBlocks = restoreRecursive(allBlocks)
                            val pageId = vm.state.value.selectedPageId
                            if (pageId != null) {
                                vm.updateState { s ->
                                    val pages = s.project.pages.map { p ->
                                        if (p.id == pageId) p.copy(blocks = restoredBlocks) else p
                                    }
                                    s.copy(project = s.project.copy(pages = pages))
                                }
                            }
                            visibilitySnapshot = null
                            stepMsg = "已成功还原图层显隐快照"
                        }
                    }

                    "SET_REFERENCE_MODE" -> {
                        val modeStr = action.mode?.uppercase() ?: "OVERLAY"
                        val refMode = when (modeStr) {
                            "SPLIT" -> ReferenceDisplayMode.SPLIT
                            "OVERLAY" -> ReferenceDisplayMode.OVERLAY
                            "HIDDEN" -> ReferenceDisplayMode.HIDDEN
                            else -> ReferenceDisplayMode.OVERLAY
                        }
                        vm.updateReferenceMode(refMode)
                        stepMsg = "已切换参考图模式为: $refMode"
                    }

                    "SET_REFERENCE_OPACITY" -> {
                        val opacity = (action.opacity ?: 0.5f).coerceIn(0.1f, 1.0f)
                        vm.updateReferenceOpacity(opacity)
                        stepMsg = "已设置参考图半透明叠加度为: $opacity"
                    }

                    "FOCUS_BLOCK_ON_CANVAS" -> {
                        val targetId = action.blockId ?: vm.state.value.selectedBlockId
                        if (targetId != null) {
                            vm.onBlockClicked(targetId)
                            stepMsg = "已聚焦选中图元 $targetId"
                        } else {
                            stepSuccess = false
                            stepMsg = "缺少 blockId"
                        }
                    }

                    "TRIGGER_CALIBRATE" -> {
                        val engine = when (action.engineMode?.uppercase()) {
                            "CV", "CLASSIC_CV" -> DetectionEngineMode.CLASSIC_CV
                            "AI", "ONNX_AI" -> DetectionEngineMode.ONNX_AI
                            else -> DetectionEngineMode.BASELINE_SNAPPER
                        }
                        val alsoCrop = action.alsoCropAndBind ?: false
                        val targetId = action.blockId
                        if (targetId != null && targetId != vm.state.value.selectedBlockId) {
                            vm.onBlockClicked(targetId)
                        }
                        vm.calibrateSelectedBlock(alsoCropAndBindReference = alsoCrop, engineMode = engine)
                        stepMsg = "已触发真实校准 (引擎: ${engine.displayName}, 切片: $alsoCrop)"
                    }

                    "TOGGLE_OUTLINES" -> {
                        vm.updateState { it.copy(isHideOutlines = !it.isHideOutlines) }
                        stepMsg = "已切换组件外轮廓显示状态"
                    }

                    "WAIT_MS" -> {
                        val ms = action.delayMs ?: 300L
                        delay(ms)
                        stepMsg = "已等待渲染完成 ${ms}ms"
                    }

                    else -> {
                        stepSuccess = false
                        stepMsg = "未知动作指令: ${action.type}"
                    }
                }
            } catch (e: Exception) {
                stepSuccess = false
                stepMsg = "执行失败: ${e.message}"
            }

            val duration = org.gemini.ui.forge.getCurrentTimeMillis() - startTime
            reports.add(
                McpUiStepReport(
                    stepIndex = index + 1,
                    actionType = action.type,
                    isSuccess = stepSuccess,
                    durationMs = duration,
                    message = stepMsg
                )
            )
            if (stepSuccess) successCount++

            // 每步默认给予轻微 UI 响应缓冲
            delay(50L)
        }

        // 是否截取执行后的整屏画面
        var screenshot: String? = null
        var screenshotCachedPath: String? = null
        if (captureScreenshot) {
            try {
                // 等待一小帧渲染落定
                delay(100L)
                val bytes = captureActiveScreenShot()
                if (bytes != null && bytes.isNotEmpty()) {
                    // 统一走公共工具压缩中枢：WEBP 优先 → JPEG 降级，并异步落盘缓存
                    val compact = compressToCompactImage(bytes)
                    screenshotCachedPath = ImageCacheManager.saveCache("ui_action", compact)
                    @OptIn(ExperimentalEncodingApi::class)
                    screenshot = Base64.encode(compact.bytes)
                }
            } catch (e: Exception) {
                // 截图异常不阻断主流程
            }
        }

        McpUiSequenceResult(
            totalSteps = actions.size,
            successfulSteps = successCount,
            allSuccess = successCount == actions.size,
            reports = reports,
            screenshotBase64 = screenshot,
            screenshotCachedPath = screenshotCachedPath
        )
    }
}
