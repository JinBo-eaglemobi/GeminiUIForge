package org.gemini.ui.forge.data.repository

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.model.ui.UIStudioProjectConfig
import org.gemini.ui.forge.model.ui.UIStudioPageIndexItem
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 模板持久化仓库类，负责项目模板及关联资源的保存、读取和删除
 */
class TemplateRepository(
    val fileStorage: LocalFileStorage = LocalFileStorage(),
    private val httpClient: HttpClient = NetworkClient.shared
) {

    /** 模板项目存放在数据根目录下的相对子目录名称 */
    val PROJECTS_DIR = "templates"

    /**
     * 将生成的资源保存到模块专用的资产目录下
     */
    suspend fun saveBlockResource(
        templateName: String,
        blockId: String,
        fileNamePrefix: String,
        bytes: ByteArray,
        isPng: Boolean = true
    ): TemplateFile {
        val sanitizedName = templateName.replace(" ", "_")
        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val ext = if (isPng) "png" else "jpg"

        // 构造强类型相对路径
        val relPath = "$PROJECTS_DIR/$sanitizedName/assets/$blockId/${fileNamePrefix}_$timestamp.$ext"
        val tFile = TemplateFile(relPath)

        // 执行写入 (TemplateFile.writeBytes 内部会自动处理绝对路径拼接)
        tFile.writeBytes(bytes)

        AppLogger.i("TemplateRepository", "✅ Block 资源已保存: $relPath")
        return tFile
    }

    /**
     * 将临时图片保存到模板的 cache 目录下
     */
    suspend fun saveCacheImage(templateName: String, fileNamePrefix: String, bytes: ByteArray): TemplateFile {
        val sanitizedName = templateName.replace(" ", "_")
        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val relPath = "$PROJECTS_DIR/$sanitizedName/cache/${fileNamePrefix}_$timestamp.jpg"
        val tFile = TemplateFile(relPath)
        tFile.writeBytes(bytes)
        return tFile
    }

    /**
     * 清理指定模板下超过 24 小时的缓存文件
     */
    suspend fun cleanupExpiredCache(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        val cacheDirRel = "$PROJECTS_DIR/$sanitizedName/cache"
        AppLogger.i("TemplateRepository", "🧹 开始清理过期缓存: $sanitizedName")
        try {
            val rootDir = fileStorage.getDataDir()
            val fullCacheDir = "$rootDir/$cacheDirRel"
            val files = listFilesInLocalDirectory(fullCacheDir)
            val now = org.gemini.ui.forge.getCurrentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L

            var count = 0
            files.forEach { filePath ->
                val lastMod = getLocalFileLastModified(filePath)
                if (now - lastMod > oneDay) {
                    deleteLocalFile(filePath)
                    count++
                }
            }
            AppLogger.i("TemplateRepository", "✅ 缓存清理完成")
        } catch (e: Exception) {
            AppLogger.e("TemplateRepository", "❌ 清理缓存异常", e)
        }
    }

    /**
     * 将外部图片资源（本地绝对路径、远程 URL 或 Base64）归档到模板的资产目录下。
     * 返回归档后的 TemplateFile 列表。
     */
    suspend fun archiveExternalImages(templateName: String, externalUris: List<String>): List<TemplateFile> {
        val sanitizedName = templateName.replace(" ", "_")
        val archivedFiles = mutableListOf<TemplateFile>()

        externalUris.forEachIndexed { index, originalPath ->
            try {
                var bytes: ByteArray? = null
                var ext = "png"

                when {
                    originalPath.startsWith("http") -> {
                        val response: HttpResponse = httpClient.get(originalPath)
                        if (response.status.value == 200) {
                            bytes = response.readRawBytes()
                            ext = response.headers["Content-Type"]?.substringAfter("/")?.substringBefore(";") ?: "png"
                        }
                    }

                    originalPath.startsWith("data:image") -> {
                        val pureBase64 =
                            if (originalPath.contains(",")) originalPath.substringAfter(",") else originalPath
                        @OptIn(ExperimentalEncodingApi::class)
                        bytes = Base64.decode(pureBase64)
                        ext = originalPath.substringAfter("data:image/").substringBefore(";").ifBlank { "png" }
                    }

                    else -> {
                        bytes = readLocalFileBytes(originalPath)
                        ext = originalPath.substringAfterLast(".", "png")
                    }
                }

                if (bytes != null) {
                    val relPath = "$PROJECTS_DIR/$sanitizedName/assets/reference_$index.$ext"
                    val tFile = TemplateFile(relPath)
                    tFile.writeBytes(bytes)
                    archivedFiles.add(tFile)
                } else {
                    AppLogger.i("TemplateRepository", "⚠️ 无法读取资源内容，跳过归档: $originalPath")
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateRepository", "❌ 归档资源失败: $originalPath", e)
            }
        }
        return archivedFiles
    }

    /**
     * 保存项目模板到本地。
     * 遵循工业级分包复合文档规范（Figma/VSCode 模式）：
     * - UI Studio 工程：彻底将 template.json 重构为纯粹的「项目级公用配置文件 (UIStudioProjectConfig)」，
     *   仅保存工程元信息及轻量页面文件索引清单（pageIndex: id, name, fileRelPath），彻底剥离界面专属私有信息；
     *   各平级视图界面的独立宽高、背景色及完整图元树 100% 单独保存至 pages/{page.id}.json（原子刷盘）。
     * - 传统 Slots 模板工程：保持原有 ProjectState 结构兼容。
     */
    suspend fun saveTemplate(templateName: String, projectState: ProjectState) {
        val sanitizedName = sanitizeName(templateName)
        AppLogger.i("TemplateRepository", "💾 开始保存模板配置: $templateName")

        if (projectState.isUiStudioProject) {
            val pagesDir = "$PROJECTS_DIR/$sanitizedName/pages"
            // 1. 各平级页面完整数据独立写入 pages/{page.id}.json（原子刷盘）
            projectState.studioPages.forEach { page ->
                try {
                    val pageJson = looseJson.encodeToString(page)
                    fileStorage.saveToFileAtomic("$pagesDir/${page.id}.json", pageJson)
                } catch (e: Exception) {
                    AppLogger.e("TemplateRepository", "❌ 保存独立页面失败: ${page.id}", e)
                }
            }

            // 2. 主 template.json 写入纯净的项目级公用配置（仅含元数据与页面轻量文件索引，无任何页面私有信息）
            val projectConfig = UIStudioProjectConfig(
                projectId = projectState.projectId,
                projectName = sanitizedName,
                formatVersion = "2.0",
                createdAt = projectState.createdAt,
                updatedAt = org.gemini.ui.forge.getCurrentTimeMillis(),
                defaultCanvasWidth = projectState.studioPages.firstOrNull()?.width ?: 1080f,
                defaultCanvasHeight = projectState.studioPages.firstOrNull()?.height ?: 1920f,
                activeViewId = projectState.studioPages.firstOrNull()?.id,
                pageIndex = projectState.studioPages.map { page ->
                    UIStudioPageIndexItem(
                        id = page.id,
                        nameStr = page.nameStr,
                        fileRelPath = "pages/${page.id}.json"
                    )
                }
            )
            val jsonRelPath = "$PROJECTS_DIR/$sanitizedName/template.json"
            val configJson = looseJson.encodeToString(projectConfig)
            fileStorage.saveToFileAtomic(jsonRelPath, configJson)
            AppLogger.i("TemplateRepository", "✅ 项目公用配置 (template.json) 与独立页面已全部原子更新: $templateName")
        } else {
            // 传统 Slots 模板工程保持原有结构兼容
            val jsonRelPath = "$PROJECTS_DIR/$sanitizedName/template.json"
            val content = looseJson.encodeToString(projectState)
            fileStorage.saveToFileAtomic(jsonRelPath, content)
            AppLogger.i("TemplateRepository", "✅ Slots 模板 JSON 已原子更新: $templateName")
        }
    }

    /**
     * 单独保存指定页面到 pages/{page.id}.json（极速单页面原子落盘，无需重写主清单和其他页面）。
     */
    suspend fun saveStudioPage(templateName: String, page: UIStudioPage) {
        val sanitizedName = sanitizeName(templateName)
        val pageRelPath = "$PROJECTS_DIR/$sanitizedName/pages/${page.id}.json"
        val pageJson = looseJson.encodeToString(page)
        fileStorage.saveToFileAtomic(pageRelPath, pageJson)
        AppLogger.d("TemplateRepository", "📄 独立页面已原子更新: ${page.id}")
    }

    /**
     * 删除指定的独立页面文件。
     */
    suspend fun deleteStudioPage(templateName: String, pageId: String) {
        val sanitizedName = sanitizeName(templateName)
        val pageRelPath = "$PROJECTS_DIR/$sanitizedName/pages/$pageId.json"
        try {
            deleteLocalFile(fileStorage.getFilePath(pageRelPath))
            AppLogger.d("TemplateRepository", "🗑️ 独立页面文件已删除: $pageId")
        } catch (e: Exception) {
            AppLogger.w("TemplateRepository", "⚠️ 删除页面文件失败: $pageId: ${e.message}")
        }
    }

    /**
     * 保存工作区配置到本地。
     */
    suspend fun saveWorkspaceConfig(templateName: String, config: org.gemini.ui.forge.model.app.WorkspaceConfig) {
        val sanitizedName = templateName.replace(" ", "_")
        val jsonRelPath = "$PROJECTS_DIR/$sanitizedName/workspace.json"
        val content = looseJson.encodeToString(config)
        fileStorage.saveToFile(jsonRelPath, content)
    }

    /**
     * 加载工作区配置。
     */
    suspend fun loadWorkspaceConfig(templateName: String): org.gemini.ui.forge.model.app.WorkspaceConfig? {
        val sanitizedName = templateName.replace(" ", "_")
        val jsonRelPath = "$PROJECTS_DIR/$sanitizedName/workspace.json"
        val content = fileStorage.readFromFile(jsonRelPath) ?: return null
        return try {
            looseJson.decodeFromString<org.gemini.ui.forge.model.app.WorkspaceConfig>(content)
        } catch (e: Exception) {
            AppLogger.e("TemplateRepository", "❌ 解析 workspace.json 失败: $templateName", e)
            null
        }
    }

    /**
     * 递归清洗图元树中子节点的路径信息。
     *
     * @param blocks 待清洗的图元列表
     * @return 清洗完成后的图元列表
     */
    private fun cleanBlockPaths(blocks: List<UIBlock>): List<UIBlock> {
        return blocks.map { block ->
            block.copy(
                children = cleanBlockPaths(block.children)
            )
        }
    }

    /**
     * 删除指定的模板及其所有关联资源
     */
    suspend fun deleteTemplate(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        AppLogger.i("TemplateRepository", "🗑️ 正在删除模板目录: $sanitizedName")
        fileStorage.deleteDirectory("$PROJECTS_DIR/$sanitizedName")
    }

    /**
     * 手动将 Base64 图片数据解码并保存为指定图元的新资产图片。
     *
     * @param templateName 模板项目名称
     * @param blockId 目标图元唯一标识
     * @param base64Data 图片的 Base64 编码字符串（支持 Data URI 前缀）
     * @return 保存成功后的 [TemplateFile] 强类型文件句柄
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveResource(templateName: String, blockId: String, base64Data: String): TemplateFile {
        val sanitizedName = templateName.replace(" ", "_")
        val pureBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = Base64.decode(pureBase64)

        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val resourceName = "$PROJECTS_DIR/$sanitizedName/assets/$blockId/manual_$timestamp.png"
        val tFile = TemplateFile(resourceName)
        tFile.writeBytes(bytes)
        AppLogger.i("TemplateRepository", "✅ 手动保存资源成功: ${tFile.relativePath}")
        return tFile
    }

    /**
     * 规范化模板工程名称（消除首尾空格、将空格转换为下划线）
     */
    fun sanitizeName(name: String): String = name.trim().replace(" ", "_")

    /**
     * 组装完整的 UIStudioPage 列表：
     * 1. 若 pages/ 目录存在独立页面 JSON，按主清单顺序（及目录增量文件）组装完整 blocks；
     * 2. 若 pages/ 不存在（老工程），直接使用 template.json 中自带的 studioPages，并自动自愈拆分存盘至 pages/ 目录。
     */
    private suspend fun assembleStudioPages(sanitizedName: String, projectState: ProjectState): ProjectState {
        val pagesDir = "$PROJECTS_DIR/$sanitizedName/pages"
        val pageFiles = try {
            if (fileStorage.exists(pagesDir)) fileStorage.listFiles(pagesDir).filter { it.endsWith(".json") } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (pageFiles.isNotEmpty()) {
            val loadedPagesMap = mutableMapOf<String, UIStudioPage>()
            for (fileName in pageFiles) {
                try {
                    val content = fileStorage.readFromFile("$pagesDir/$fileName")
                    if (!content.isNullOrBlank()) {
                        val page = looseJson.decodeFromString<UIStudioPage>(content)
                        loadedPagesMap[page.id] = page
                    }
                } catch (e: Exception) {
                    AppLogger.e("TemplateRepository", "❌ 读取独立页面失败: $fileName", e)
                }
            }

            // 优先按照主清单的顺序重组，并补齐目录中存在但清单遗漏的页面
            val assembledPages = mutableListOf<UIStudioPage>()
            val visitedIds = mutableSetOf<String>()
            for (summary in projectState.studioPages) {
                val fullPage = loadedPagesMap[summary.id] ?: summary
                assembledPages.add(fullPage)
                visitedIds.add(summary.id)
            }
            for ((id, page) in loadedPagesMap) {
                if (!visitedIds.contains(id)) {
                    assembledPages.add(page)
                }
            }
            return projectState.copy(studioPages = assembledPages)
        } else if (projectState.studioPages.isNotEmpty()) {
            // 老工程自愈升级：老 template.json 中已带有完整 blocks，自动无感拆分写入 pages/ 目录
            AppLogger.i("TemplateRepository", "🔄 检测到存量工程，正在自动拆分为独立页面存储: $sanitizedName")
            for (page in projectState.studioPages) {
                try {
                    val pageJson = looseJson.encodeToString(page)
                    fileStorage.saveToFileAtomic("$pagesDir/${page.id}.json", pageJson)
                } catch (e: Exception) {
                    AppLogger.w("TemplateRepository", "⚠️ 老工程拆分保存失败: ${page.id}: ${e.message}")
                }
            }
        }
        return projectState
    }

    /**
     * 单点统一解析 template.json 内容：优先按照 UIStudioProjectConfig 纯净公用配置解码并组装独立页面；
     * 优雅向后兼容存量旧版 ProjectState 并在读取时无感拆分。
     */
    private suspend fun parseTemplateJsonContent(dirName: String, content: String): ProjectState? {
        val sanitizedName = sanitizeName(dirName)
        // 1. 优先尝试解析为全新的纯净项目级公用配置 (UIStudioProjectConfig)
        if (content.contains("\"formatVersion\"") && content.contains("\"pageIndex\"")) {
            try {
                val config = looseJson.decodeFromString<UIStudioProjectConfig>(content)
                val assembledPages = assembleStudioPagesFromConfig(sanitizedName, config)
                val activeId = config.activeViewId ?: assembledPages.firstOrNull()?.id ?: "MainPage"
                val runtimeState = ProjectState(
                    projectId = config.projectId,
                    pages = emptyList(),
                    studioPages = assembledPages,
                    createdAt = config.createdAt
                )
                return validateAndCleanMissingReferencesInMemory(runtimeState)
            } catch (e: Exception) {
                AppLogger.w("TemplateRepository", "⚠️ 尝试按 UIStudioProjectConfig 解析失败，将降级尝试传统格式: $dirName: ${e.message}")
            }
        }

        // 2. 存量老工程兼容：解码旧版 ProjectState 并自动组装/无感拆分
        return try {
            val state = looseJson.decodeFromString<ProjectState>(content)
            val assembled = assembleStudioPages(sanitizedName, state)
            validateAndCleanMissingReferencesInMemory(assembled)
        } catch (e: Exception) {
            AppLogger.e("TemplateRepository", "❌ 解析模板 JSON 失败: $dirName", e)
            null
        }
    }

    /**
     * 从 UIStudioProjectConfig 的页面索引清单中读取 pages/ 目录下的独立页面 JSON 组装完整 studioPages
     */
    private suspend fun assembleStudioPagesFromConfig(sanitizedName: String, config: UIStudioProjectConfig): List<UIStudioPage> {
        val pagesDir = "$PROJECTS_DIR/$sanitizedName/pages"
        val loadedPagesMap = mutableMapOf<String, UIStudioPage>()

        val pageFiles = try {
            if (fileStorage.exists(pagesDir)) fileStorage.listFiles(pagesDir).filter { it.endsWith(".json") } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        for (fileName in pageFiles) {
            try {
                val content = fileStorage.readFromFile("$pagesDir/$fileName")
                if (!content.isNullOrBlank()) {
                    val page = looseJson.decodeFromString<UIStudioPage>(content)
                    loadedPagesMap[page.id] = page
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateRepository", "❌ 读取独立页面失败: $fileName", e)
            }
        }

        val result = mutableListOf<UIStudioPage>()
        val visitedIds = mutableSetOf<String>()

        // 按照配置中的 pageIndex 顺序加载
        for (indexItem in config.pageIndex) {
            val fullPage = loadedPagesMap[indexItem.id]
            if (fullPage != null) {
                result.add(fullPage)
                visitedIds.add(indexItem.id)
            } else {
                val fallback = UIStudioPage(id = indexItem.id, nameStr = indexItem.nameStr)
                result.add(fallback)
                visitedIds.add(indexItem.id)
            }
        }

        // 自动收录 pages/ 目录下存在但清单未记录的孤儿页面文件（磁盘物理双向同步与自愈）
        for ((id, page) in loadedPagesMap) {
            if (!visitedIds.contains(id)) {
                result.add(page)
            }
        }

        if (result.isEmpty()) {
            result.add(UIStudioPage(id = "MainPage", nameStr = "MainPage", width = config.defaultCanvasWidth, height = config.defaultCanvasHeight))
        }

        return result
    }

    /**
     * 根据名称直接精确定位读取单个模板（支持原始下划线名与带空格名称，无需扫描全部目录）
     */
    suspend fun getTemplateByName(name: String): ProjectState? {
        val sanitizedName = sanitizeName(name)
        val directPath = "$PROJECTS_DIR/$sanitizedName/template.json"
        val directContent = fileStorage.readFromFile(directPath)
        if (directContent != null) {
            val state = parseTemplateJsonContent(sanitizedName, directContent)
            if (state != null) return state
        }

        // 兜底扫描：若直接定位未命中，在已有工程列表中做不区分大小写与下划线/空格的宽容匹配
        val all = getTemplates()
        return all.firstOrNull { (title, projState) ->
            sanitizeName(title).equals(sanitizedName, ignoreCase = true) ||
            sanitizeName(projState.projectId).equals(sanitizedName, ignoreCase = true)
        }?.second
    }

    /**
     * 查找模板并返回其标准保存标识名与工程状态（彻底解决空格与下划线不匹配导致的查找失败）
     */
    suspend fun findTemplatePair(projectName: String): Pair<String, ProjectState>? {
        val sanitized = sanitizeName(projectName)
        val direct = getTemplateByName(projectName)
        if (direct != null) {
            return sanitized to direct
        }
        val all = getTemplates()
        val match = all.firstOrNull { (title, projState) ->
            sanitizeName(title).equals(sanitized, ignoreCase = true) ||
            sanitizeName(projState.projectId).equals(sanitized, ignoreCase = true)
        } ?: return null
        return sanitizeName(match.first) to match.second
    }

    /**
     * 扫描本地已保存的所有模板项目，并返回名称与工程状态的配对列表。
     *
     * 内部自动执行参考图物理有效性校验，确保丢失文件的模块在内存中置空而不会崩溃。
     *
     * @return 包含模板标题与 [ProjectState] 的二元组列表
     */
    suspend fun getTemplates(): List<Pair<String, ProjectState>> {
        AppLogger.d("TemplateRepository", "🔍 正在扫描本地模板列表...")
        val dirs = fileStorage.listDirectories(PROJECTS_DIR)
        return dirs.mapNotNull { dirName ->
            val relativePath = "$PROJECTS_DIR/$dirName"
            val content = fileStorage.readFromFile("$relativePath/template.json")
            if (content != null) {
                val state = parseTemplateJsonContent(dirName, content)
                if (state != null) {
                    val title = dirName.replace("_", " ")
                    AppLogger.d("TemplateRepository", "📖 已加载模板并完成内存参考图校验: $title")
                    title to state
                } else null
            } else null
        }
    }

    /**
     * 内存安全校验与清洗：检查所有 blocks 的 referenceImage 物理文件是否存在，
     * 若文件丢失则仅在内存运行时置为 null（不写磁盘，保留原文件）。
     */
    private suspend fun validateAndCleanMissingReferencesInMemory(projectState: ProjectState): ProjectState {
        val updatedPages = projectState.pages.map { page ->
            suspend fun cleanBlock(block: UIBlock): UIBlock {
                val ref = block.referenceImage
                val validRef = if (ref != null && ref.relativePath.isNotBlank()) {
                    if (isFileExists(ref.getAbsolutePath())) ref else null
                } else null

                val cleanedChildren = block.children.map { cleanBlock(it) }
                return block.copy(referenceImage = validRef, children = cleanedChildren)
            }

            val cleanedBlocks = page.blocks.map { cleanBlock(it) }
            page.copy(blocks = cleanedBlocks.bindParents())
        }
        return projectState.copy(pages = updatedPages)
    }

    /**
     * 更新本地持久化数据根目录路径。
     *
     * @param newPath 新的数据存储绝对物理路径
     * @return 迁移/切换成功返回 true，失败返回 false
     */
    suspend fun updateStorageDir(newPath: String): Boolean {
        return fileStorage.updateDataDir(newPath)
    }

    /**
     * 获取当前有效的数据存储根目录绝对路径。
     *
     * @return 当前数据目录绝对路径
     */
    suspend fun getDataDir(): String {
        return fileStorage.getDataDir()
    }

    /**
     * 在操作系统的原生文件资源管理器中打开指定模板工程所在的物理目录。
     *
     * @param module 目标 UI 模块或模板工程信息
     */
    suspend fun openFileDir(module: UIModule) {
        val sanitizedName = module.id.replace(" ", "_")
        val path = fileStorage.getDataDir() + "/$PROJECTS_DIR/$sanitizedName"
        AppLogger.i("TemplateRepository", "🗑️ 正在打开模板目录: $path")
        getPlatform().openInFileExplorer(path)
    }
}
