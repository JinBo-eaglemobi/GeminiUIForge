package org.gemini.ui.forge.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.model.ui.*
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.utils.*

/**
 * 资产管理逻辑委托。
 * 负责资产绑定、历史记录加载、图片裁剪以及九宫格固化（Bake）等 IO/图像处理逻辑。
 */
class AssetManagerDelegate(
    private val scope: CoroutineScope,
    private val templateRepo: TemplateRepository,
    private val getState: () -> ProjectWorkspaceState,
    private val updateState: ((ProjectWorkspaceState) -> ProjectWorkspaceState) -> Unit,
    private val markDirty: () -> Unit,
    private val notifySelectionHandled: () -> Unit
) {

    /** 加载模块的历史生成记录 */
    suspend fun loadHistoricalImages(blockId: String): List<TemplateFile> {
        val rootDir = templateRepo.getDataDir()
        val dir = "$rootDir/templates/${getState().projectName.replace(" ", "_")}/assets/$blockId"
        return listFilesInLocalDirectory(dir)
            .filter { it.endsWith(".png") || it.endsWith(".jpg") }
            .map { absPath ->
                val rel = absPath.replace("\\", "/").let {
                    val root = GlobalAppEnv.currentRootPath
                    if (it.startsWith(root)) it.removePrefix(root).removePrefix("/") else it
                }
                TemplateFile(rel)
            }
    }

    /** 选中并应用图片资源 */
    fun onImageSelected(imageUri: TemplateFile) {
        val pageId = getState().selectedPageId ?: return
        val blockId = getState().batchPendingConfirmBlock?.id ?: getState().selectedBlockId ?: return
        
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == pageId) page.copy(blocks = updateBlockInList(page.blocks, blockId) {
                    it.copy(currentImageUri = imageUri)
                }) else page
            }
            currentState.copy(
                project = currentState.project.copy(pages = updatedPages),
                generatedCandidates = if (currentState.batchPendingConfirmBlock != null) currentState.generatedCandidates else emptyList()
            )
        }
        markDirty()
        notifySelectionHandled()
    }

    fun clearSelectedImage(blockId: String) {
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(page.blocks, blockId) { it.copy(currentImageUri = null) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 保存当前的风格设置与参考图，并执行磁盘缓存管理 */
    fun saveStyleSettings(onComplete: () -> Unit) {
        val currentState = getState()
        val currentStyle = currentState.globalStyle
        val currentRefUri = currentState.referenceImageUri
        val projectName = currentState.projectName
        val sanitizedProjectName = projectName.replace(" ", "_")
        
        scope.launch {
            var finalRefUri: TemplateFile? = currentRefUri

            if (currentRefUri != null) {
                val destDir = "templates/$sanitizedProjectName/assets/style_ref"
                val currentRelPath = currentRefUri.relativePath
                
                try {
                    if (!currentRelPath.contains(destDir)) {
                        val newHash = calculateFileHash(currentRefUri.getAbsolutePath())
                        var isSame = false

                        if (newHash != null) {
                            val destDirFile = TemplateFile(destDir)
                            val existingFiles = if (destDirFile.exists()) listFilesInLocalDirectory(destDirFile.getAbsolutePath()) else emptyList()
                            
                            if (existingFiles.isNotEmpty()) {
                                val oldFilePath = existingFiles.first()
                                val oldHash = calculateFileHash(oldFilePath)
                                if (oldHash == newHash) {
                                    isSame = true
                                    val rel = oldFilePath.replace("\\", "/").let {
                                        val root = GlobalAppEnv.currentRootPath
                                        if (it.startsWith(root)) it.removePrefix(root).removePrefix("/") else it
                                    }
                                    finalRefUri = TemplateFile(rel)
                                }
                            }
                            
                            if (!isSame) {
                                existingFiles.forEach { deleteLocalFile(it) }
                                val ext = currentRelPath.substringAfterLast(".", "jpg")
                                val targetFileName = "active_style_ref_${newHash.take(8)}.$ext"
                                val relDestPath = "$destDir/$targetFileName"
                                val tFile = TemplateFile(relDestPath)
                                val success = copyLocalFile(currentRefUri.getAbsolutePath(), tFile.getAbsolutePath())
                                if (success) {
                                    finalRefUri = tFile
                                    updateState { it.copy(referenceImageUri = finalRefUri) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("AssetManager", "处理风格图失败: ${e.message}")
                }
            } else {
                try {
                    val destDir = "templates/$sanitizedProjectName/assets/style_ref"
                    val tDir = TemplateFile(destDir)
                    if (tDir.exists()) tDir.delete(recursive = true)
                } catch (e: Exception) {}
            }

            val updatedProject = getState().project.copy(
                globalStyle = currentStyle,
                styleReferenceUri = finalRefUri
            )
            updateState { it.copy(project = updatedProject) }
            markDirty()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun setGlobalStyle(style: String) {
        updateState { it.copy(globalStyle = style) }
        markDirty()
    }

    fun setReferenceImageExternal(uriOrPath: String?) {
        if (uriOrPath == null) {
            updateState { it.copy(referenceImageUri = null) }
            markDirty()
            return
        }
        scope.launch {
            val tFile = if (!uriOrPath.startsWith("/") && !uriOrPath.contains(":\\")) {
                TemplateFile(uriOrPath)
            } else {
                templateRepo.archiveExternalImages(getState().projectName, listOf(uriOrPath)).firstOrNull()
            }
            if (tFile != null) {
                updateState { it.copy(referenceImageUri = tFile) }
                markDirty()
            }
        }
    }

    fun setReferenceImage(tFile: TemplateFile?) {
        updateState { it.copy(referenceImageUri = tFile) }
        markDirty()
    }

    /** 执行物理图片固化（Bake） */
    fun bakeBlockImage(
        blockId: String,
        resizeMode: ImageResizeMode,
        ninePatchConfig: NinePatchConfig,
        targetWidth: Int,
        targetHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        imageBytes: ByteArray? = null,
        originalCropBytes: ByteArray? = null
    ) {
        val currentState = getState()
        val block = findBlockById(currentState.project.pages.flatMap { it.blocks }, blockId) ?: return
        val currentUri = block.currentImageUri
        if (currentUri == null && imageBytes == null) return
        
        val projectName = currentState.projectName

        scope.launch {
            updateState { it.copy(isGenerating = true) }
            try {
                if (originalCropBytes != null) {
                    templateRepo.saveBlockResource(projectName, blockId, "crop_${getCurrentTimeMillis()}", originalCropBytes, isPng = true)
                }

                val bytes = withContext(Dispatchers.Default) {
                    if (imageBytes != null) {
                        bakeNinePatchImage(imageBytes, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), contentWidth.coerceAtLeast(1), contentHeight.coerceAtLeast(1), resizeMode, ninePatchConfig)
                    } else {
                        bakeNinePatchImage(currentUri!!.getAbsolutePath(), targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), contentWidth.coerceAtLeast(1), contentHeight.coerceAtLeast(1), resizeMode, ninePatchConfig)
                    }
                }

                if (bytes != null) {
                    val newFile = templateRepo.saveBlockResource(projectName, blockId, "baked_${getCurrentTimeMillis()}", bytes, isPng = true)
                    
                    updateState { s ->
                        val updatedPages = s.project.pages.map { page ->
                            if (page.id == s.selectedPageId) page.copy(
                                blocks = updateBlockInList(page.blocks, blockId) {
                                    it.copy(
                                        currentImageUri = newFile,
                                        resizeMode = ImageResizeMode.STRETCH,
                                        ninePatchConfig = NinePatchConfig(0, 0, 0, 0),
                                        bounds = it.bounds.copy(right = it.bounds.left + targetWidth.toFloat(), bottom = it.bounds.top + targetHeight.toFloat())
                                    )
                                }
                            ) else page
                        }
                        s.copy(
                            project = s.project.copy(pages = updatedPages),
                            generatedCandidates = if (s.batchPendingConfirmBlock != null) s.generatedCandidates else emptyList()
                        )
                    }
                    templateRepo.saveTemplate(projectName, getState().project)
                    markDirty()
                    notifySelectionHandled()
                }
            } catch (e: Exception) {
                AppLogger.e("AssetManager", "固化失败", e)
            } finally {
                updateState { it.copy(isGenerating = false) }
            }
        }
    }

    fun updateBlockPrompt(blockId: String, lang: org.gemini.ui.forge.model.app.PromptLanguage, prompt: String) {
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(page.blocks, blockId) { 
                        if (lang == org.gemini.ui.forge.model.app.PromptLanguage.EN) it.copy(userPromptEn = prompt) else it.copy(userPromptZh = prompt)
                    }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 更新模块属性 */
    fun updateBlockProperties(blockId: String, properties: BlockProperties) {
        updateState { currentState ->
            val updatedPages = currentState.project.pages.map { page ->
                if (page.id == currentState.selectedPageId) page.copy(
                    blocks = updateBlockInList(page.blocks, blockId) { it.copy(properties = properties) }) else page
            }
            currentState.copy(project = currentState.project.copy(pages = updatedPages))
        }
        markDirty()
    }

    /** 按钮特定状态资源选择 */
    fun onButtonStateImageSelected(imageUri: TemplateFile, target: AssetGenerationDelegate.ButtonGenTarget) {
        val currentState = getState()
        val block = currentState.selectedBlock ?: return
        if (block.type != UIBlockType.BUTTON) return
        
        val existingProps = block.properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties(isMultiState = true)
        val newProps = when(target) {
            AssetGenerationDelegate.ButtonGenTarget.PRESSED -> existingProps.copy(pressedUri = imageUri, isMultiState = true)
            AssetGenerationDelegate.ButtonGenTarget.DISABLED -> existingProps.copy(disabledUri = imageUri, isMultiState = true)
            else -> existingProps
        }
        updateBlockProperties(block.id, newProps)
    }

    // --- 内部辅助 ---

    private fun findBlockById(blocks: List<UIBlock>, id: String): UIBlock? {
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }

    private fun updateBlockInList(blocks: List<UIBlock>, blockId: String, transform: (UIBlock) -> UIBlock): List<UIBlock> {
        return blocks.map { block ->
            if (block.id == blockId) transform(block)
            else {
                val newChildren = updateBlockInList(block.children, blockId, transform)
                if (newChildren !== block.children) block.copy(children = newChildren) else block
            }
        }
    }
}
