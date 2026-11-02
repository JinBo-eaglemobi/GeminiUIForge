package org.gemini.ui.forge.service.tool.shared

import kotlinx.serialization.json.*
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.service.TemplateOverlayRenderer
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline
import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.ui.uistudio.model.UIAssetLibrary
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.bindParents
import org.gemini.ui.forge.utils.findBlockById
import org.gemini.ui.forge.utils.looseJson

/**
 * 更新图元属性与元数据工具 (UpdateBlockTool)
 *
 * 支持针对传统 Slots 工程与 UI Studio 独立工程双轨更新图元属性。
 * 支持修改坐标与尺寸(bounds)、中文/英文生图提示词(userPromptZh/userPromptEn)、组件类型(type)、
 * 纯容器标记(isPureContainer)、素材切片哈希绑定(assetHash/assetFileName)、层级树移动(parentId)、
 * 显隐状态(isVisible)与透明度(opacity)，并自动触发工作区内存刷新与磁盘持久化。
 */
class UpdateBlockTool(
    private val repository: TemplateRepository = TemplateRepository(),
    private val fileStorage: LocalFileStorage = LocalFileStorage()
) : ToolDefinition {

    override val name: String = "update_block"

    override val description: String =
        "更新指定模板中某个图元的属性配置。支持修改坐标与尺寸(bounds)、提示词(userPromptZh/userPromptEn)、组件类型(type)、切片哈希(assetHash/assetFileName)、层级移入移出(parentId)、显隐(isVisible)与透明度(opacity)。"

    override val annotations: ToolAnnotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("projectName", buildJsonObject {
                put("type", "string")
                put("description", "模板工程名称")
            })
            put("blockId", buildJsonObject {
                put("type", "string")
                put("description", "目标图元的唯一标识 ID")
            })
            put("name", buildJsonObject {
                put("type", "string")
                put("description", "可选：图元语义化显示名称")
            })
            put("type", buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add("CONTAINER")
                    add("BUTTON")
                    add("IMAGE")
                    add("TEXT")
                    add("VIEW")
                    add("INPUT")
                    add("REEL")
                    add("SPIN_BUTTON")
                    add("WIN_DISPLAY")
                    add("BACKGROUND")
                    add("SYMBOL")
                    add("HEADER")
                    add("FOOTER")
                    add("PROGRESS_BAR")
                    add("POPUP_MENU")
                    add("LOADER")
                    add("SCROLL_BAR")
                    add("SLIDER")
                    add("COMBO_BOX")
                }
                put(
                    "description",
                    "可选：图元组件类型。常用推荐：'CONTAINER'（纯容器/组合层，仅用于排版）、'BUTTON'（可交互按键）、'IMAGE'（独立切片/贴图）、'TEXT'（文本框）。亦支持专用类型如 'VIEW', 'INPUT', 'REEL', 'SPIN_BUTTON', 'WIN_DISPLAY', 'BACKGROUND', 'SYMBOL', 'HEADER', 'FOOTER', 'PROGRESS_BAR' 等。"
                )
            })
            put("assetHash", buildJsonObject {
                put("type", "string")
                put("description", "可选：UI Studio 切片 SHA-256 哈希值，用于图元贴图绑定。传入空字符串 \"\" 则表示解绑切片贴图。")
            })
            put("assetFileName", buildJsonObject {
                put("type", "string")
                put("description", "可选：素材库切片文件名（如 'icon_gem.png' 或 'btn_spin'）。若为 UI Studio 工程，自动在素材库中匹配切片并提取其哈希进行绑定。与 assetHash 同时提供时以 assetHash 优先。")
            })
            put("parentId", buildJsonObject {
                put("type", "string")
                put("description", "可选：新父容器图元 ID。若要将该图元移入某个父容器，传入该父容器的 blockId；若要移到页面根层级，传入 \"\" 或 \"null\"。层级调整时自动执行代数守恒逆向局部换算，保持视口绝对物理坐标不变。")
            })
            put("isVisible", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：图层在画布与预览中是否可见（显隐控制）")
            })
            put("opacity", buildJsonObject {
                put("type", "number")
                put("description", "可选：独立不透明度，取值范围 0.0 ~ 1.0（超出自动物理 clamp）")
            })
            put("isPureContainer", buildJsonObject {
                put("type", "boolean")
                put("description", "可选：是否为纯容器/占位层（true 表示仅用于排版，不参与任何 AI 图片资源生成）")
            })
            put("userPromptZh", buildJsonObject {
                put("type", "string")
                put("description", "可选：中文 AI 生图提示词（仅传统 Slots 工程生效）")
            })
            put("userPromptEn", buildJsonObject {
                put("type", "string")
                put("description", "可选：英文 AI 生图提示词（仅传统 Slots 工程生效）")
            })
            put("bounds", buildJsonObject {
                put("type", "object")
                put("description", "可选：局部相对坐标与尺寸对象 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
            put("cropRect", buildJsonObject {
                put("type", "object")
                put("description", "可选：参考图局部裁切区域坐标 { left, top, right, bottom }")
                put("properties", buildJsonObject {
                    put("left", buildJsonObject { put("type", "number") })
                    put("top", buildJsonObject { put("type", "number") })
                    put("right", buildJsonObject { put("type", "number") })
                    put("bottom", buildJsonObject { put("type", "number") })
                })
            })
            put("scaleConfig", buildJsonObject {
                put("type", "object")
                put("description", "可选：图片不透明主体缩放与发光对齐配置 { scaleX, scaleY, offsetX, offsetY, enabled, lockAspectRatio }")
                put("properties", buildJsonObject {
                    put("scaleX", buildJsonObject { put("type", "number") })
                    put("scaleY", buildJsonObject { put("type", "number") })
                    put("offsetX", buildJsonObject { put("type", "number") })
                    put("offsetY", buildJsonObject { put("type", "number") })
                    put("enabled", buildJsonObject { put("type", "boolean") })
                    put("lockAspectRatio", buildJsonObject { put("type", "boolean") })
                })
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
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'projectName' 不能为空")
        val blockId = arguments["blockId"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("参数 'blockId' 不能为空")

        onProgress?.invoke(0.1f, "正在读取工程结构...")
        val match = repository.findTemplatePair(projectName)
            ?: return ToolResult.error("未找到工程 '$projectName'")

        val state = match.second
        val isUiStudio = state.isUiStudioProject || state.studioPages.isNotEmpty()

        // 解析通用入参
        val assetHashArg = arguments["assetHash"]?.jsonPrimitive?.contentOrNull
        val assetFileNameArg = arguments["assetFileName"]?.jsonPrimitive?.contentOrNull
        val parentIdArg = arguments["parentId"]?.jsonPrimitive?.contentOrNull
        val isVisibleArg = arguments["isVisible"]?.jsonPrimitive?.booleanOrNull
        val opacityArg = arguments["opacity"]?.jsonPrimitive?.floatOrNull?.coerceIn(0.0f, 1.0f)
        val isPureContainerArg = arguments["isPureContainer"]?.jsonPrimitive?.booleanOrNull
        val userPromptZhArg = arguments["userPromptZh"]?.jsonPrimitive?.contentOrNull
        val userPromptEnArg = arguments["userPromptEn"]?.jsonPrimitive?.contentOrNull

        val typeStr = arguments["type"]?.jsonPrimitive?.contentOrNull
        val targetType = typeStr?.let {
            runCatching { UIBlockType.valueOf(it.uppercase()) }.getOrNull()
        }

        val boundsObj = arguments["bounds"]?.let { it as? JsonObject }
        val cropRectObj = arguments["cropRect"]?.let { it as? JsonObject }
        val scaleConfigObj = arguments["scaleConfig"]?.let { it as? JsonObject }

        // 解析素材切片哈希绑定（针对 UI Studio 工程优先支持）
        var resolvedAssetHash: String? = null
        var shouldUpdateAssetHash = false

        if (assetHashArg != null) {
            shouldUpdateAssetHash = true
            resolvedAssetHash = if (assetHashArg.isBlank()) null else assetHashArg
        } else if (assetFileNameArg != null && isUiStudio) {
            onProgress?.invoke(0.2f, "正在从素材库检索切片 '$assetFileNameArg'...")
            val assetLibPath = "templates/$projectName/asset_library.json"
            val rawJson = fileStorage.readFromFile(assetLibPath)
            if (rawJson.isNullOrBlank()) {
                return ToolResult.error("工程 '$projectName' 的素材库为空，无法通过名称 '$assetFileNameArg' 查找切片")
            }

            val library = runCatching {
                looseJson.decodeFromString<UIAssetLibrary>(rawJson)
            }.getOrElse {
                return ToolResult.error("解析素材库失败: ${it.message}")
            }

            val queryClean = assetFileNameArg.trim().lowercase()
            val matches = library.items.filter { item ->
                val pureName = item.name.lowercase()
                val fileName = item.relativePath.substringAfterLast('/').substringAfterLast('\\').lowercase()
                pureName == queryClean || fileName == queryClean ||
                        pureName == queryClean.substringBeforeLast('.') ||
                        fileName.substringBeforeLast('.') == queryClean.substringBeforeLast('.')
            }

            if (matches.isEmpty()) {
                return ToolResult.error("在素材库中未找到名称匹配 '$assetFileNameArg' 的切片素材")
            }
            if (matches.size > 1) {
                val candidateList = matches.joinToString(", ") { "${it.name} [hash=${it.hash}]" }
                return ToolResult.error("在素材库中匹配到多个同名切片 ($candidateList)，请传入具体的 'assetHash' 以消除歧义")
            }

            val matchedItem = matches.first()
            if (matchedItem.hash.isNullOrBlank()) {
                return ToolResult.error("匹配到的切片 '${matchedItem.name}' 尚未生成文件哈希，无法绑定")
            }
            shouldUpdateAssetHash = true
            resolvedAssetHash = matchedItem.hash
        }

        onProgress?.invoke(0.4f, "正在应用属性补丁...")

        if (isUiStudio) {
            // ==================== UI Studio 双轨更新链路 ====================
            var foundTargetBlock: UIStudioBlock? = null
            var targetPageIndex = -1

            val boundPages = state.studioPages.map { page ->
                page.copy(blocks = page.blocks.bindParents())
            }

            for ((idx, page) in boundPages.withIndex()) {
                val block = page.blocks.findBlockById(blockId) as? UIStudioBlock
                if (block != null) {
                    foundTargetBlock = block
                    targetPageIndex = idx
                    break
                }
            }

            if (foundTargetBlock == null || targetPageIndex < 0) {
                return ToolResult.error("在工程 '$projectName' 中未找到 ID 为 '$blockId' 的 UI Studio 图元")
            }

            val originalPage = boundPages[targetPageIndex]
            val originalAbsBounds = foundTargetBlock.absoluteBounds

            // 构建新属性副本（锁定非空类型）
            val targetBlock: UIStudioBlock = foundTargetBlock
            var updatedBlock: UIStudioBlock = targetBlock
            if (shouldUpdateAssetHash) {
                updatedBlock = updatedBlock.copy(assetHash = resolvedAssetHash)
            }
            isVisibleArg?.let { updatedBlock = updatedBlock.copy(isVisible = it) }
            opacityArg?.let { updatedBlock = updatedBlock.copy(opacity = it) }
            isPureContainerArg?.let { updatedBlock = updatedBlock.copy(isPureContainer = it) }
            targetType?.let { updatedBlock = updatedBlock.copy(type = it) }

            boundsObj?.let { bObj ->
                val left = bObj["left"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.bounds.left
                val top = bObj["top"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.bounds.top
                val right = bObj["right"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.bounds.right
                val bottom = bObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.bounds.bottom
                updatedBlock = updatedBlock.copy(bounds = SerialRect(left, top, right, bottom))
            }

            cropRectObj?.let { cObj ->
                val left = cObj["left"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.cropRect?.left ?: updatedBlock.bounds.left
                val top = cObj["top"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.cropRect?.top ?: updatedBlock.bounds.top
                val right = cObj["right"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.cropRect?.right ?: updatedBlock.bounds.right
                val bottom = cObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updatedBlock.cropRect?.bottom ?: updatedBlock.bounds.bottom
                updatedBlock = updatedBlock.copy(cropRect = SerialRect(left, top, right, bottom))
            }

            val finalBlocks: List<UIStudioBlock> = if (parentIdArg != null) {
                // ---------- 层级移动 (Reparenting) ----------
                val isMovingToRoot = parentIdArg.isBlank() || parentIdArg.equals("null", ignoreCase = true)
                val strippedBlocks = removeStudioBlockRecursive(originalPage.blocks, blockId)

                if (isMovingToRoot) {
                    // 移动到页面根层级：父级位移为 0，局部坐标直接等于绝对坐标
                    val newBounds = if (boundsObj != null) updatedBlock.bounds else originalAbsBounds
                    val blockAtRoot = updatedBlock.copy(bounds = newBounds, parent = null)
                    (strippedBlocks + blockAtRoot).bindParents()
                } else {
                    // 移动到指定父容器
                    if (parentIdArg == blockId) {
                        return ToolResult.error("不能将图元 '$blockId' 移动为其自身的子图元")
                    }
                    val targetParent = strippedBlocks.findBlockById(parentIdArg) as? UIStudioBlock
                        ?: return ToolResult.error("在页面中未找到目标父容器 ID '$parentIdArg'")

                    // 代数守恒：逆向换算相对于 targetParent 的局部相对矩形
                    val newLocalBounds = if (boundsObj != null) updatedBlock.bounds else targetParent.toLocalBounds(originalAbsBounds)
                    val blockInParent = updatedBlock.copy(bounds = newLocalBounds)
                    addStudioBlockToParentRecursive(strippedBlocks, parentIdArg, blockInParent).bindParents()
                }
            } else {
                // ---------- 原地递归更新 ----------
                updateStudioBlockRecursive(originalPage.blocks, blockId) { updatedBlock }.bindParents()
            }

            val updatedPages = boundPages.toMutableList()
            updatedPages[targetPageIndex] = originalPage.copy(blocks = finalBlocks)
            val updatedProject = state.copy(studioPages = updatedPages)

            onProgress?.invoke(0.8f, "正在持久化工程并刷新工作区...")
            repository.saveTemplate(projectName, updatedProject)

            try {
                McpUiActionPipeline.getActiveUiStudioViewModel()?.reload(updatedProject)
            } catch (e: Throwable) {
                AppLogger.w("UpdateBlockTool", "通知活跃 UI Studio 工作区刷新失败", e)
            }

            val resultJson = buildJsonObject {
                put("success", true)
                put("isUiStudio", true)
                put("updatedBlock", looseJson.encodeToJsonElement(updatedBlock))
            }.toString()

            onProgress?.invoke(1.0f, "UI Studio 图元属性已成功更新")
            return ToolResult.text(resultJson)
        } else {
            // ==================== 传统 Slots 双轨更新链路 ====================
            var foundBlock: UIBlock? = null
            var targetPageIndex = -1

            val boundPages = state.pages.map { page ->
                page.copy(blocks = page.blocks.bindParents())
            }

            for ((idx, page) in boundPages.withIndex()) {
                val block = page.blocks.findBlockById(blockId) as? UIBlock
                if (block != null) {
                    foundBlock = block
                    targetPageIndex = idx
                    break
                }
            }

            if (foundBlock == null || targetPageIndex < 0) {
                return ToolResult.error("在模板 '$projectName' 中未找到 ID 为 '$blockId' 的图元")
            }

            val originalPage = boundPages[targetPageIndex]
            val originalAbsBounds = foundBlock.absoluteBounds

            val targetBlock: UIBlock = foundBlock
            var updated: UIBlock = targetBlock
            userPromptZhArg?.let { updated = updated.copy(userPromptZh = it) }
            userPromptEnArg?.let { updated = updated.copy(userPromptEn = it) }
            isPureContainerArg?.let { updated = updated.copy(isPureContainer = it) }
            isVisibleArg?.let { updated = updated.copy(isVisible = it) }
            opacityArg?.let { updated = updated.copy(opacity = it) }
            targetType?.let { updated = updated.copy(type = it) }

            boundsObj?.let { bObj ->
                val left = bObj["left"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.left
                val top = bObj["top"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.top
                val right = bObj["right"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.right
                val bottom = bObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updated.bounds.bottom
                updated = updated.copy(bounds = SerialRect(left, top, right, bottom))
            }

            cropRectObj?.let { cObj ->
                val left = cObj["left"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.left ?: updated.bounds.left
                val top = cObj["top"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.top ?: updated.bounds.top
                val right = cObj["right"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.right ?: updated.bounds.right
                val bottom = cObj["bottom"]?.jsonPrimitive?.floatOrNull ?: updated.cropRect?.bottom ?: updated.bounds.bottom
                updated = updated.copy(cropRect = SerialRect(left, top, right, bottom))
            }

            scaleConfigObj?.let { scObj ->
                val currentSc = updated.scaleConfig
                val scaleX = scObj["scaleX"]?.jsonPrimitive?.floatOrNull ?: currentSc.scaleX
                val scaleY = scObj["scaleY"]?.jsonPrimitive?.floatOrNull ?: currentSc.scaleY
                val offsetX = scObj["offsetX"]?.jsonPrimitive?.floatOrNull ?: currentSc.offsetX
                val offsetY = scObj["offsetY"]?.jsonPrimitive?.floatOrNull ?: currentSc.offsetY
                val enabled = scObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: currentSc.enabled
                val lockAspectRatio = scObj["lockAspectRatio"]?.jsonPrimitive?.booleanOrNull ?: currentSc.lockAspectRatio
                updated = updated.copy(
                    scaleConfig = currentSc.copy(
                        scaleX = scaleX,
                        scaleY = scaleY,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        enabled = enabled,
                        lockAspectRatio = lockAspectRatio
                    )
                )
            }

            val finalBlocks: List<UIBlock> = if (parentIdArg != null) {
                // ---------- 层级移动 (Reparenting) ----------
                val isMovingToRoot = parentIdArg.isBlank() || parentIdArg.equals("null", ignoreCase = true)
                val strippedBlocks = removeBlockRecursive(originalPage.blocks, blockId)

                if (isMovingToRoot) {
                    val newBounds = if (boundsObj != null) updated.bounds else originalAbsBounds
                    val blockAtRoot = updated.copy(bounds = newBounds, parent = null)
                    (strippedBlocks + blockAtRoot).bindParents()
                } else {
                    if (parentIdArg == blockId) {
                        return ToolResult.error("不能将图元 '$blockId' 移动为其自身的子图元")
                    }
                    val targetParent = strippedBlocks.findBlockById(parentIdArg) as? UIBlock
                        ?: return ToolResult.error("在页面中未找到目标父容器 ID '$parentIdArg'")

                    val newLocalBounds = if (boundsObj != null) updated.bounds else targetParent.toLocalBounds(originalAbsBounds)
                    val blockInParent = updated.copy(bounds = newLocalBounds)
                    addBlockToParentRecursive(strippedBlocks, parentIdArg, blockInParent).bindParents()
                }
            } else {
                // ---------- 原地递归更新 ----------
                updateBlockRecursive(originalPage.blocks, blockId) { updated }.bindParents()
            }

            val updatedPages = boundPages.toMutableList()
            updatedPages[targetPageIndex] = originalPage.copy(blocks = finalBlocks)
            val updatedProject = state.copy(pages = updatedPages)

            onProgress?.invoke(0.7f, "正在持久化工程与离屏标注图...")
            repository.saveTemplate(projectName, updatedProject)

            try {
                TemplateOverlayRenderer.renderAndSaveLatest(projectName, updatedProject)
            } catch (e: Exception) {
                AppLogger.w("UpdateBlockTool", "更新标注图异常", e)
            }

            try {
                val activeVm = McpUiActionPipeline.getActiveViewModel()
                if (activeVm != null) {
                    activeVm.reload(updatedProject)
                }
            } catch (e: Throwable) {
                AppLogger.w("UpdateBlockTool", "通知活跃工作区刷新失败", e)
            }

            onProgress?.invoke(1.0f, "图元属性已成功更新")

            val resultJson = buildJsonObject {
                put("success", true)
                put("isUiStudio", false)
                put("updatedBlock", looseJson.encodeToJsonElement(updated))
            }.toString()

            return ToolResult.text(resultJson)
        }
    }

    // ==================== 私有递归层级辅助方法 (UI Studio) ====================

    private fun removeStudioBlockRecursive(blocks: List<UIStudioBlock>, targetId: String): List<UIStudioBlock> {
        val result = mutableListOf<UIStudioBlock>()
        for (b in blocks) {
            if (b.id != targetId) {
                val newChildren = removeStudioBlockRecursive(b.children, targetId)
                result.add(b.copy(children = newChildren))
            }
        }
        return result
    }

    private fun addStudioBlockToParentRecursive(
        blocks: List<UIStudioBlock>,
        parentId: String,
        newChild: UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            if (b.id == parentId) {
                b.copy(children = b.children + newChild)
            } else {
                b.copy(children = addStudioBlockToParentRecursive(b.children, parentId, newChild))
            }
        }
    }

    private fun updateStudioBlockRecursive(
        blocks: List<UIStudioBlock>,
        targetId: String,
        transform: (UIStudioBlock) -> UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else {
                b.copy(children = updateStudioBlockRecursive(b.children, targetId, transform))
            }
        }
    }

    // ==================== 私有递归层级辅助方法 (传统 Slots) ====================

    private fun removeBlockRecursive(blocks: List<UIBlock>, targetId: String): List<UIBlock> {
        val result = mutableListOf<UIBlock>()
        for (b in blocks) {
            if (b.id != targetId) {
                val newChildren = removeBlockRecursive(b.children, targetId)
                result.add(b.copy(children = newChildren))
            }
        }
        return result
    }

    private fun addBlockToParentRecursive(
        blocks: List<UIBlock>,
        parentId: String,
        newChild: UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            if (b.id == parentId) {
                b.copy(children = b.children + newChild)
            } else {
                b.copy(children = addBlockToParentRecursive(b.children, parentId, newChild))
            }
        }
    }

    private fun updateBlockRecursive(
        blocks: List<UIBlock>,
        targetId: String,
        transform: (UIBlock) -> UIBlock
    ): List<UIBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else {
                b.copy(children = updateBlockRecursive(b.children, targetId, transform))
            }
        }
    }
}
