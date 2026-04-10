package org.gemini.ui.forge.data.repository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.remote.NetworkClient
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIPage
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

    /**
     * 将临时图片（如 AI 候选图或重塑裁剪图）保存到模板的 cache 目录下
     * @return 返回保存后的本地绝对路径
     */
    suspend fun saveCacheImage(templateName: String, fileNamePrefix: String, bytes: ByteArray): String {
        val sanitizedName = templateName.replace(" ", "_")
        val timestamp = org.gemini.ui.forge.getCurrentTimeMillis()
        val fileName = "$sanitizedName/cache/${fileNamePrefix}_$timestamp.jpg"
        return fileStorage.saveBytesToFile(fileName, bytes)
    }

    /**
     * 清理指定模板下超过 24 小时的缓存文件
     */
    suspend fun cleanupExpiredCache(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        val cacheDirName = "$sanitizedName/cache"
        try {
            val rootDir = fileStorage.getDataDir()
            val fullCacheDir = "$rootDir/$cacheDirName"
            val files = listFilesInLocalDirectory(fullCacheDir)
            val now = org.gemini.ui.forge.getCurrentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L

            files.forEach { filePath ->
                val lastMod = getLocalFileLastModified(filePath)
                if (now - lastMod > oneDay) {
                    deleteLocalFile(filePath)
                    AppLogger.d("TemplateRepository", "已清理过期缓存文件: $filePath")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("TemplateRepository", "清理缓存异常", e)
        }
    }

    /**
     * 保存项目模板到本地。
     */
    suspend fun saveTemplate(templateName: String, projectState: ProjectState) {
        val sanitizedName = templateName.replace(" ", "_")
        
        val pathMapping = mutableMapOf<String, String>()
        val updatedReferenceImages = mutableListOf<String>()

        projectState.referenceImages.forEachIndexed { index, originalPath ->
            try {
                var bytes: ByteArray? = null
                var ext = "png"

                when {
                    originalPath.startsWith("http") -> {
                        val response = httpClient.get(originalPath)
                        if (response.status.value == 200) {
                            bytes = response.readRawBytes()
                            ext = response.headers["Content-Type"]?.substringAfter("/")?.substringBefore(";") ?: "png"
                        }
                    }
                    originalPath.startsWith("data:image") -> {
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
                    val archivedFileName = "$sanitizedName/assets/reference_$index.$ext"
                    val savedPath = fileStorage.saveBytesToFile(archivedFileName, bytes)
                    updatedReferenceImages.add(savedPath)
                    pathMapping[originalPath] = savedPath
                } else if (originalPath.contains(sanitizedName) && originalPath.contains("assets")) {
                    updatedReferenceImages.add(originalPath)
                    pathMapping[originalPath] = originalPath
                }
            } catch (e: Exception) {
                AppLogger.e("TemplateRepository", "归档参考图失败: $originalPath", e)
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

        val fileName = "$sanitizedName/template.json"
        val content = json.encodeToString(updatedState)
        fileStorage.saveToFile(fileName, content)
    }

    /**
     * 删除指定的模板及其所有关联资源
     */
    suspend fun deleteTemplate(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        fileStorage.deleteDirectory(sanitizedName)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun saveResource(templateName: String, blockId: String, base64Data: String): String {
        val sanitizedName = templateName.replace(" ", "_")
        val pureBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)

        val resourceName = "$sanitizedName/${blockId}_${kotlin.random.Random.nextInt(1000000)}.png"
        return fileStorage.saveBytesToFile(resourceName, bytes)
    }

    suspend fun getTemplates(): List<Pair<String, ProjectState>> {
        val dirs = fileStorage.listDirectories()
        return dirs.mapNotNull { dirName ->
            val content = fileStorage.readFromFile("$dirName/template.json")
            if (content != null) {
                try {
                    val state = json.decodeFromString<ProjectState>(content)
                    val title = dirName.replace("_", " ")
                    title to state
                } catch (e: Exception) {
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
