package org.gemini.ui.forge.data.repository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.service.LocalFileStorage
import org.gemini.ui.forge.utils.*

/**
 * 模板持久化仓库类，负责项目模板及关联资源的保存、读取和删除
 * @property fileStorage 本地文件存储抽象层
 */
class TemplateRepository(
    val fileStorage: LocalFileStorage = LocalFileStorage(),
    private val httpClient: HttpClient = NetworkClient.shared
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val PROJECTS_DIR = "templates"

    /**
     * 将生成的资源保存到模块专用的资产目录下
     * 路径：templates/$templateName/assets/$blockId/
     */
    suspend fun saveBlockResource(templateName: String, blockId: String, fileNamePrefix: String, bytes: ByteArray): String {
        val sanitizedName = templateName.replace(" ", "_")
        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        // 存储规范：templates/项目名/assets/模块名/前缀_时间戳.jpg
        val fileName = "$PROJECTS_DIR/$sanitizedName/assets/$blockId/${fileNamePrefix}_$timestamp.jpg"
        val savedPath = fileStorage.saveBytesToFile(fileName, bytes)
        AppLogger.i("TemplateRepository", "✅ Block 资源已保存: $savedPath (${bytes.size / 1024} KB)")
        return savedPath
    }

    /**
     * 将临时图片（如分析时的局部图）保存到模板的 cache 目录下
     */
    suspend fun saveCacheImage(templateName: String, fileNamePrefix: String, bytes: ByteArray): String {
        val sanitizedName = templateName.replace(" ", "_")
        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val fileName = "$PROJECTS_DIR/$sanitizedName/cache/${fileNamePrefix}_$timestamp.jpg"
        val savedPath = fileStorage.saveBytesToFile(fileName, bytes)
        AppLogger.d("TemplateRepository", "📦 缓存图片已保存: $savedPath (${bytes.size / 1024} KB)")
        return savedPath
    }

    /**
     * 清理指定模板下超过 24 小时的缓存文件
     */
    suspend fun cleanupExpiredCache(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        val cacheDirName = "$PROJECTS_DIR/$sanitizedName/cache"
        AppLogger.i("TemplateRepository", "🧹 开始清理过期缓存: $sanitizedName")
        try {
            val rootDir = fileStorage.getDataDir()
            val fullCacheDir = "$rootDir/$cacheDirName"
            val files = listFilesInLocalDirectory(fullCacheDir)
            val now = org.gemini.ui.forge.getCurrentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L

            var count = 0
            files.forEach { filePath ->
                val lastMod = getLocalFileLastModified(filePath)
                if (now - lastMod > oneDay) {
                    deleteLocalFile(filePath)
                    count++
                    AppLogger.d("TemplateRepository", "🗑️ 已清理过期缓存文件: $filePath")
                }
            }
            AppLogger.i("TemplateRepository", "✅ 缓存清理完成，共移除 $count 个文件")
        } catch (e: Exception) {
            AppLogger.e("TemplateRepository", "❌ 清理缓存异常", e)
        }
    }

    /**
     * 保存项目模板到本地。
     */
    suspend fun saveTemplate(templateName: String, projectState: ProjectState) {
        val sanitizedName = templateName.replace(" ", "_")
        AppLogger.i("TemplateRepository", "💾 开始保存模板: $templateName")
        
        val pathMapping = mutableMapOf<String, String>()
        val updatedReferenceImages = mutableListOf<String>()

        projectState.referenceImages.forEachIndexed { index, originalPath ->
            try {
                var bytes: ByteArray? = null
                var ext = "png"

                when {
                    originalPath.startsWith("http") -> {
                        AppLogger.d("TemplateRepository", "🌐 正在下载远程参考图: $originalPath")
                        val response = httpClient.get(originalPath)
                        if (response.status.value == 200) {
                            bytes = response.readRawBytes()
                            ext = response.headers["Content-Type"]?.substringAfter("/")?.substringBefore(";") ?: "png"
                        }
                    }
                    originalPath.startsWith("data:image") -> {
                        AppLogger.d("TemplateRepository", "🖼️ 正在解码 Base64 参考图")
                        val pureBase64 = if (originalPath.contains(",")) originalPath.substringAfter(",") else originalPath
                        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                        bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)
                        ext = originalPath.substringAfter("data:image/").substringBefore(";").ifBlank { "png" }
                    }
                    else -> {
                        bytes = readLocalFileBytes(originalPath)
                        ext = originalPath.substringAfterLast(".", "png")
                    }
                }

                if (bytes != null) {
                    val archivedFileName = "$PROJECTS_DIR/$sanitizedName/assets/reference_$index.$ext"
                    val savedPath = fileStorage.saveBytesToFile(archivedFileName, bytes)
                    updatedReferenceImages.add(savedPath)
                    pathMapping[originalPath] = savedPath
                    AppLogger.d("TemplateRepository", "📑 参考图归档成功: $savedPath")
                } else if (originalPath.contains(sanitizedName) && originalPath.contains("assets")) {
                    updatedReferenceImages.add(originalPath)
                    pathMapping[originalPath] = originalPath
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateRepository", "❌ 归档参考图失败: $originalPath", e)
            }
        }

        val updatedPages = projectState.pages.map { page ->
            val mappedPath = pathMapping[page.sourceImageUri] ?: page.sourceImageUri
            page.copy(sourceImageUri = mappedPath)
        }

        val updatedState = projectState.copy(
            referenceImages = updatedReferenceImages,
            pages = updatedPages
        )

        val fileName = "$PROJECTS_DIR/$sanitizedName/template.json"
        val content = json.encodeToString(updatedState)
        fileStorage.saveToFile(fileName, content)
        AppLogger.i("TemplateRepository", "✅ 模板 JSON 已更新: $fileName (${content.length / 1024} KB)")
    }

    /**
     * 删除指定的模板及其所有关联资源
     */
    suspend fun deleteTemplate(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        AppLogger.i("TemplateRepository", "🗑️ 正在删除模板目录: $sanitizedName")
        fileStorage.deleteDirectory("$PROJECTS_DIR/$sanitizedName")
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun saveResource(templateName: String, blockId: String, base64Data: String): String {
        val sanitizedName = templateName.replace(" ", "_")
        val pureBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)

        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val resourceName = "$PROJECTS_DIR/$sanitizedName/assets/$blockId/manual_$timestamp.png"
        val savedPath = fileStorage.saveBytesToFile(resourceName, bytes)
        AppLogger.i("TemplateRepository", "✅ 手动保存资源成功: $savedPath")
        return savedPath
    }

    suspend fun getTemplates(): List<Pair<String, ProjectState>> {
        AppLogger.d("TemplateRepository", "🔍 正在扫描本地模板列表...")
        // 关键点：扫描 templates 子目录下的项目
        val dirs = fileStorage.listDirectories(PROJECTS_DIR)
        return dirs.mapNotNull { dirName ->
            val relativePath = "$PROJECTS_DIR/$dirName"
            val content = fileStorage.readFromFile("$relativePath/template.json")
            if (content != null) {
                try {
                    val state = json.decodeFromString<ProjectState>(content)
                    val title = dirName.replace("_", " ")
                    AppLogger.d("TemplateRepository", "📖 已加载模板: $title")
                    title to state
                } catch (e: Exception) {
                    AppLogger.e("TemplateRepository", "❌ 解析模板 JSON 失败: $dirName", e)
                    null
                }
            } else null
        }
    }

    suspend fun updateStorageDir(newPath: String): Boolean {
        return fileStorage.updateDataDir(newPath)
    }

    suspend fun getDataDir(): String {
        return fileStorage.getDataDir()
    }
}
