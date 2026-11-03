package org.gemini.ui.forge.ui.uistudio.delegate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.MattingTaskItem
import org.gemini.ui.forge.service.assetsheet.AssetSheetAiDetector
import org.gemini.ui.forge.service.assetsheet.AssetSheetNormalizer
import org.gemini.ui.forge.ui.component.ToastType
import org.gemini.ui.forge.ui.uistudio.SliceItemStatus
import org.gemini.ui.forge.ui.uistudio.SliceStage
import org.gemini.ui.forge.ui.uistudio.SliceTaskItemState
import org.gemini.ui.forge.ui.uistudio.UIStudioState
import org.gemini.ui.forge.ui.uistudio.model.UIAssetFolder
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.asset.AssetFileNameGuard
import org.gemini.ui.forge.utils.calculateShortFileHash
import org.gemini.ui.forge.utils.cropImage
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.isSupportedImageFile
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * 切片导入域委托：承载外部图集大图 + 坐标 JSON 的导入配置弹窗全生命周期、
 * 零预设自适应离线解析、Gemini 视觉大模型逆向 AI 识别、
 * 可视化预审校对弹窗流转，以及确认后的多核物理切片、批量 AI 去背、
 * 素材登记入库与持久化等切片导入全流水线操作。
 *
 * @param getState 状态读取桥接（指向宿主 ViewModel 的 state getter）
 * @param setState 状态写入桥接（指向宿主 ViewModel 的 state setter）
 * @param scope 宿主全局协程作用域
 * @param mattingService 本地抠图服务（批量 AI 去背景）
 * @param assetLibrary 素材库域委托（切片完成后持久化 asset_library.json）
 * @param saveDraft 宿主草稿落盘回调（校对弹窗流转时自动保存初始草稿）
 */
class SliceImportDelegate(
    private val getState: () -> UIStudioState,
    private val setState: (UIStudioState) -> Unit,
    private val scope: CoroutineScope,
    private val mattingService: LocalMattingService,
    private val assetLibrary: AssetLibraryDelegate,
    private val saveDraft: (List<NormalizedAssetItem>) -> Unit,
) {

    /** 状态桥接属性：使平移自宿主的方法体内 `state = state.copy(...)` 零改动生效 */
    private var state: UIStudioState
        get() = getState()
        set(value) = setState(value)

    /**
     * 打开导入图集配置弹窗。
     */
    fun openImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = true)
        if (state.sourceImagePath.isNotBlank()) {
            updateSourceImagePath(state.sourceImagePath)
        }
        if (state.sourceJsonPath.isNotBlank()) {
            updateSourceJsonPath(state.sourceJsonPath)
        }
    }

    /**
     * 关闭导入图集配置弹窗。
     */
    fun closeImportSheetDialog() {
        state = state.copy(isImportSheetDialogVisible = false)
    }

    /**
     * 更新选中的源图片文件路径并进行磁盘真实物理校验。
     *
     * 在入口处第一时间无条件回显路径到状态层，避免文件选择器或输入迟滞，
     * 随后在后台通过 [isLocalFileExists] 安全探测物理文件真实性与图片格式。
     */
    fun updateSourceImagePath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            state = state.copy(
                sourceImagePath = "",
                isSourceImageFileExists = false,
                sourceImageError = null
            )
            return
        }

        // 1. 第一时间在主线程回显输入路径，保证界面 0 延迟响应
        state = state.copy(
            sourceImagePath = trimmed,
            sourceImageError = null
        )

        // 2. 异步执行跨平台轻量物理探测，杜绝任何崩溃或迟滞
        scope.launch(Dispatchers.Default) {
            val exists = runCatching { isFileExists(trimmed) }.getOrDefault(false)
            val isImage = isSupportedImageFile(trimmed)
            val error = when {
                !exists -> "大图文件在磁盘上不存在，请检查路径"
                !isImage -> "不支持的文件格式，仅支持 PNG、JPG、JPEG、WEBP"
                else -> null
            }
            withContext(Dispatchers.Main) {
                // 仅当用户未进行新输入时才写回校验状态
                if (state.sourceImagePath == trimmed) {
                    state = state.copy(
                        isSourceImageFileExists = exists && isImage,
                        sourceImageError = error
                    )
                }
            }
        }
    }

    /**
     * 更新选中的源 JSON 坐标文件路径并触发自适应解析与物理存在性校验。
     *
     * 在入口处第一时间无条件回显路径到状态层，随后在后台安全探测物理存在性并解析。
     */
    fun updateSourceJsonPath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            state = state.copy(
                sourceJsonPath = "",
                isSourceJsonFileExists = false,
                sourceJsonError = null,
                parseResult = null
            )
            return
        }

        // 1. 第一时间在主线程回显输入路径，保证界面 0 延迟响应
        state = state.copy(
            sourceJsonPath = trimmed,
            sourceJsonError = null
        )

        // 2. 异步执行跨平台轻量物理探测并自适应解析
        scope.launch(Dispatchers.Default) {
            val exists = runCatching { isFileExists(trimmed) }.getOrDefault(false)
            if (!exists) {
                withContext(Dispatchers.Main) {
                    if (state.sourceJsonPath == trimmed) {
                        state = state.copy(
                            isSourceJsonFileExists = false,
                            sourceJsonError = "坐标 JSON 文件在磁盘上不存在，请检查路径",
                            parseResult = null,
                            isParsingJson = false
                        )
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (state.sourceJsonPath == trimmed) {
                    state = state.copy(
                        isSourceJsonFileExists = true,
                        sourceJsonError = null
                    )
                }
            }
            parseJsonFile(trimmed)
        }
    }

    /**
     * 读取指定本地路径的 JSON 文件并进行零预设自适应结构解析。
     *
     * 支持绝对物理路径（如 E:\...）与相对工作区路径，调用跨平台安全读取器 [readLocalFileBytes]。
     */
    fun parseJsonFile(path: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val content = readLocalFileBytes(path)?.decodeToString()
                if (content.isNullOrBlank()) {
                    Toast.show("JSON 文件为空或无法读取", ToastType.ERROR)
                    state = state.copy(
                        isParsingJson = false,
                        isSourceJsonFileExists = false,
                        sourceJsonError = "JSON 文件内容为空或无法读取",
                        parseResult = null
                    )
                    return@launch
                }
                parseJsonContent(content)
            } catch (e: Exception) {
                Toast.show("读取文件失败: ${e.message}", ToastType.ERROR)
                state = state.copy(
                    isParsingJson = false,
                    isSourceJsonFileExists = false,
                    sourceJsonError = "读取文件失败: ${e.message}",
                    parseResult = null
                )
            }
        }
    }

    /**
     * 直接对文本内容执行零预设自适应解析。
     */
    fun parseJsonContent(content: String) {
        state = state.copy(isParsingJson = true)
        scope.launch {
            try {
                val result = AssetSheetNormalizer.normalizeJsonOffline(content)
                state = state.copy(
                    parseResult = result,
                    isParsingJson = false
                )
                if (result.items.isNotEmpty()) {
                    Toast.show("成功识别 ${result.items.size} 个图元组件", ToastType.SUCCESS)
                } else {
                    Toast.show("未能从内容中识别出有效的图元坐标", ToastType.INFO)
                }
            } catch (e: Exception) {
                state = state.copy(isParsingJson = false)
                Toast.show("解析失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 从导入配置弹窗流转进入切片可视化预审校对弹窗（阶段三）。
     */
    fun proceedToVerifyDialog() {
        val imagePath = state.sourceImagePath.trim()
        val imageExists = state.isSourceImageFileExists && imagePath.isNotBlank()
        if (!imageExists) {
            Toast.show("大图文件不存在或路径无效，无法进入校对", ToastType.ERROR)
            state = state.copy(
                isSourceImageFileExists = false,
                sourceImageError = "大图文件在磁盘上不存在，请检查路径"
            )
            return
        }

        val result = state.parseResult
        val items = result?.items ?: emptyList()
        if (items.isEmpty()) {
            Toast.show("图元列表为空，无法进入校对", ToastType.ERROR)
            return
        }

        state = state.copy(
            isImportSheetDialogVisible = false,
            isVerifySliceDialogVisible = true,
            verifySliceItems = items
        )
        // 自动落盘初始草稿
        saveDraft(items)
    }

    /**
     * 针对纯图场景调用 Gemini 视觉多模态大模型逆向检测图元坐标，并流转进入阶段三切片校对弹窗。
     *
     * @param apiKey 可选自定义 API Key，缺省时自动从本地配置中心读取
     */
    fun startAiDetectAndProceed(apiKey: String? = null) {
        val imagePath = state.sourceImagePath.trim()
        if (imagePath.isBlank()) {
            Toast.show("请先选择或输入待识别的图集大图路径", ToastType.INFO)
            return
        }
        val imageExists = state.isSourceImageFileExists && imagePath.isNotBlank()
        if (!imageExists) {
            Toast.show("大图文件不存在或路径无效，无法启动 AI 识别", ToastType.ERROR)
            state = state.copy(
                isSourceImageFileExists = false,
                sourceImageError = "大图文件在磁盘上不存在，请检查路径"
            )
            return
        }

        state = state.copy(
            isAiDetectingCoords = true,
            aiDetectStatusText = "正在准备上传图集图片并请求 Gemini 视觉大模型..."
        )

        scope.launch {
            try {
                val (result, _) = AssetSheetAiDetector.detectAssetSheet(
                    imagePath = imagePath,
                    apiKey = apiKey,
                    projectName = state.project?.projectId,
                    onProgress = { _, message ->
                        state = state.copy(aiDetectStatusText = message)
                    }
                )

                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = "",
                    parseResult = result,
                    isImportSheetDialogVisible = false,
                    isVerifySliceDialogVisible = true,
                    verifySliceItems = result.items
                )

                // 自动保存初始草稿
                saveDraft(result.items)
                Toast.show("AI 成功识别出 ${result.items.size} 个图元组件！", ToastType.SUCCESS)
            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "AI 视觉图集逆向识别失败: ${e.message}", e)
                state = state.copy(
                    isAiDetectingCoords = false,
                    aiDetectStatusText = ""
                )
                Toast.show("AI 识别失败: ${e.message}", ToastType.ERROR)
            }
        }
    }

    /**
     * 关闭可视化预审校对弹窗。
     */
    /** 整图去背后的透明源（生命周期 = 本次导入会话，不入草稿序列化） */
    var transparentSourceBytes: ByteArray? = null
        private set

    /** 对原始大图执行整图去背；成功缓存透明源并返回，失败返回 null（调用方回退原图展示） */
    suspend fun mattingSourceImage(sourceBytes: ByteArray): ByteArray? {
        transparentSourceBytes?.let { return it }
        val result = mattingService.removeBackground(sourceBytes) { log ->
            AppLogger.d("SliceImport", "[SourceMatting] $log")
        }
        if (result == null) {
            Toast.show("原图去背失败（请检查 Python 环境），已回退展示原图，切片后将按逐张方式去背", ToastType.INFO)
            return null
        }
        transparentSourceBytes = result
        Toast.show("✅ 原图已整体去背，切片将直接输出透明素材", ToastType.SUCCESS)
        return result
    }

    fun clearTransparentSource() {
        transparentSourceBytes = null
    }

    fun closeVerifySliceDialog() {
        clearTransparentSource()
        state = state.copy(isVerifySliceDialogVisible = false)
    }

    /**
     * 确认校对切片并执行真正的物理透明切片与入库落盘（阶段四核心实现）。
     */
    fun onConfirmSlice(
        items: List<NormalizedAssetItem>,
        repo: TemplateRepository,
        onProjectUpdated: ((ProjectState) -> Unit)? = null,
        targetSubPath: String? = null,
        targetFolderId: String? = null
    ) {
        val proj = state.project ?: return
        val imagePath = state.sourceImagePath
        if (imagePath.isBlank() || items.isEmpty()) {
            Toast.show("未指定原图或图元列表为空", ToastType.INFO)
            return
        }

        scope.launch(Dispatchers.Default) {
            val sanitizedName = proj.projectId.trim().replace(" ", "_")
            val cleanSubPath = targetSubPath?.trim()?.trim('/', '\\')
            
            // 自动检索已有文件夹，或在新目录不存在时自动构建新文件夹对象
            val existingFolder = if (!cleanSubPath.isNullOrBlank()) {
                state.assetLibrary.folders.find { it.name.equals(cleanSubPath, ignoreCase = true) }
            } else if (targetFolderId != null) {
                state.assetLibrary.folders.find { it.id == targetFolderId }
            } else if (targetSubPath == null) {
                state.assetLibrary.folders.find { it.id == state.selectedFolderId }
            } else null

            val autoCreatedFolder = if (existingFolder == null && !cleanSubPath.isNullOrBlank()) {
                UIAssetFolder(
                    id = "folder_${getCurrentTimeMillis()}",
                    name = cleanSubPath,
                    parentFolderId = null
                )
            } else null

            val targetFolder = existingFolder ?: autoCreatedFolder
            val effectiveFolderId = targetFolder?.id
            val folderSubDir = if (targetFolder != null) "${targetFolder.name.trim().replace(" ", "_")}/" else ""
            val targetDir = "templates/$sanitizedName/assets/image/$folderSubDir".trimEnd('/')

            // 1. 计算每个切片图元的文件名（优先使用 name，name 缺失时兜底 id）
            val safeNames = items.mapIndexed { idx, meta ->
                AssetFileNameGuard.sanitizeFileName(meta.name)
                    .ifBlank { AssetFileNameGuard.sanitizeFileName(meta.id).ifBlank { "asset_$idx" } }
            }

            // 2. 整批重名预检：批内互斥 + 目标目录同名检测（不区分大小写）
            val conflicts = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            safeNames.forEach { n ->
                if (!seen.add(n.lowercase())) {
                    conflicts += n
                }
            }
            safeNames.forEach { n ->
                if (AssetFileNameGuard.isFileNameTakenInDir(assetLibrary.storage, targetDir, "$n.png")) {
                    conflicts += n
                }
            }

            // 任一冲突则整批拒绝，0 文件落盘，保持校对弹窗打开供用户直接改名
            if (conflicts.isNotEmpty()) {
                val conflictSummary = conflicts.distinct().joinToString("、")
                Toast.show("切片保存被拒绝：以下名称在目标目录已存在或批内重复（不区分大小写）：$conflictSummary。请在校对弹窗中修改名称后重试", ToastType.ERROR)
                return@launch
            }

            val initialTaskItems = items.mapIndexed { idx, item ->
                val assetId = item.id.ifBlank { "asset_${getCurrentTimeMillis()}_$idx" }
                SliceTaskItemState(
                    id = assetId,
                    name = item.name,
                    width = item.bounds.width.toInt().coerceAtLeast(1),
                    height = item.bounds.height.toInt().coerceAtLeast(1),
                    category = item.type.name.lowercase(),
                    status = SliceItemStatus.PENDING
                )
            }

            val startTime = getCurrentTimeMillis()

            state = state.copy(
                isVerifySliceDialogVisible = false,
                verifySliceItems = items,
                isSlicingAssets = true,
                sliceProgress = 0f,
                sliceStatusText = "正在读取图集大图...",
                sliceTaskItems = initialTaskItems,
                sliceStartTimeMillis = startTime,
                sliceCurrentStage = SliceStage.PREPARING,
                sliceCurrentItemName = ""
            )

            try {
                val imageBytes = readLocalFileBytes(imagePath)
                if (imageBytes == null || imageBytes.isEmpty()) {
                    Toast.show("无法读取图集文件: $imagePath", ToastType.ERROR)
                    state = state.copy(isSlicingAssets = false)
                    return@launch
                }

                val sanitizedName = proj.projectId.trim().replace(" ", "_")
                val canvasW = state.canvasWidth
                val canvasH = state.canvasHeight

                // ★ 关键性能突破：在循环外仅解码一次大图为共享不可变 Bitmap，彻底消除 N 次巨幅大图重复解码开销！
                // 优先使用内存中的透明原图数据，切片天然带透明通道
                val mattingSource = transparentSourceBytes
                val decodeBytes = mattingSource ?: imageBytes
                val baseImage = try {
                    Image.makeFromEncoded(decodeBytes)
                } catch (e: Exception) {
                    AppLogger.e("UIStudioViewModel", "❌ 解码大图失败", e)
                    null
                }
                if (baseImage == null) {
                    Toast.show("无法解析图集图像数据", ToastType.ERROR)
                    state = state.copy(isSlicingAssets = false)
                    return@launch
                }
                val imgWidth = baseImage.width
                val imgHeight = baseImage.height
                val srcBitmap = Bitmap.makeFromImage(baseImage).apply { setImmutable() }

                AppLogger.i("UIStudioViewModel", "=== [SLICE_VERIFY] 确认切图开始: 首个图元[${items.firstOrNull()?.name}] bounds=${items.firstOrNull()?.bounds}, 大图分辨率=${imgWidth}x${imgHeight}, 前置去背=${mattingSource != null} ===")

                val total = items.size
                val shouldRemoveBg = state.autoRemoveBgOnSlice

                // ★ 阶段一：极速物理多核切片（零大图重复解码，切片流水线全量保底落盘）
                state = state.copy(
                    sliceCurrentStage = SliceStage.SLICING,
                    sliceProgress = 0f,
                    sliceStatusText = "正在执行微秒级物理多核切片 (0/$total)..."
                )

                val concurrencyLimit = 8
                val sliceSemaphore = Semaphore(concurrencyLimit)
                val progressMutex = Mutex()
                var slicedCount = 0

                data class SlicedItemMeta(
                    val index: Int,
                    val item: NormalizedAssetItem,
                    val assetId: String,
                    val relPath: String,
                    val diskAbsolutePath: String
                )

                val slicedMetas = coroutineScope {
                    items.mapIndexed { idx, item ->
                        async(Dispatchers.Default) {
                            sliceSemaphore.withPermit {
                                val assetId = initialTaskItems[idx].id
                                val relPath = "templates/$sanitizedName/assets/image/${folderSubDir}${safeNames[idx]}.png"
                                val tFile = TemplateFile(relPath)
                                val diskAbsolutePath = tFile.getAbsolutePath()

                                // 更新单项状态为切片中
                                progressMutex.withLock {
                                    val currentList = state.sliceTaskItems.toMutableList()
                                    if (idx < currentList.size) {
                                        currentList[idx] = currentList[idx].copy(status = SliceItemStatus.SLICING)
                                        state = state.copy(
                                            sliceTaskItems = currentList,
                                            sliceCurrentItemName = item.name
                                        )
                                    }
                                }

                                // 1. 调用 Skia 执行微秒级物理切片 (原图绝对物理像素 1:1 精准无损裁切)
                                val sliceBytes = cropImage(
                                    srcBitmap = srcBitmap,
                                    imageWidth = imgWidth,
                                    imageHeight = imgHeight,
                                    bounds = item.bounds,
                                    originalWidth = imgWidth.toFloat(),
                                    originalHeight = imgHeight.toFloat(),
                                    isPng = true
                                )

                                // 2. 物理写入磁盘素材库目录
                                if (sliceBytes != null && sliceBytes.isNotEmpty()) {
                                    tFile.writeBytes(sliceBytes)
                                }

                                // 视觉节流：保证流水线流转可感知（约 15~20ms）
                                delay(20)

                                // 3. 更新进度与单项状态 (已切片完成)
                                progressMutex.withLock {
                                    slicedCount++
                                    val currentList = state.sliceTaskItems.toMutableList()
                                    if (idx < currentList.size) {
                                        val itemFinalStatus = if (shouldRemoveBg) {
                                            if (mattingSource != null) SliceItemStatus.SUCCESS else SliceItemStatus.SLICED
                                        } else {
                                            SliceItemStatus.SUCCESS
                                        }
                                        currentList[idx] = currentList[idx].copy(
                                            status = itemFinalStatus
                                        )
                                        val slicePartProgress = slicedCount.toFloat() / total.coerceAtLeast(1)
                                        state = state.copy(
                                            sliceTaskItems = currentList,
                                            sliceProgress = slicePartProgress,
                                            sliceStatusText = "正在并发切片 ($slicedCount/$total): ${item.name}"
                                        )
                                    }
                                }

                                SlicedItemMeta(
                                    index = idx,
                                    item = item,
                                    assetId = assetId,
                                    relPath = relPath,
                                    diskAbsolutePath = diskAbsolutePath
                                )
                            }
                        }
                    }.awaitAll().sortedBy { it.index }
                }

                // 阶段一收口停留：让切片完成态清晰可见
                delay(200)

                // ★ 阶段二：若开启自动去背且未提前做过整图前置去背，启动常驻单进程 AI 批量去背景（仅启动 1 次 Python + 模型常驻复用！）
                if (shouldRemoveBg && mattingSource == null) {
                    state = state.copy(
                        sliceCurrentStage = SliceStage.MATTING,
                        sliceProgress = 0f,
                        sliceCurrentItemName = "",
                        sliceStatusText = "⚡ 正在初始化常驻 AI 去背引擎并批量处理..."
                    )

                    val mattingTasks = slicedMetas.map { meta ->
                        MattingTaskItem(
                            id = meta.assetId,
                            name = meta.item.name,
                            input = meta.diskAbsolutePath,
                            output = meta.diskAbsolutePath
                        )
                    }

                    val mattingSuccess = mattingService.batchRemoveBackground(
                        tasks = mattingTasks,
                        onProgress = { current, totalCount, itemId, itemName ->
                            val currentList = state.sliceTaskItems.toMutableList()
                            val targetIdx = currentList.indexOfFirst { it.id == itemId }
                            if (targetIdx != -1) {
                                currentList[targetIdx] = currentList[targetIdx].copy(status = SliceItemStatus.SUCCESS)
                            }
                            val mattingProgress = (current.toFloat() / totalCount.coerceAtLeast(1))
                            state = state.copy(
                                sliceTaskItems = currentList,
                                sliceProgress = mattingProgress,
                                sliceCurrentItemName = itemName,
                                sliceStatusText = "正在批量 AI 去背景 ($current/$totalCount): $itemName"
                            )
                        },
                        onLog = { logLine ->
                            AppLogger.d("UIStudioViewModel", "[BatchMattingLog] $logLine")
                        }
                    )

                    if (!mattingSuccess) {
                        AppLogger.w("UIStudioViewModel", "⚠️ 批量去背部分完成或异常，原始切片均已完整保底保留")
                    }
                }

                // ★ 阶段三：登记入库与素材库持久化
                state = state.copy(
                    sliceCurrentStage = SliceStage.REGISTERING,
                    sliceProgress = 0.96f,
                    sliceStatusText = "正在登记素材库并持久化..."
                )

                val newAssetItems = slicedMetas.map { meta ->
                    val fileHash = calculateShortFileHash(meta.relPath) ?: meta.relPath.hashCode().toString(16)
                    UIAssetItem(
                        hash = fileHash,
                        relativePath = meta.relPath,
                        width = meta.item.bounds.width.toInt().coerceAtLeast(1),
                        height = meta.item.bounds.height.toInt().coerceAtLeast(1),
                        category = meta.item.type.name.lowercase(),
                        folderId = effectiveFolderId
                    )
                }

                val finalFolders = if (autoCreatedFolder != null && state.assetLibrary.folders.none { it.id == autoCreatedFolder.id }) {
                    state.assetLibrary.folders + autoCreatedFolder
                } else {
                    state.assetLibrary.folders
                }
                val finalExpandedIds = if (autoCreatedFolder != null) {
                    state.expandedFolderIds + autoCreatedFolder.id
                } else {
                    state.expandedFolderIds
                }

                val updatedLibrary = state.assetLibrary.copy(
                    folders = finalFolders,
                    items = state.assetLibrary.items.filterNot { old -> newAssetItems.any { it.hash == old.hash || it.relativePath == old.relativePath } } + newAssetItems
                )

                // 物理清理切片校对草稿文件与透明源内存缓存
                clearTransparentSource()
                try {
                    val draftPath = "templates/$sanitizedName/import_draft.json"
                    TemplateFile(draftPath).delete()
                } catch (_: Exception) {}

                state = state.copy(
                    assetLibrary = updatedLibrary,
                    expandedFolderIds = finalExpandedIds,
                    sliceProgress = 1.0f,
                    sliceCurrentStage = SliceStage.FINISHED,
                    sliceStatusText = "🎉 全部切片与去背已完成！"
                )

                // 持久化素材库到 asset_library.json 并触发全量刷新与高亮聚焦
                assetLibrary.saveAssetLibrary()
                assetLibrary.refreshAssetLibrary(focusAssetPath = newAssetItems.firstOrNull()?.relativePath)

                withContext(Dispatchers.Main) {
                    onProjectUpdated?.invoke(proj)
                }

                delay(600)
                state = state.copy(
                    isSlicingAssets = false,
                    importDraft = null,
                    isDraftPromptVisible = false,
                    sliceStatusText = ""
                )

                Toast.show("🎉 成功切片并入库 ${newAssetItems.size} 个素材到素材库中！", ToastType.SUCCESS)
                AppLogger.i("UIStudioViewModel", "✅ 物理切片与素材库持久化完成: 共 ${newAssetItems.size} 个素材入库")

            } catch (e: Exception) {
                AppLogger.e("UIStudioViewModel", "❌ 切图入库失败", e)
                Toast.show("切片入库失败: ${e.message}", ToastType.ERROR)
                state = state.copy(isSlicingAssets = false)
            }
        }
    }
}
