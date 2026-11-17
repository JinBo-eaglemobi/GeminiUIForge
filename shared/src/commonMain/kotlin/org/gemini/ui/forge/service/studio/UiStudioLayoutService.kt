package org.gemini.ui.forge.service.studio

import kotlinx.serialization.json.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.asset.AssetManifestHelper
import org.gemini.ui.forge.utils.asset.AssetManifestHelper.AssetSemanticCategory
import org.gemini.ui.forge.utils.bindParents

/**
 * UI Studio 界面全自动装配与排版落地核心服务。
 *
 * 核心职能：
 * 1. 响应大模型发起的 [apply_view_layout] 本地工具调用；
 * 2. 贯彻局部相对坐标系与纯容器铁律；
 * 3. 自动根据切片文件名 (assetFileName) 匹配工程素材库物理资源并绑定哈希引用；
 * 4. 递归装配 UIStudioBlock 树并构建完整 UIStudioPage 视图对象。
 */
object UiStudioLayoutService {
    private const val TAG = "UiStudioLayoutService"

    /**
     * 工具执行返回结果
     */
    data class ApplyLayoutResult(
        val isSuccess: Boolean,
        val viewName: String,
        val blockCount: Int,
        val matchedAssetCount: Int = 0,
        val createdPage: UIStudioPage? = null,
        val summary: String = "",
        val errorMessage: String? = null
    )

    /**
     * 万能排版工具 [edit_view_layout] 执行结果
     */
    data class EditLayoutResult(
        val isSuccess: Boolean,
        val summary: String = "",
        val errorMessage: String? = null,
        val updatedPages: List<UIStudioPage> = emptyList(),
        val affectedViewIds: List<String> = emptyList(),
        val isPageReplacedOrCreated: Boolean = false,
        val activeViewId: String? = null
    )

    /**
     * 响应大模型发起的 [edit_view_layout] 万能视图排版工具调用。
     * 遵循统一数据模型（mode + elements 统一 key），并原生支持单次调用内多 operations 数组流水线批量执行。
     */
    fun editViewLayout(
        projectId: String,
        assetLibrary: UIAssetLibrary,
        args: JsonObject,
        availablePages: List<UIStudioPage>,
        currentView: UIStudioPage?
    ): EditLayoutResult {
        return try {
            // 1. 规范化 operations 操作列表（支持数组与单操作自适应）
            val rawOperations = args["operations"]?.jsonArray
            val operations: List<JsonObject> = if (rawOperations != null && rawOperations.isNotEmpty()) {
                rawOperations.mapNotNull { it as? JsonObject }
            } else {
                // 若传入单操作扁平格式，自适应包装为包含 1 项的列表
                listOf(args)
            }

            if (operations.isEmpty()) {
                return EditLayoutResult(
                    isSuccess = false,
                    summary = "",
                    errorMessage = "未在入参中检测到有效的 operations 排版操作列表"
                )
            }

            // 建立素材库索引表 (忽略大小写：覆盖显示名、物理文件名及内容哈希)
            val assetMap = mutableMapOf<String, UIAssetItem>()
            assetLibrary.items.forEach { asset ->
                assetMap[asset.name.lowercase()] = asset
                val cleanName = asset.name.substringBeforeLast('.').lowercase()
                assetMap[cleanName] = asset

                val realFileName = asset.relativePath.substringAfterLast('/').substringAfterLast('\\').lowercase()
                if (realFileName.isNotBlank()) {
                    assetMap[realFileName] = asset
                    assetMap[realFileName.substringBeforeLast('.')] = asset
                }
                asset.hash?.lowercase()?.takeIf { it.isNotBlank() }?.let { hash ->
                    assetMap[hash] = asset
                }
            }

            var workingPages = availablePages.toMutableList()
            val affectedIds = mutableSetOf<String>()
            var isAnyPageReplacedOrCreated = false
            var finalActiveViewId: String? = null
            val stepSummaries = mutableListOf<String>()

            // 2. 依次执行每个 operation 操作单元
            for ((index, op) in operations.withIndex()) {
                val mode = op["mode"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() ?: "PATCH"
                val rawViewName = op["viewName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: op["viewId"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: args["viewName"]?.jsonPrimitive?.contentOrNull?.trim()

                // 定位目标页面
                var targetPage: UIStudioPage? = if (!rawViewName.isNullOrBlank()) {
                    workingPages.find { it.id.equals(rawViewName, ignoreCase = true) }
                } else {
                    currentView ?: workingPages.firstOrNull()
                }

                // 提取统一图元数据
                val rawElements = op["elements"]?.jsonArray ?: JsonArray(emptyList())

                when (mode) {
                    "REPLACE_PAGE" -> {
                        val sanitizedName = rawViewName?.let { name ->
                            val hasChinese = name.any { it in '\u4e00'..'\u9fa5' }
                            if (hasChinese) {
                                val asciiOnly = name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
                                if (asciiOnly.isNotBlank()) asciiOnly else "View_${org.gemini.ui.forge.getCurrentTimeMillis() % 10000}"
                            } else name
                        } ?: targetPage?.id ?: "AutoGeneratedView"

                        val width = op["canvasWidth"]?.jsonPrimitive?.intOrNull?.toFloat()
                            ?: op["width"]?.jsonPrimitive?.intOrNull?.toFloat()
                            ?: targetPage?.width
                            ?: 1280f
                        val height = op["canvasHeight"]?.jsonPrimitive?.intOrNull?.toFloat()
                            ?: op["height"]?.jsonPrimitive?.intOrNull?.toFloat()
                            ?: targetPage?.height
                            ?: 720f

                        // 数据审核
                        val validationError = validateViewLayoutData(rawElements, workingPages, assetLibrary)
                        if (validationError != null) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "第 ${index + 1} 项操作 (REPLACE_PAGE) 数据格式审核失败: $validationError"
                            )
                        }

                        // 解析图元树
                        val assembledBlocks = mutableListOf<UIStudioBlock>()
                        val slotRoleMap = mutableMapOf<String, String>()
                        for (element in rawElements) {
                            val blockObj = element as? JsonObject ?: continue
                            parseBlockRecursive(blockObj, assetMap, slotRoleMap, assembledBlocks)
                        }

                        val finalBlocks = if (assetLibrary.items.isNotEmpty()) {
                            autoFitAssetsToSlots(assembledBlocks, slotRoleMap, assetLibrary)
                        } else assembledBlocks

                        val newPageId = targetPage?.id ?: sanitizedName.replace(" ", "_")
                        val newPage = UIStudioPage(
                            id = newPageId,
                            width = width,
                            height = height,
                            blocks = finalBlocks
                        )
                        newPage.blocks.bindParents()

                        // 更新工作集
                        if (targetPage != null) {
                            workingPages = workingPages.map { if (it.id == targetPage.id) newPage else it }.toMutableList()
                        } else {
                            workingPages.add(newPage)
                        }
                        affectedIds.add(newPage.id)
                        isAnyPageReplacedOrCreated = true
                        finalActiveViewId = newPage.id
                        stepSummaries.add("重构整页 [$newPageId] (${width.toInt()}x${height.toInt()}, ${finalBlocks.size} 个顶层图元)")
                    }

                    "REPLACE_CONTAINER" -> {
                        val targetContainerId = op["targetId"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?: op["targetContainerId"]?.jsonPrimitive?.contentOrNull?.trim()
                        if (targetContainerId.isNullOrBlank()) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "第 ${index + 1} 项操作 (REPLACE_CONTAINER) 缺少必填参数 'targetId' (目标容器ID)"
                            )
                        }
                        val page = targetPage ?: return EditLayoutResult(
                            isSuccess = false,
                            summary = "",
                            errorMessage = "第 ${index + 1} 项操作 (REPLACE_CONTAINER) 未找到目标视图页面: $rawViewName"
                        )

                        val existingContainer = findBlockRecursively(page.blocks, targetContainerId)
                        if (existingContainer == null) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "在视图 [${page.id}] 中未找到目标容器图元 '$targetContainerId'"
                            )
                        }
                        if (!existingContainer.targetPageId.isNullOrBlank()) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "目标图元 '$targetContainerId' 是独立页面引用实例 (targetPageId='${existingContainer.targetPageId}')，架构红线严禁向引用图元注入 children！请直接针对目标视图页面 [${existingContainer.targetPageId}] 发起 REPLACE_PAGE 操作进行重构。"
                            )
                        }

                        // 前置数据校验
                        val validationError = validateViewLayoutData(rawElements, workingPages, assetLibrary)
                        if (validationError != null) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "第 ${index + 1} 项操作 (REPLACE_CONTAINER) 格式审核未通过: $validationError"
                            )
                        }
                        val assembledBlocks = mutableListOf<UIStudioBlock>()
                        val slotRoleMap = mutableMapOf<String, String>()
                        for (element in rawElements) {
                            val blockObj = element as? JsonObject ?: continue
                            parseBlockRecursive(blockObj, assetMap, slotRoleMap, assembledBlocks)
                        }

                        val finalChildren = if (assetLibrary.items.isNotEmpty()) {
                            autoFitAssetsToSlots(assembledBlocks, slotRoleMap, assetLibrary)
                        } else assembledBlocks

                        // 递归替换容器的直接 children，保持容器外壳与几何属性不变
                        val updatedBlocks = updateBlockRecursively(page.blocks, targetContainerId) { container ->
                            container.copy(children = finalChildren)
                        }
                        updatedBlocks.bindParents()

                        val updatedPage = page.copy(blocks = updatedBlocks)
                        workingPages = workingPages.map { if (it.id == updatedPage.id) updatedPage else it }.toMutableList()
                        affectedIds.add(updatedPage.id)
                        finalActiveViewId = updatedPage.id
                        stepSummaries.add("局部重构容器 [$targetContainerId] (${finalChildren.size} 个子组件)")
                    }

                    "PATCH" -> {
                        val page = targetPage ?: return EditLayoutResult(
                            isSuccess = false,
                            summary = "",
                            errorMessage = "第 ${index + 1} 项操作 (PATCH) 未找到目标视图页面: $rawViewName"
                        )

                        val patchList: List<JsonObject> = rawElements.mapNotNull { it as? JsonObject }

                        if (patchList.isEmpty()) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "第 ${index + 1} 项操作 (PATCH) 未包含有效的图元微调列表"
                            )
                        }

                        val validationError = validatePatchList(patchList, page, workingPages, assetLibrary)
                        if (validationError != null) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = "第 ${index + 1} 项操作 (PATCH) 数据审核失败: $validationError"
                            )
                        }

                        // 执行微调
                        val patchResult = patchViewElementsCore(page, patchList, assetMap, workingPages)
                        if (!patchResult.isSuccess || patchResult.updatedPage == null) {
                            return EditLayoutResult(
                                isSuccess = false,
                                summary = "",
                                errorMessage = patchResult.errorMessage ?: "局部微调执行失败"
                            )
                        }

                        val updatedPage = patchResult.updatedPage
                        val routedMap = patchResult.routedPages.associateBy { it.id }
                        workingPages = workingPages.map { p ->
                            when {
                                p.id == updatedPage.id -> updatedPage
                                routedMap.containsKey(p.id) -> routedMap.getValue(p.id)
                                else -> p
                            }
                        }.toMutableList()
                        affectedIds.add(updatedPage.id)
                        patchResult.routedPages.forEach { affectedIds.add(it.id) }
                        finalActiveViewId = updatedPage.id
                        stepSummaries.add("局部微调页面 [${page.id}] (${patchList.size} 项补丁)")
                    }

                    else -> {
                        return EditLayoutResult(
                            isSuccess = false,
                            summary = "",
                            errorMessage = "不支持的排版操作模式: '$mode' (有效模式: REPLACE_PAGE, REPLACE_CONTAINER, PATCH)"
                        )
                    }
                }
            }

            // 注意：独立页面引用（targetPageId != null）的子组件仅在目标独立页面中维护，
            // 严禁在此处调用 syncReferencedPageRecursively 将子页面 blocks 反向塞回宿主引用节点的 children，
            // 同时对本次未显式修改的节点保持原样无损。
            val fullySyncedPages = workingPages.toList()

            val finalSummary = "已成功执行 ${operations.size} 项视图排版操作: " + stepSummaries.joinToString("; ")
            AppLogger.i(TAG, finalSummary)

            EditLayoutResult(
                isSuccess = true,
                summary = finalSummary,
                updatedPages = fullySyncedPages,
                affectedViewIds = affectedIds.toList(),
                isPageReplacedOrCreated = isAnyPageReplacedOrCreated,
                activeViewId = finalActiveViewId
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行 edit_view_layout 异常", e)
            EditLayoutResult(
                isSuccess = false,
                summary = "",
                errorMessage = e.message ?: "未知排版解析异常"
            )
        }
    }

    /**
     * 将大模型输出的 JSON 参数解析并装配为真实的 UIStudioPage 页面实体
     */
    fun assembleViewPage(
        projectId: String,
        assetLibrary: UIAssetLibrary,
        args: JsonObject,
        availablePages: List<UIStudioPage> = emptyList()
    ): ApplyLayoutResult {
        return try {
            val rawName = args["viewName"]?.jsonPrimitive?.contentOrNull?.trim()
            // 约束 AI 创建页面名称必须为纯英文，若 AI 意外传入中文字符则自适应清洗过滤为合法英文标识
            val sanitizedName = rawName?.let { name ->
                val hasChinese = name.any { it in '\u4e00'..'\u9fa5' }
                if (hasChinese) {
                    val asciiOnly = name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
                    if (asciiOnly.isNotBlank()) asciiOnly else "View_${org.gemini.ui.forge.getCurrentTimeMillis() % 10000}"
                } else {
                    name
                }
            }
            val viewName = if (sanitizedName.isNullOrBlank()) "AutoGeneratedView" else sanitizedName
            val width = args["width"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 1280f
            val height = args["height"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 720f

            val rawBlocks = args["blocks"]?.jsonArray ?: JsonArray(emptyList())

            // ★ 核心安全防呆拦截：防冲垮非空已有视图
            val overrideConfirmed = args["overrideConfirmed"]?.jsonPrimitive?.booleanOrNull ?: false
            val existingPage = availablePages.find {
                it.id.equals(viewName, ignoreCase = true)
            }
            if (existingPage != null && existingPage.blocks.isNotEmpty() && !overrideConfirmed) {
                val existingCount = countAllBlocks(existingPage.blocks)
                return ApplyLayoutResult(
                    isSuccess = false,
                    viewName = viewName,
                    blockCount = 0,
                    matchedAssetCount = 0,
                    errorMessage = "【安全拦截】：视图 [$viewName] 已经存在且包含 $existingCount 个已有图元组件！直接调用 apply_view_layout 将彻底冲垮并抹除该页面的全部已有设计。若确实需要全量重构整页，必须首先通过 'ask_user_question' 弹出确认卡片征得用户授权，并在调用时显式传入 'overrideConfirmed = true'；若仅是新增或微调个别组件，请改用 'patch_view_elements' 执行局部精准增删改。"
                )
            }

            // ★ 前置刚性数据格式审核：严格拦截非法结构、非法图元、引用违规与未入库资产
            val validationError = validateViewLayoutData(rawBlocks, availablePages, assetLibrary)
            if (validationError != null) {
                return ApplyLayoutResult(
                    isSuccess = false,
                    viewName = viewName,
                    blockCount = 0,
                    matchedAssetCount = 0,
                    errorMessage = "AI 数据格式审核失败: $validationError"
                )
            }

            // 建立素材库文件名快速索引 (忽略大小写：覆盖显示名、物理文件名及内容哈希)
            val assetMap = mutableMapOf<String, UIAssetItem>()
            assetLibrary.items.forEach { asset ->
                assetMap[asset.name.lowercase()] = asset
                val cleanName = asset.name.substringBeforeLast('.').lowercase()
                assetMap[cleanName] = asset

                val realFileName = asset.relativePath.substringAfterLast('/').substringAfterLast('\\').lowercase()
                if (realFileName.isNotBlank()) {
                    assetMap[realFileName] = asset
                    assetMap[realFileName.substringBeforeLast('.')] = asset
                }
                asset.hash?.lowercase()?.takeIf { it.isNotBlank() }?.let { hash ->
                    assetMap[hash] = asset
                }
            }

            // 递归解析图元列表与插槽角色映射
            val assembledBlocks = mutableListOf<UIStudioBlock>()
            val slotRoleMap = mutableMapOf<String, String>()
            for (element in rawBlocks) {
                val blockObj = element as? JsonObject ?: continue
                parseBlockRecursive(blockObj, assetMap, slotRoleMap, assembledBlocks)
            }

            // 【阶段二：全自动素材智能装配流水线】
            // 若素材库非空，针对骨架中尚未绑定素材的插槽，按语义角色、网格顺序与纵横比智能填充切片
            val finalBlocks = if (assetLibrary.items.isNotEmpty()) {
                autoFitAssetsToSlots(assembledBlocks, slotRoleMap, assetLibrary)
            } else {
                assembledBlocks
            }

            // 构建 UIStudioPage 视图实体
            val initialPage = UIStudioPage(
                id = viewName.replace(" ", "_"),
                width = width,
                height = height,
                blocks = finalBlocks
            )

            // 绑定父级引用接线
            initialPage.blocks.bindParents()

            val totalBlocksCount = countAllBlocks(initialPage.blocks)
            val matchedAssetCount = countMatchedAssets(initialPage.blocks)
            AppLogger.i(TAG, "成功为工程 [$projectId] 装配视图 [$viewName]，包含 $totalBlocksCount 个图元 (匹配素材: $matchedAssetCount)")

            ApplyLayoutResult(
                isSuccess = true,
                viewName = viewName,
                blockCount = totalBlocksCount,
                matchedAssetCount = matchedAssetCount,
                createdPage = initialPage,
                summary = "成功装配视图 [$viewName] ($width x $height)，包含 $totalBlocksCount 个组件图元 (已精准拼装 $matchedAssetCount 个切片素材)"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行 apply_view_layout 装配异常", e)
            ApplyLayoutResult(
                isSuccess = false,
                viewName = "ErrorView",
                blockCount = 0,
                matchedAssetCount = 0,
                errorMessage = e.message ?: "未知排版解析异常"
            )
        }
    }

    /**
     * 递归解析单个图元对象（哈希引用模型：命中素材即绑定其唯一 assetHash）
     */
    private fun parseBlockRecursive(
        obj: JsonObject,
        assetMap: Map<String, UIAssetItem>,
        slotRoleMap: MutableMap<String, String>,
        outList: MutableList<UIStudioBlock>
    ) {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "block_${outList.size + 1}"
        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: "COMPONENT"
        val type = parseBlockType(typeStr)

        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 0f
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: 0f
        val w = obj["width"]?.jsonPrimitive?.floatOrNull ?: 100f
        val h = obj["height"]?.jsonPrimitive?.floatOrNull ?: 100f

        // 收集大模型可能显式指定的插槽语义角色
        obj["slotRole"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let {
            slotRoleMap[id] = it
        }

        // 匹配素材库切片（命中即持有其唯一哈希引用，优先匹配 assetHash，次之 assetFileName）
        val assetHash = obj["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
        val assetFileName = obj["assetFileName"]?.jsonPrimitive?.contentOrNull?.trim()
        val targetPageId = obj["targetPageId"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        var matchedAsset: UIAssetItem? = null
        if (!assetHash.isNullOrBlank()) {
            matchedAsset = assetMap[assetHash.lowercase()]
        }
        if (matchedAsset == null && !assetFileName.isNullOrBlank()) {
            matchedAsset = assetMap[assetFileName.lowercase()]
                ?: assetMap[assetFileName.substringBeforeLast('.').lowercase()]
        }

        // 若为页面引用组件，强制类型为 VIEW
        val finalType = if (targetPageId != null) UIBlockType.COMPONENT else type

        // 若绑定了切片素材，保证宽高严格守恒真实切片的纵横比（Aspect Ratio），并在目标槽位框 [x, y, w, h] 内等比内切居中（Contain Fit），彻底杜绝拉伸变形或撑破父容器底边
        val bounds = if (matchedAsset != null && matchedAsset.width > 0 && matchedAsset.height > 0) {
            val assetW = matchedAsset.width.toFloat()
            val assetH = matchedAsset.height.toFloat()
            val assetRatio = assetW / assetH
            if (w <= 0f || h <= 0f || (w == 100f && h == 100f)) {
                SerialRect(x, y, x + assetW, y + assetH)
            } else {
                val modelRatio = w / h
                if (kotlin.math.abs(modelRatio - assetRatio) > 0.05f) {
                    val scale = minOf(w / assetW, h / assetH)
                    val fitW = assetW * scale
                    val fitH = assetH * scale
                    val fitX = x + (w - fitW) / 2f
                    val fitY = y + (h - fitH) / 2f
                    SerialRect(fitX, fitY, fitX + fitW, fitY + fitH)
                } else {
                    SerialRect(x, y, x + w, y + h)
                }
            }
        } else {
            val finalW = if (w <= 0f) 100f else w
            val finalH = if (h <= 0f) 100f else h
            SerialRect(x, y, x + finalW, y + finalH)
        }

        // 解析嵌套子组件：若声明了 targetPageId，页面引用图元严禁内联 children
        val childBlocks = mutableListOf<UIStudioBlock>()
        if (targetPageId == null) {
            val rawChildren = obj["children"]?.jsonArray
            if (rawChildren != null && rawChildren.isNotEmpty()) {
                for (childElement in rawChildren) {
                    val childObj = childElement as? JsonObject ?: continue
                    parseBlockRecursive(childObj, assetMap, slotRoleMap, childBlocks)
                }
            }
        }

        val block = UIStudioBlock(
            id = id,
            type = finalType,
            bounds = bounds,
            assetHash = matchedAsset?.hash,
            children = childBlocks,
            targetPageId = targetPageId
        )
        outList.add(block)
    }

    /**
     * 【阶段二核心引擎】：将素材库中的视觉切片全自动智能装配到线框插槽中。
     *
     * 匹配策略：
     * 1. 语义分类池：背景底图、Spin核心按键、滚轴图案(Symbols)、常规控制按键、HUD/底栏、标题Logo；
     * 2. 插槽角色路由：根据 slotRole、type、id 及父级上下文智能推断；
     * 3. 滚轴符号轮播：按顺序轮播丰富图案，避免千篇一律；
     * 4. 纵横比守恒与居中：严格遵循素材物理尺寸与比例，解除纯容器遮盖。
     */
    private fun autoFitAssetsToSlots(
        blocks: List<UIStudioBlock>,
        slotRoleMap: Map<String, String>,
        assetLibrary: UIAssetLibrary
    ): List<UIStudioBlock> {
        // 哈希→素材反查索引（用于识别已被显式绑定的素材，避免重复分配）
        val assetByHash = assetLibrary.items.filter { it.hash != null }.associateBy { it.hash!! }

        // 收集已显式绑定的素材，避免重复分配
        val usedAssetNames = mutableSetOf<String>()
        fun collectBound(list: List<UIStudioBlock>) {
            for (b in list) {
                b.assetHash?.let { hash ->
                    assetByHash[hash]?.let { asset ->
                        usedAssetNames.add(asset.name.lowercase())
                    }
                }
                collectBound(b.children)
            }
        }
        collectBound(blocks)

        // 语义分类池
        val classified = assetLibrary.items.groupBy { AssetManifestHelper.categorizeAsset(it.name) }
        val backgrounds = classified[AssetSemanticCategory.BACKGROUND].orEmpty().sortedByDescending { it.width * it.height }
        val spinButtons = classified[AssetSemanticCategory.SPIN_BUTTON].orEmpty()
        val reelSymbols = classified[AssetSemanticCategory.REEL_SYMBOL].orEmpty()
        val generalButtons = classified[AssetSemanticCategory.BUTTON].orEmpty()
        val hudFrames = classified[AssetSemanticCategory.HUD_FRAME].orEmpty()
        val titleLogos = classified[AssetSemanticCategory.TITLE_LOGO].orEmpty()

        var symbolCursor = 0
        val usedButtons = mutableSetOf<String>()

        fun fitRecursive(blockList: List<UIStudioBlock>, parentId: String = ""): List<UIStudioBlock> {
            return blockList.map { block ->
                // 如果当前节点为独立页面引用实例，绝对禁止为其自动绑定切片或篡改容器尺寸
                if (!block.targetPageId.isNullOrBlank()) {
                    return@map block
                }
                val explicitRole = slotRoleMap[block.id]
                // 如果当前节点已有切片绑定，或属于无顯式插槽角色的 VIEW / LIST / TEXT / SPINE 容器，仅递归处理子组件，严禁强绑切片贴图
                if (block.assetHash != null || (explicitRole.isNullOrBlank() && (block.type == UIBlockType.COMPONENT || block.type == UIBlockType.LIST || block.type == UIBlockType.TEXT || block.type == UIBlockType.SPINE || block.children.isNotEmpty()))) {
                    return@map block.copy(children = fitRecursive(block.children, block.id))
                }

                val idLower = block.id.lowercase()
                val parentLower = parentId.lowercase()

                // 推断该插槽的最佳语义角色
                val targetCategory: AssetSemanticCategory? = when {
                    explicitRole.equals("BACKGROUND", true) || idLower.contains("bg") || idLower.contains("background") ->
                        AssetSemanticCategory.BACKGROUND
                    explicitRole.equals("SPIN_BUTTON", true) || idLower.contains("spin") ->
                        AssetSemanticCategory.SPIN_BUTTON
                    explicitRole.equals("REEL_SYMBOL", true) || idLower.contains("sym") || idLower.contains("icon") || idLower.contains("slot_") || parentLower.contains("col_") || parentLower.contains("reel_") ->
                        AssetSemanticCategory.REEL_SYMBOL
                    explicitRole.equals("BUTTON", true) || explicitRole.equals("BET_CONTROL", true) || block.type == UIBlockType.BUTTON || idLower.contains("btn") ->
                        AssetSemanticCategory.BUTTON
                    explicitRole.equals("HUD_FRAME", true) || idLower.contains("hud") || idLower.contains("balance") || idLower.contains("bar") || idLower.contains("jackpot") ->
                        AssetSemanticCategory.HUD_FRAME
                    explicitRole.equals("TITLE_LOGO", true) || idLower.contains("logo") || idLower.contains("title") ->
                        AssetSemanticCategory.TITLE_LOGO
                    else -> null
                }

                // 从对应分类池挑选切片
                val candidateAsset: UIAssetItem? = when (targetCategory) {
                    AssetSemanticCategory.BACKGROUND -> {
                        backgrounds.firstOrNull { it.name.lowercase() !in usedAssetNames } ?: backgrounds.firstOrNull()
                    }
                    AssetSemanticCategory.SPIN_BUTTON -> {
                        spinButtons.firstOrNull { it.name.lowercase() !in usedAssetNames }
                            ?: generalButtons.firstOrNull { it.name.lowercase().contains("spin") }
                            ?: spinButtons.firstOrNull()
                    }
                    AssetSemanticCategory.REEL_SYMBOL -> {
                        if (reelSymbols.isNotEmpty()) {
                            val pick = reelSymbols[symbolCursor % reelSymbols.size]
                            symbolCursor++
                            pick
                        } else null
                    }
                    AssetSemanticCategory.BUTTON -> {
                        val matched = generalButtons.firstOrNull { btn ->
                            val bName = btn.name.lowercase()
                            bName !in usedButtons && when {
                                idLower.contains("line") -> bName.contains("line")
                                idLower.contains("bet") -> bName.contains("bet")
                                idLower.contains("plus") || idLower.contains("add") -> bName.contains("plus") || bName.contains("add")
                                idLower.contains("minus") || idLower.contains("sub") -> bName.contains("minus") || bName.contains("sub")
                                idLower.contains("max") -> bName.contains("max")
                                idLower.contains("auto") -> bName.contains("auto")
                                else -> false
                            }
                        } ?: generalButtons.firstOrNull { it.name.lowercase() !in usedButtons }
                        matched?.also { usedButtons.add(it.name.lowercase()) }
                    }
                    AssetSemanticCategory.HUD_FRAME -> {
                        hudFrames.firstOrNull { it.name.lowercase() !in usedAssetNames } ?: hudFrames.firstOrNull()
                    }
                    AssetSemanticCategory.TITLE_LOGO -> {
                        titleLogos.firstOrNull { it.name.lowercase() !in usedAssetNames } ?: titleLogos.firstOrNull()
                    }
                    else -> null
                }

                // 执行物理装配（哈希引用绑定）
                if (candidateAsset != null) {
                    usedAssetNames.add(candidateAsset.name.lowercase())

                    // 保持素材原生纵横比，在目标槽位框内等比内切居中（Contain Fit），防止撑破父容器
                    val newBounds = if (candidateAsset.width > 0 && candidateAsset.height > 0) {
                        val assetW = candidateAsset.width.toFloat()
                        val assetH = candidateAsset.height.toFloat()
                        val assetRatio = assetW / assetH
                        val curX = block.bounds.left
                        val curY = block.bounds.top
                        val curW = block.bounds.width
                        val curH = block.bounds.height

                        if (curW <= 0f || curH <= 0f || (curW == 100f && curH == 100f)) {
                            SerialRect(curX, curY, curX + assetW, curY + assetH)
                        } else {
                            val curRatio = curW / curH
                            if (kotlin.math.abs(curRatio - assetRatio) > 0.05f) {
                                val scale = minOf(curW / assetW, curH / assetH)
                                val fitW = assetW * scale
                                val fitH = assetH * scale
                                val fitX = curX + (curW - fitW) / 2f
                                val fitY = curY + (curH - fitH) / 2f
                                SerialRect(fitX, fitY, fitX + fitW, fitY + fitH)
                            } else {
                                block.bounds
                            }
                        }
                    } else {
                        block.bounds
                    }

                    block.copy(
                        assetHash = candidateAsset.hash,
                        bounds = newBounds,
                        children = fitRecursive(block.children, block.id)
                    )
                } else {
                    block.copy(children = fitRecursive(block.children, block.id))
                }
            }
        }

        return fitRecursive(blocks)
    }

    /**
     * 容错解析图元类型枚举
     */
    private fun parseBlockType(typeStr: String): UIBlockType {
        val upper = typeStr.uppercase().trim()
        return try {
            UIBlockType.valueOf(upper)
        } catch (_: Exception) {
            when {
                upper == "LIST" || upper == "GLIST" || upper == "ROLL_LIST" || upper == "REEL" -> UIBlockType.LIST
                upper == "SPINE" || upper == "SKELETON" || upper == "MOVIE_CLIP" -> UIBlockType.SPINE
                upper == "LOADER" || upper == "GLOADER" -> UIBlockType.LOADER
                upper.contains("BG") || upper.contains("BACK") || upper == "ICON" || upper == "SYMBOL" -> UIBlockType.IMAGE
                upper.contains("BTN") || upper.contains("BUTTON") || upper.contains("SPIN") -> UIBlockType.BUTTON
                upper.contains("TEXT") || upper.contains("LABEL") -> UIBlockType.TEXT
                else -> UIBlockType.COMPONENT
            }
        }
    }

    /**
     * 局部补丁操作返回结果
     */
    data class PatchElementsResult(
        val isSuccess: Boolean,
        val updatedPage: UIStudioPage? = null,
        val patchedCount: Int = 0,
        val summary: String = "",
        val errorMessage: String? = null,
        val routedPages: List<UIStudioPage> = emptyList() // ★ 被自动路由修改的关联子页面列表（需原子刷盘）
    )

    /**
     * 前置刚性数据格式审核：验证 PATCH 模式 elements 操作列表
     */
    private fun validatePatchList(
        patchList: List<JsonObject>,
        targetPage: UIStudioPage,
        availablePages: List<UIStudioPage>,
        assetLibrary: UIAssetLibrary
    ): String? {
        val availablePageIds = availablePages.map { it.id }.toSet()
        val validAssetHashes = assetLibrary.items.mapNotNull { it.hash?.lowercase()?.trim() }.toSet()
        val existingBlockIds = mutableSetOf<String>()
        fun collectExistingIds(blocks: List<UIStudioBlock>) {
            for (b in blocks) {
                existingBlockIds.add(b.id)
                if (b.children.isNotEmpty()) collectExistingIds(b.children)
            }
        }
        collectExistingIds(targetPage.blocks)

        for ((index, patch) in patchList.withIndex()) {
            val step = "第 ${index + 1} 项补丁"
            val op = patch["op"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "UPDATE"
            if (op !in listOf("UPDATE", "ADD", "REMOVE", "DELETE")) {
                return "$step: 缺少或包含非法的操作动词 'op' (当前值: '${patch["op"]?.jsonPrimitive?.contentOrNull}')，仅支持 UPDATE, ADD, REMOVE"
            }

            val id = patch["id"]?.jsonPrimitive?.contentOrNull
            when (op) {
                "UPDATE" -> {
                    if (id.isNullOrBlank()) {
                        return "$step: UPDATE 操作必须指定非空的 'id'"
                    }
                    if (id !in existingBlockIds) {
                        return "$step: UPDATE 目标图元 '$id' 在当前页面 [${targetPage.id}] 中不存在"
                    }
                    patch["width"]?.jsonPrimitive?.floatOrNull?.let { w ->
                        if (w <= 0f) return "$step: 图元 '$id' 修改后的 width 必须大于 0 (当前值: $w)"
                    }
                    patch["height"]?.jsonPrimitive?.floatOrNull?.let { h ->
                        if (h <= 0f) return "$step: 图元 '$id' 修改后的 height 必须大于 0 (当前值: $h)"
                    }
                    val targetPageId = patch["targetPageId"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (!targetPageId.isNullOrBlank() && availablePageIds.isNotEmpty() && targetPageId !in availablePageIds) {
                        return "$step: 图元 '$id' 指定的引用目标页面 '$targetPageId' 不存在"
                    }
                    val children = patch["children"]?.jsonArray
                    val existingBlock = findBlockRecursively(targetPage.blocks, id)
                    val effectiveTargetPageId = targetPageId ?: existingBlock?.targetPageId
                    if (!effectiveTargetPageId.isNullOrBlank()) {
                        if (children != null && children.isNotEmpty()) {
                            return "$step: 图元 '$id' 为页面引用实例 (targetPageId='$effectiveTargetPageId')，架构红线严禁在 children 中内联塞入子图元！其子组件必须在独立页面内维护"
                        }
                    }
                    val typeStr = patch["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim()
                    if (typeStr == "CONTAINER") {
                        return "$step: 类型 'CONTAINER' 已彻底废弃，请使用 'VIEW' 或 'LIST'"
                    }
                    val reqAssetHash = patch["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (!reqAssetHash.isNullOrBlank() && validAssetHashes.isNotEmpty() && reqAssetHash.lowercase() !in validAssetHashes) {
                        return "$step: 图元 '$id' 指定的 assetHash '$reqAssetHash' 在当前工程素材库中不存在！严禁伪造或预填不存在的 Hash。若为新生成的图片，必须先通过 'ask_user_question' 征得用户同意，并调用 'save_image_to_asset_library' 落盘入库获取真实 assetHash 后方可装配。"
                    }
                }
                "REMOVE", "DELETE" -> {
                    if (id.isNullOrBlank()) {
                        return "$step: REMOVE 操作必须指定非空的 'id'"
                    }
                    if (id !in existingBlockIds) {
                        return "$step: REMOVE 目标图元 '$id' 在当前页面 [${targetPage.id}] 中不存在"
                    }
                }
                "ADD" -> {
                    if (id.isNullOrBlank()) {
                        return "$step: ADD 新图元必须指定非空的 'id'"
                    }
                    patch["width"]?.jsonPrimitive?.floatOrNull?.let { w ->
                        if (w <= 0f) return "$step: 新图元 '$id' 的 width 必须大于 0 (当前值: $w)"
                    }
                    patch["height"]?.jsonPrimitive?.floatOrNull?.let { h ->
                        if (h <= 0f) return "$step: 新图元 '$id' 的 height 必须大于 0 (当前值: $h)"
                    }
                    val newType = patch["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim()
                    if (newType == "CONTAINER") {
                        return "$step: 类型 'CONTAINER' 已彻底废弃，请使用 'VIEW' 或 'LIST'"
                    }
                    val parentBlockId = patch["parentBlockId"]?.jsonPrimitive?.contentOrNull
                    if (!parentBlockId.isNullOrBlank() && parentBlockId !in existingBlockIds) {
                        return "$step: ADD 指定的父容器 parentBlockId '$parentBlockId' 在当前页面中不存在"
                    }
                    val newTargetPageId = patch["targetPageId"]?.jsonPrimitive?.contentOrNull?.trim()
                    val children = patch["children"]?.jsonArray
                    if (!newTargetPageId.isNullOrBlank() && children != null && children.isNotEmpty()) {
                        return "$step: 新图元 '$id' 为页面引用实例 (targetPageId='$newTargetPageId')，严禁在 children 中内联塞入子图元"
                    }
                    val newAssetHash = patch["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (!newAssetHash.isNullOrBlank() && validAssetHashes.isNotEmpty() && newAssetHash.lowercase() !in validAssetHashes) {
                        return "$step: 新图元 '$id' 指定的 assetHash '$newAssetHash' 在当前工程素材库中不存在！严禁伪造或预填不存在的 Hash。若为新生成的图片，必须先通过 'ask_user_question' 征得用户同意，并调用 'save_image_to_asset_library' 落盘入库获取真实 assetHash 后方可装配。"
                    }
                }
            }
        }
        return null
    }

    /**
     * 前置刚性数据格式审核：验证 apply_view_layout 全量排版 blocks
     */
    private fun validateViewLayoutData(
        rawBlocks: JsonArray,
        availablePages: List<UIStudioPage>,
        assetLibrary: UIAssetLibrary
    ): String? {
        if (rawBlocks.isEmpty()) {
            return "全量排版数据中 blocks 数组不能为空"
        }
        val availablePageIds = availablePages.map { it.id }.toSet()
        val visitedIds = mutableSetOf<String>()
        val validAssetHashes = assetLibrary.items.mapNotNull { it.hash?.lowercase() }.toSet()

        fun validateBlockNode(obj: JsonObject, path: String): String? {
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()
            if (id.isNullOrBlank()) {
                return "$path: 图元缺少必填的 'id' 字段"
            }
            if (id in visitedIds) {
                return "$path: 图元 ID '$id' 重复，同一视图内图元 ID 必须唯一"
            }
            visitedIds.add(id)

            val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim()
            if (typeStr == "CONTAINER") {
                return "$path (图元 '$id'): 类型 'CONTAINER' 已彻底废弃，请使用 'VIEW' 或 'LIST'"
            }

            obj["width"]?.jsonPrimitive?.floatOrNull?.let { w ->
                if (w <= 0f) return "$path (图元 '$id'): width 必须大于 0 (当前值: $w)"
            }
            obj["height"]?.jsonPrimitive?.floatOrNull?.let { h ->
                if (h <= 0f) return "$path (图元 '$id'): height 必须大于 0 (当前值: $h)"
            }

            val targetPageId = obj["targetPageId"]?.jsonPrimitive?.contentOrNull?.trim()
            val children = obj["children"]?.jsonArray
            if (!targetPageId.isNullOrBlank()) {
                if (availablePageIds.isNotEmpty() && targetPageId !in availablePageIds) {
                    return "$path (图元 '$id'): 指定的引用页面 targetPageId '$targetPageId' 不存在"
                }
                if (children != null && children.isNotEmpty()) {
                    return "$path (图元 '$id'): 是页面引用实例 (targetPageId='$targetPageId')，严禁在 children 中内联塞入子图元！组件内容必须在其独立子页面中维护"
                }
            }

            val reqAssetHash = obj["assetHash"]?.jsonPrimitive?.contentOrNull?.trim()
            if (!reqAssetHash.isNullOrBlank() && validAssetHashes.isNotEmpty() && reqAssetHash.lowercase() !in validAssetHashes) {
                return "$path (图元 '$id'): 指定的 assetHash '$reqAssetHash' 在当前工程素材库中不存在！严禁伪造或预填不存在的 Hash。若为新生成的图片，必须先通过 'ask_user_question' 征得用户同意，并调用 'save_image_to_asset_library' 落盘入库获取真实 assetHash 后方可装配。"
            }

            if (children != null) {
                for ((cIdx, child) in children.withIndex()) {
                    val childObj = child as? JsonObject
                        ?: return "$path (图元 '$id') -> children[$cIdx]: 格式错误，必须为 JSON 对象"
                    val childErr = validateBlockNode(childObj, "$path -> $id -> children[$cIdx]")
                    if (childErr != null) return childErr
                }
            }
            return null
        }

        for ((idx, item) in rawBlocks.withIndex()) {
            val blockObj = item as? JsonObject
                ?: return "blocks[$idx]: 格式错误，必须为 JSON 对象"
            val err = validateBlockNode(blockObj, "blocks[$idx]")
            if (err != null) return err
        }
        return null
    }

    private fun findBlockRecursively(blocks: List<UIStudioBlock>, targetId: String): UIStudioBlock? {
        for (b in blocks) {
            if (b.id == targetId) return b
            if (b.children.isNotEmpty()) {
                val found = findBlockRecursively(b.children, targetId)
                if (found != null) return found
            }
        }
        return null
    }

    private fun updateBlockRecursively(
        blocks: List<UIStudioBlock>,
        targetId: String,
        transform: (UIStudioBlock) -> UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else if (b.children.isNotEmpty()) {
                b.copy(children = updateBlockRecursively(b.children, targetId, transform))
            } else {
                b
            }
        }
    }

    private fun removeBlockRecursively(
        blocks: List<UIStudioBlock>,
        targetId: String
    ): List<UIStudioBlock> {
        return blocks.filter { it.id != targetId }.map { b ->
            if (b.children.isNotEmpty()) {
                b.copy(children = removeBlockRecursively(b.children, targetId))
            } else {
                b
            }
        }
    }

    /**
     * 跨页面多重引用递归同步
     */
    fun syncReferencedPageRecursively(
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
                updatedBlock.copy(children = syncReferencedPageRecursively(updatedBlock.children, targetPageId, syncedChildren))
            } else {
                updatedBlock
            }
        }.bindParents()
    }

    /**
     * 局部图元微调核心执行器
     */
    private fun patchViewElementsCore(
        targetPage: UIStudioPage,
        patchList: List<JsonObject>,
        assetMap: Map<String, UIAssetItem>,
        availablePages: List<UIStudioPage>
    ): PatchElementsResult {
        var currentBlocks = targetPage.blocks
        var appliedCount = 0
        val routedPagesMap = mutableMapOf<String, UIStudioPage>()

        for (patch in patchList) {
            val op = patch["op"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "UPDATE"
            val id = patch["id"]?.jsonPrimitive?.contentOrNull

            when (op) {
                "UPDATE" -> {
                    if (id.isNullOrBlank()) continue
                    currentBlocks = updateBlockRecursively(currentBlocks, id) { existing ->
                        var b = existing.bounds
                        var x = b.left
                        var y = b.top
                        var w = b.width
                        var h = b.height

                        patch["x"]?.jsonPrimitive?.floatOrNull?.let { x = it }
                        patch["y"]?.jsonPrimitive?.floatOrNull?.let { y = it }
                        patch["width"]?.jsonPrimitive?.floatOrNull?.let { w = it }
                        patch["height"]?.jsonPrimitive?.floatOrNull?.let { h = it }

                        var assetHash = existing.assetHash
                        val fileName = patch["assetFileName"]?.jsonPrimitive?.contentOrNull
                        if (!fileName.isNullOrBlank()) {
                            val matched = assetMap[fileName.lowercase()]
                            if (matched?.hash != null) {
                                assetHash = matched.hash
                                if (patch["width"] == null && matched.width > 0) w = matched.width.toFloat()
                                if (patch["height"] == null && matched.height > 0) h = matched.height.toFloat()
                            }
                        }
                        patch["assetHash"]?.jsonPrimitive?.contentOrNull?.let { assetHash = it }

                        var type = existing.type
                        patch["type"]?.jsonPrimitive?.contentOrNull?.let {
                            type = parseBlockType(it)
                        }

                        var targetPageId = existing.targetPageId
                        if (patch.containsKey("targetPageId")) {
                            targetPageId = patch["targetPageId"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                        }

                        // 外部显式处理 children 规则：
                        // 1. 若未在 patch 中触碰 children 且未新设 targetPageId，则保持 existing.children 原样不变；
                        // 2. 若外部在 patch 中显式传入了 "children"（如 children: [] 清理冗余子图元）或显式设置了 "targetPageId"，则彻底清空或更新 children，不再保留历史冗余子图元。
                        var updatedChildren = existing.children
                        if (patch.containsKey("children")) {
                            val rawChildArr = patch["children"]?.jsonArray
                            if (rawChildArr == null || rawChildArr.isEmpty() || targetPageId != null) {
                                updatedChildren = emptyList()
                            } else {
                                val parsedChildren = mutableListOf<UIStudioBlock>()
                                for (childEl in rawChildArr) {
                                    val childObj = childEl as? JsonObject ?: continue
                                    parseBlockRecursive(childObj, assetMap, mutableMapOf(), parsedChildren)
                                }
                                updatedChildren = parsedChildren
                            }
                        } else if (patch.containsKey("targetPageId") && targetPageId != null) {
                            updatedChildren = emptyList()
                        }

                        if (targetPageId != null) {
                            type = UIBlockType.COMPONENT
                            if (!patch.containsKey("assetHash") && fileName.isNullOrBlank()) {
                                assetHash = null
                            }
                        } else if (patch.containsKey("assetHash") && patch["assetHash"] is JsonNull) {
                            assetHash = null
                        }

                        existing.copy(
                            type = type,
                            bounds = SerialRect(x, y, x + w, y + h),
                            assetHash = assetHash,
                            targetPageId = targetPageId,
                            children = updatedChildren
                        )
                    }
                    appliedCount++
                }
                "REMOVE", "DELETE" -> {
                    if (id.isNullOrBlank()) continue
                    currentBlocks = removeBlockRecursively(currentBlocks, id)
                    appliedCount++
                }
                "ADD" -> {
                    val tempOut = mutableListOf<UIStudioBlock>()
                    parseBlockRecursive(patch, assetMap, mutableMapOf(), tempOut)
                    val created = tempOut.firstOrNull()
                    if (created != null) {
                        val parentBlockId = patch["parentBlockId"]?.jsonPrimitive?.contentOrNull
                        if (!parentBlockId.isNullOrBlank()) {
                            val parentBlock = findBlockRecursively(currentBlocks, parentBlockId)
                            if (parentBlock != null && parentBlock.targetPageId != null) {
                                val targetSubPageId = parentBlock.targetPageId
                                val currentSubPage = routedPagesMap[targetSubPageId]
                                    ?: availablePages.find { it.id == targetSubPageId }
                                if (currentSubPage != null) {
                                    val updatedSubBlocks = (currentSubPage.blocks + created).bindParents()
                                    val updatedSubPage = currentSubPage.copy(blocks = updatedSubBlocks)
                                    routedPagesMap[targetSubPageId] = updatedSubPage
                                    // 仅路由至目标独立子页面，严禁同时往宿主引用节点 parent.children 塞入副本
                                    appliedCount++
                                } else {
                                    return PatchElementsResult(
                                        isSuccess = false,
                                        errorMessage = "智能路由失败：未在工程中找到引用组件 [${parentBlock.id}] 绑定的目标页面 [$targetSubPageId]"
                                    )
                                }
                            } else {
                                currentBlocks = updateBlockRecursively(currentBlocks, parentBlockId) { parent ->
                                    parent.copy(children = parent.children + created)
                                }
                                appliedCount++
                            }
                        } else {
                            currentBlocks = currentBlocks + created
                            appliedCount++
                        }
                    }
                }
            }
        }

        val updatedPage = targetPage.copy(blocks = currentBlocks.bindParents())
        val routedList = routedPagesMap.values.toList()
        return PatchElementsResult(
            isSuccess = true,
            updatedPage = updatedPage,
            patchedCount = appliedCount,
            summary = "成功在页面 [${targetPage.id}] 执行 $appliedCount 项局部属性补丁更新" +
                    if (routedList.isNotEmpty()) " (已自动路由并同步更新 ${routedList.size} 个关联组件页面)" else "",
            routedPages = routedList
        )
    }

    /**
     * 递归统计整棵树的图元总数
     */
    fun countAllBlocks(blocks: List<UIStudioBlock>): Int {
        var count = blocks.size
        for (b in blocks) {
            if (b.children.isNotEmpty()) {
                count += countAllBlocks(b.children)
            }
        }
        return count
    }

    /**
     * 递归统计已成功绑定切片素材的图元总数
     */
    fun countMatchedAssets(blocks: List<UIStudioBlock>): Int {
        var count = 0
        for (b in blocks) {
            if (b.assetHash != null) {
                count++
            }
            if (b.children.isNotEmpty()) {
                count += countMatchedAssets(b.children)
            }
        }
        return count
    }

    // ==================== 公用图元树原子增删改移函数（多环境统一入口） ====================

    private suspend fun resolveTargetProject(
        projectName: String,
        repository: TemplateRepository
    ): Pair<String, org.gemini.ui.forge.state.ui.ProjectState>? {
        val sanitized = repository.sanitizeName(projectName)
        val activeVm = org.gemini.ui.forge.service.mcp.McpUiActionPipeline.getActiveUiStudioViewModel()
        val activeProj = activeVm?.state?.project
        if (activeProj != null && repository.sanitizeName(activeProj.projectId).equals(sanitized, ignoreCase = true)) {
            return sanitized to activeProj
        }
        return repository.findTemplatePair(projectName)
    }

    private suspend fun commitUpdatedPageAndSync(
        sanitizedName: String,
        project: org.gemini.ui.forge.state.ui.ProjectState,
        pageIndex: Int,
        updatedPage: UIStudioPage,
        repository: TemplateRepository,
        recordUndoLabel: String?,
        syncActiveWorkspace: Boolean
    ): org.gemini.ui.forge.state.ui.ProjectState {
        val activeVm = org.gemini.ui.forge.service.mcp.McpUiActionPipeline.getActiveUiStudioViewModel()
        if (recordUndoLabel != null && activeVm != null && repository.sanitizeName(activeVm.state.project?.projectId ?: "").equals(sanitizedName, ignoreCase = true)) {
            activeVm.canvasEditor.saveSnapshot(recordUndoLabel)
        }

        val workingPages = project.studioPages.toMutableList().apply {
            set(pageIndex, updatedPage)
        }.toList()

        // 注意：独立子页面更新时，严禁通过 syncReferencedPageRecursively 将 blocks 反向塞入宿主页面引用节点的 children 中
        val updatedProject = project.copy(studioPages = workingPages)
        repository.saveTemplate(sanitizedName, updatedProject)

        if (syncActiveWorkspace && activeVm != null && repository.sanitizeName(activeVm.state.project?.projectId ?: "").equals(sanitizedName, ignoreCase = true)) {
            activeVm.reload(updatedProject)
        }
        return updatedProject
    }

    /**
     * 公用函数：向指定视图（或父容器）添加新图元，自动执行跨页面引用同步与持久化。
     */
    suspend fun addStudioBlock(
        projectName: String,
        viewName: String? = null,
        parentId: String? = null,
        block: UIStudioBlock,
        insertIndex: Int? = null,
        repository: TemplateRepository = TemplateRepository(),
        recordUndo: Boolean = true,
        syncActiveWorkspace: Boolean = true
    ): Result<Pair<String, UIStudioBlock>> {
        val match = resolveTargetProject(projectName, repository)
            ?: return Result.failure(IllegalArgumentException("未找到工程 '$projectName'"))
        val sanitizedName = match.first
        val state = match.second

        val targetPageIndex = if (!viewName.isNullOrBlank()) {
            state.studioPages.indexOfFirst { it.id.equals(viewName, ignoreCase = true) }
        } else 0
        if (targetPageIndex < 0 || targetPageIndex >= state.studioPages.size) {
            return Result.failure(IllegalArgumentException("在工程 '$projectName' 中未找到目标视图 '${viewName ?: "MainPage"}'"))
        }
        val page = state.studioPages[targetPageIndex]

        if (findBlockRecursively(page.blocks, block.id) != null) {
            return Result.failure(IllegalArgumentException("图元 id '${block.id}' 在视图 '${page.id}' 中已存在，请指定不同的 id"))
        }

        if (!parentId.isNullOrBlank() && findBlockRecursively(page.blocks, parentId) == null) {
            return Result.failure(IllegalArgumentException("在视图 '${page.id}' 中未找到指定的父容器 id '$parentId'"))
        }

        fun insertRecursively(blocks: List<UIStudioBlock>): List<UIStudioBlock> {
            if (parentId.isNullOrBlank()) {
                val list = blocks.toMutableList()
                val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                list.add(idx, block)
                return list
            }
            return blocks.map { b ->
                if (b.id == parentId) {
                    val list = b.children.toMutableList()
                    val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                    list.add(idx, block)
                    b.copy(children = list)
                } else if (b.children.isNotEmpty()) {
                    b.copy(children = insertRecursively(b.children))
                } else b
            }
        }

        val updatedPage = page.copy(blocks = insertRecursively(page.blocks).bindParents())
        commitUpdatedPageAndSync(
            sanitizedName = sanitizedName,
            project = state,
            pageIndex = targetPageIndex,
            updatedPage = updatedPage,
            repository = repository,
            recordUndoLabel = if (recordUndo) "添加图元: ${block.id}" else null,
            syncActiveWorkspace = syncActiveWorkspace
        )
        return Result.success(page.id to block)
    }

    /**
     * 公用函数：更新指定图元属性（支持同时变更父容器 parentId 并自动保持代数守恒局部坐标换算），自动跨页面引用同步与持久化。
     */
    suspend fun updateStudioBlock(
        projectName: String,
        viewName: String? = null,
        id: String,
        parentId: String? = null,
        hasExplicitBounds: Boolean = false,
        repository: TemplateRepository = TemplateRepository(),
        recordUndo: Boolean = true,
        syncActiveWorkspace: Boolean = true,
        updater: (UIStudioBlock) -> UIStudioBlock
    ): Result<Pair<String, UIStudioBlock>> {
        val match = resolveTargetProject(projectName, repository)
            ?: return Result.failure(IllegalArgumentException("未找到工程 '$projectName'"))
        val sanitizedName = match.first
        val state = match.second

        val boundPages = state.studioPages.map { it.copy(blocks = it.blocks.bindParents()) }
        var foundBlock: UIStudioBlock? = null
        var targetPageIndex = -1

        for ((idx, page) in boundPages.withIndex()) {
            if (!viewName.isNullOrBlank() && !page.id.equals(viewName, ignoreCase = true)) continue
            val b = findBlockRecursively(page.blocks, id)
            if (b != null) {
                foundBlock = b
                targetPageIndex = idx
                break
            }
        }
        if (foundBlock == null) {
            for ((idx, page) in boundPages.withIndex()) {
                val b = findBlockRecursively(page.blocks, id)
                if (b != null) {
                    foundBlock = b
                    targetPageIndex = idx
                    break
                }
            }
        }
        val targetBlock = foundBlock
            ?: return Result.failure(IllegalArgumentException("在工程 '$projectName' 中未找到 id 为 '$id' 的 UI Studio 图元"))
        val originalPage = boundPages[targetPageIndex]
        val originalAbsBounds = targetBlock.absoluteBounds
        val updatedBlock = updater(targetBlock)

        val finalBlocks = if (parentId != null) {
            val isMovingToRoot = parentId.isBlank() || parentId.equals("null", ignoreCase = true)
            val stripped = removeBlockRecursively(originalPage.blocks, id).bindParents()
            if (isMovingToRoot) {
                val newBounds = if (hasExplicitBounds) updatedBlock.bounds else originalAbsBounds
                (stripped + updatedBlock.copy(bounds = newBounds, parent = null)).bindParents()
            } else {
                if (parentId == id) {
                    return Result.failure(IllegalArgumentException("不能将图元 '$id' 移动为其自身的子图元"))
                }
                val targetParent = findBlockRecursively(stripped, parentId)
                    ?: return Result.failure(IllegalArgumentException("在页面中未找到目标父容器 id '$parentId'"))
                val newLocalBounds = if (hasExplicitBounds) updatedBlock.bounds else targetParent.toLocalBounds(originalAbsBounds)
                val blockInParent = updatedBlock.copy(bounds = newLocalBounds)
                updateBlockRecursively(stripped, parentId) { p -> p.copy(children = p.children + blockInParent) }.bindParents()
            }
        } else {
            updateBlockRecursively(originalPage.blocks, id) { updatedBlock }.bindParents()
        }

        val updatedPage = originalPage.copy(blocks = finalBlocks)
        commitUpdatedPageAndSync(
            sanitizedName = sanitizedName,
            project = state.copy(studioPages = boundPages),
            pageIndex = targetPageIndex,
            updatedPage = updatedPage,
            repository = repository,
            recordUndoLabel = if (recordUndo) "更新图元: $id" else null,
            syncActiveWorkspace = syncActiveWorkspace
        )
        return Result.success(originalPage.id to updatedBlock)
    }

    /**
     * 公用函数：移动或重排序图元层级（内置父子局部坐标代数守恒换算，保持屏幕绝对位置不变）。
     */
    suspend fun moveStudioBlock(
        projectName: String,
        viewName: String? = null,
        id: String,
        newParentId: String? = null,
        insertIndex: Int? = null,
        preserveAbsolutePosition: Boolean = true,
        repository: TemplateRepository = TemplateRepository(),
        recordUndo: Boolean = true,
        syncActiveWorkspace: Boolean = true
    ): Result<String> {
        val match = resolveTargetProject(projectName, repository)
            ?: return Result.failure(IllegalArgumentException("未找到工程 '$projectName'"))
        val sanitizedName = match.first
        val state = match.second

        val boundPages = state.studioPages.map { it.copy(blocks = it.blocks.bindParents()) }
        var foundBlock: UIStudioBlock? = null
        var targetPageIndex = -1

        for ((idx, p) in boundPages.withIndex()) {
            if (!viewName.isNullOrBlank() && !p.id.equals(viewName, ignoreCase = true)) continue
            val b = findBlockRecursively(p.blocks, id)
            if (b != null) {
                foundBlock = b
                targetPageIndex = idx
                break
            }
        }
        val extracted = foundBlock
            ?: return Result.failure(IllegalArgumentException("在工程 '$projectName' 中未找到 id 为 '$id' 的图元"))
        val page = boundPages[targetPageIndex]
        val originalAbsBounds = extracted.absoluteBounds

        val cleanParentId = newParentId?.trim()?.ifBlank { null }
            ?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("root", ignoreCase = true) }

        if (cleanParentId == id) {
            return Result.failure(IllegalArgumentException("不能将图元 '$id' 移动为其自身的子图元"))
        }

        val blocksWithoutTarget = removeBlockRecursively(page.blocks, id).bindParents()
        val relocatedBlock = if (cleanParentId == null) {
            val newBounds = if (preserveAbsolutePosition) originalAbsBounds else extracted.bounds
            extracted.copy(bounds = newBounds, parent = null)
        } else {
            val targetParent = findBlockRecursively(blocksWithoutTarget, cleanParentId)
                ?: return Result.failure(IllegalArgumentException("在视图 '${page.id}' 中未找到目标父容器 id '$cleanParentId'"))
            val newLocal = if (preserveAbsolutePosition) targetParent.toLocalBounds(originalAbsBounds) else extracted.bounds
            extracted.copy(bounds = newLocal)
        }

        fun insertIntoTree(blocks: List<UIStudioBlock>): List<UIStudioBlock> {
            if (cleanParentId == null) {
                val list = blocks.toMutableList()
                val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                list.add(idx, relocatedBlock)
                return list
            }
            return blocks.map { b ->
                if (b.id == cleanParentId) {
                    val list = b.children.toMutableList()
                    val idx = (insertIndex ?: list.size).coerceIn(0, list.size)
                    list.add(idx, relocatedBlock)
                    b.copy(children = list)
                } else if (b.children.isNotEmpty()) {
                    b.copy(children = insertIntoTree(b.children))
                } else b
            }
        }

        val updatedPage = page.copy(blocks = insertIntoTree(blocksWithoutTarget).bindParents())
        commitUpdatedPageAndSync(
            sanitizedName = sanitizedName,
            project = state.copy(studioPages = boundPages),
            pageIndex = targetPageIndex,
            updatedPage = updatedPage,
            repository = repository,
            recordUndoLabel = if (recordUndo) "移动图元: $id" else null,
            syncActiveWorkspace = syncActiveWorkspace
        )
        return Result.success(page.id)
    }

    /**
     * 公用函数：从视图中删除指定图元及其子节点，自动跨页面引用同步与持久化。
     */
    suspend fun deleteStudioBlock(
        projectName: String,
        viewName: String? = null,
        id: String,
        repository: TemplateRepository = TemplateRepository(),
        recordUndo: Boolean = true,
        syncActiveWorkspace: Boolean = true
    ): Result<String> {
        val match = resolveTargetProject(projectName, repository)
            ?: return Result.failure(IllegalArgumentException("未找到工程 '$projectName'"))
        val sanitizedName = match.first
        val state = match.second

        var targetPageIndex = -1
        for ((idx, page) in state.studioPages.withIndex()) {
            if (!viewName.isNullOrBlank() && !page.id.equals(viewName, ignoreCase = true)) continue
            if (findBlockRecursively(page.blocks, id) != null) {
                targetPageIndex = idx
                break
            }
        }
        if (targetPageIndex < 0) {
            return Result.failure(IllegalArgumentException("在工程 '$projectName' 中未找到 id 为 '$id' 的图元"))
        }

        val page = state.studioPages[targetPageIndex]
        val updatedBlocks = removeBlockRecursively(page.blocks, id).bindParents()
        val updatedPage = page.copy(blocks = updatedBlocks)

        commitUpdatedPageAndSync(
            sanitizedName = sanitizedName,
            project = state,
            pageIndex = targetPageIndex,
            updatedPage = updatedPage,
            repository = repository,
            recordUndoLabel = if (recordUndo) "删除图元: $id" else null,
            syncActiveWorkspace = syncActiveWorkspace
        )
        return Result.success(page.id)
    }
}
