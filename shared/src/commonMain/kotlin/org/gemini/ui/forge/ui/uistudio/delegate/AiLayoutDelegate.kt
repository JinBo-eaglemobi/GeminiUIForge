package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.service.studio.LayoutDiffCalculator
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.LayoutUndoSnapshot
import org.gemini.ui.forge.ui.uistudio.UIStudioLeftBottomTab
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.bindParents

/**
 * UI Studio 布局应用领域委托 (aiLayout)
 *
 * 职责范围：
 * - 大模型 apply_view_layout 工具结果的鲁棒提取（官方 Function Call / Markdown 代码块 / 裸文本三通道）
 * - AI 排版方案的确认应用（替换当前视图或新建独立视图）与撤销还原
 * - 布局应用决策弹窗的开关控制
 *
 * 跨域依赖通过构造注入：
 * - [saveProject]：宿主工程持久化函数引用（应用/撤销排版后触发保存）
 */
class AiLayoutDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val jsonHelper: Json,
    private val storage: LocalFileStorage,
    private val saveProject: (TemplateRepository) -> Unit,
) {

    /** 状态桥接属性：使平移自 ViewModel 的方法体可零改动读写宿主状态 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /**
     * 检查并自动执行大模型返回的 apply_view_layout 本地装配任务
     *
     * @param autoPromptDialog 是否在解析完成后主动弹出应用决策弹窗（默认 false，防止生图或会话收口时强行弹窗打断用户；当用户主动点击气泡按钮时传 true）
     */
    fun checkAndExecuteLayoutTool(rawResponseJson: String, responseText: String, autoPromptDialog: Boolean = false) {
        val extractedArgs = extractApplyViewLayoutArgs(rawResponseJson, responseText) ?: return
        val projId = state.project?.projectId ?: "default"
        val applyResult = UiStudioLayoutService.assembleViewPage(
            projectId = projId,
            assetLibrary = state.assetLibrary,
            args = extractedArgs
        )
        if (applyResult.isSuccess && applyResult.createdPage != null) {
            if (autoPromptDialog) {
                // 用户显式触发时才弹出确认对话框，杜绝会话结束自动弹窗打断工作流
                state = state.copy(pendingLayoutResult = applyResult)
            }
        }
    }

    /**
     * 从给定的消息或文本中重新提取排版方案并弹出应用决策弹窗（用户主动点击气泡按钮时触发）
     */
    fun reapplyLayoutFromText(rawText: String) {
        checkAndExecuteLayoutTool(rawText, rawText, autoPromptDialog = true)
    }

    /**
     * 确认应用 AI 生成的界面排版
     *
     * @param replaceCurrent 是否覆盖替换当前视图
     * @param targetName 目标视图名称
     */
    fun confirmApplyLayout(replaceCurrent: Boolean, targetName: String) {
        val result = state.pendingLayoutResult ?: return
        val rawPage = result.createdPage ?: return
        val existingPages = state.project?.studioPages ?: emptyList()

        val currentView = state.currentView ?: existingPages.firstOrNull()
        val finalPage = if (replaceCurrent) {
            // 替换当前正在编辑的视图，继承当前视图的 id 与 name
            val targetId = currentView?.id ?: rawPage.id
            val targetNameStr = currentView?.nameStr ?: targetName.ifBlank { rawPage.nameStr }
            rawPage.copy(
                id = targetId,
                nameStr = targetNameStr
            )
        } else {
            // 作为新视图创建
            val cleanName = targetName.trim().ifBlank { rawPage.nameStr }
            if (existingPages.any { it.nameStr.equals(cleanName, ignoreCase = true) }) {
                Toast.show("视图名称 \"$cleanName\" 已存在，请更换其他新名称", ToastType.ERROR)
                return
            }
            val newId = "page_${cleanName.replace(" ", "_")}_${getCurrentTimeMillis() % 10000}"
            rawPage.copy(
                id = newId,
                nameStr = cleanName
            )
        }

        val oldBlocks = if (replaceCurrent) currentView?.blocks ?: emptyList() else emptyList()
        val newBlocks = finalPage.blocks
        val diffResult = LayoutDiffCalculator.calculateDiff(oldBlocks, newBlocks)
        val undoSnapshot = LayoutUndoSnapshot(
            viewId = finalPage.id,
            viewName = finalPage.nameStr,
            isReplaced = replaceCurrent,
            oldBlocks = oldBlocks,
            newBlocks = newBlocks,
            diff = diffResult
        )

        val updatedPages = if (replaceCurrent) {
            existingPages.map { if (it.id == finalPage.id) finalPage else it }
        } else {
            existingPages + finalPage
        }

        val updatedProject = state.project?.copy(studioPages = updatedPages)
        if (updatedProject != null) {
            state = state.copy(
                project = updatedProject,
                currentViewId = finalPage.id,
                openViewIds = (state.openViewIds + finalPage.id).distinct(),
                pendingLayoutResult = null,
                lastLayoutUndoSnapshot = undoSnapshot,
                isDirty = true,
                isFitToViewport = true,
                leftBottomTab = UIStudioLeftBottomTab.HIERARCHY
            )
            saveProject(TemplateRepository(storage))
            val toastMsg = if (replaceCurrent) {
                "视图 [${finalPage.nameStr}] 已成功应用 AI 排版！"
            } else {
                "🎉 全新视图 [${finalPage.nameStr}] 已创建并在画布呈现！"
            }
            scope.launch(Dispatchers.Main) {
                Toast.show(toastMsg, ToastType.SUCCESS)
            }
        } else {
            state = state.copy(pendingLayoutResult = null)
        }
    }

    /**
     * 撤销上一次 AI 应用的界面排版变更，无损还原图元树或新建的视图
     */
    fun undoLastLayoutChanges() {
        val snapshot = state.lastLayoutUndoSnapshot ?: run {
            Toast.show("当前没有可撤回的 AI 排版变更", ToastType.INFO)
            return
        }
        val existingPages = state.project?.studioPages ?: return
        val updatedPages = if (snapshot.isReplaced) {
            // 还原被替换视图的图元树
            existingPages.map { page ->
                if (page.id == snapshot.viewId) {
                    page.copy(blocks = snapshot.oldBlocks)
                } else page
            }
        } else {
            // 新建视图场景，直接删除该新建页面
            existingPages.filter { it.id != snapshot.viewId }
        }

        val fallbackViewId = if (snapshot.isReplaced) {
            snapshot.viewId
        } else {
            updatedPages.firstOrNull()?.id
        }

        val updatedProject = state.project?.copy(studioPages = updatedPages)
        if (updatedProject != null) {
            state = state.copy(
                project = updatedProject,
                currentViewId = fallbackViewId,
                openViewIds = (state.openViewIds.filter { it != snapshot.viewId } + listOfNotNull(fallbackViewId)).distinct(),
                lastLayoutUndoSnapshot = null,
                isDirty = true,
                isFitToViewport = true,
                leftBottomTab = if (fallbackViewId != null) UIStudioLeftBottomTab.HIERARCHY else state.leftBottomTab
            )
            saveProject(TemplateRepository(storage))
            Toast.show("↩️ 已成功撤回 AI 布局调整，画布已还原", ToastType.SUCCESS)
        }
    }

    /**
     * 放弃或关闭 AI 排版确认弹窗
     */
    fun cancelApplyLayout() {
        state = state.copy(pendingLayoutResult = null)
    }

    /**
     * 执行大模型派发的局部图元补丁 (patch_view_elements)。
     * 具备 All-or-Nothing 事务性、单页面独立原子保存，并自动联动所有引用该页面的图元实例。
     */
    fun executePatchViewElements(args: JsonObject): UiStudioLayoutService.PatchElementsResult {
        val proj = state.project ?: return UiStudioLayoutService.PatchElementsResult(false, errorMessage = "当前未加载工程项目")
        val viewName = args["viewName"]?.jsonPrimitive?.contentOrNull
        val targetPage = if (!viewName.isNullOrBlank()) {
            proj.studioPages.find { it.nameStr.equals(viewName, ignoreCase = true) || it.id == viewName }
                ?: return UiStudioLayoutService.PatchElementsResult(false, errorMessage = "未找到目标视图页面: $viewName")
        } else {
            state.currentView ?: proj.studioPages.firstOrNull()
                ?: return UiStudioLayoutService.PatchElementsResult(false, errorMessage = "当前工作区无可用视图页面")
        }

        val patchResult = UiStudioLayoutService.patchViewElements(targetPage, state.assetLibrary, args)
        if (!patchResult.isSuccess || patchResult.updatedPage == null) {
            return patchResult
        }

        val updatedPage = patchResult.updatedPage
        val repo = TemplateRepository(storage)

        // 1. 更新 studioPages
        var updatedPages = proj.studioPages.map { if (it.id == updatedPage.id) updatedPage else it }

        // 2. 跨页面多重引用镜像同步：将更新后的 blocks 递归同步到所有引用该平级页面的图元内部
        updatedPages = updatedPages.map { p ->
            if (p.id != updatedPage.id) {
                val syncedBlocks = syncReferencedChildren(p.blocks, updatedPage.id, updatedPage.blocks)
                if (syncedBlocks != p.blocks) p.copy(blocks = syncedBlocks) else p
            } else p
        }

        val updatedProj = proj.copy(studioPages = updatedPages)
        state = state.copy(project = updatedProj, isDirty = true)

        // 3. 独立单页面原子刷盘
        scope.launch {
            repo.saveStudioPage(proj.projectId, updatedPage)
        }

        scope.launch(Dispatchers.Main) {
            Toast.show(patchResult.summary, ToastType.SUCCESS)
        }

        return patchResult
    }

    private fun syncReferencedChildren(
        blocks: List<UIStudioBlock>,
        targetPageId: String,
        syncedChildren: List<UIStudioBlock>
    ): List<UIStudioBlock> {
        return blocks.map { block ->
            val updatedBlock = if (block.targetPageId == targetPageId) {
                block.copy(children = syncedChildren)
            } else {
                block
            }
            if (updatedBlock.children.isNotEmpty() && updatedBlock.targetPageId != targetPageId) {
                updatedBlock.copy(children = syncReferencedChildren(updatedBlock.children, targetPageId, syncedChildren))
            } else {
                updatedBlock
            }
        }.bindParents()
    }

    /**
     * 从返回报文或正文代码块中鲁棒提取 apply_view_layout 参数
     */
    private fun extractApplyViewLayoutArgs(rawJson: String, responseText: String): JsonObject? {
        // 通道 1: 官方 Function Call 协议检查
        try {
            if (rawJson.contains("apply_view_layout")) {
                val element = jsonHelper.parseToJsonElement(rawJson)
                val fcObj = findFunctionCallRecursive(element, "apply_view_layout")
                if (fcObj != null) {
                    val args = fcObj["args"]?.jsonObject
                    if (args != null && args.containsKey("blocks")) {
                        return args
                    }
                }
            }
        } catch (_: Exception) {}

        // 通道 2: 文本 Markdown 代码块中嵌入的 JSON 参数检查
        try {
            val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val matches = codeBlockRegex.findAll(responseText)
            for (match in matches) {
                val jsonSnippet = match.groupValues[1].trim()
                if (jsonSnippet.startsWith("{") && (jsonSnippet.contains("blocks") || jsonSnippet.contains("apply_view_layout"))) {
                    val parsed = jsonHelper.parseToJsonElement(jsonSnippet).jsonObject
                    if (parsed.containsKey("blocks")) {
                        return parsed
                    }
                    if (parsed.containsKey("args")) {
                        val inner = parsed["args"]?.jsonObject
                        if (inner != null && inner.containsKey("blocks")) return inner
                    }
                }
            }
        } catch (_: Exception) {}

        // 通道 3: 提取裸输出中包含的 arguments 结构
        try {
            val targetKey = "apply_view_layout"
            val idx = responseText.indexOf(targetKey)
            if (idx >= 0) {
                val braceStart = responseText.indexOf('{', idx)
                if (braceStart >= 0) {
                    val snippet = extractBalancedJsonObject(responseText, braceStart)
                    if (snippet != null) {
                        val parsed = jsonHelper.parseToJsonElement(snippet).jsonObject
                        if (parsed.containsKey("blocks")) return parsed
                        val args = parsed["arguments"]?.jsonObject ?: parsed["args"]?.jsonObject
                        if (args != null && args.containsKey("blocks")) return args
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * 从文本中提取首个括号配对平衡的 JSON 对象片段
     */
    private fun extractBalancedJsonObject(text: String, startIndex: Int): String? {
        var depth = 0
        var start = -1
        for (i in startIndex until text.length) {
            val c = text[i]
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 递归查找任意层级中名称匹配的 functionCall 节点
     */
    private fun findFunctionCallRecursive(element: JsonElement, targetName: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                val fc = element["functionCall"]?.jsonObject ?: element["function_call"]?.jsonObject
                if (fc != null && fc["name"]?.jsonPrimitive?.contentOrNull == targetName) {
                    return fc
                }
                for ((_, v) in element) {
                    val found = findFunctionCallRecursive(v, targetName)
                    if (found != null) return found
                }
            }
            is JsonArray -> {
                for (item in element) {
                    val found = findFunctionCallRecursive(item, targetName)
                    if (found != null) return found
                }
            }
            else -> {}
        }
        return null
    }
}
