package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.domain.ProjectState

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
     * 保存项目模板到本地。
     * 同时也负责将所有参与分析的参考图物理归档（下载/解码/移动）到模板专属的 assets 目录中。
     * 关键优化：会自动更新 UIPage 中的 sourceImageUri 指向归档后的安全路径。
     */
    suspend fun saveTemplate(templateName: String, projectState: ProjectState) {
        val sanitizedName = templateName.replace(" ", "_")
        
        // 建立“原始路径 -> 归档路径”的映射表，用于修正 Page 引用
        val pathMapping = mutableMapOf<String, String>()
        val updatedReferenceImages = mutableListOf<String>()

        // 1. 全量归档参考图
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
                        bytes = org.gemini.ui.forge.utils.readLocalFileBytes(originalPath)
                        ext = originalPath.substringAfterLast(".", "png")
                    }
                }

                if (bytes != null) {
                    val archivedFileName = "$sanitizedName/assets/reference_$index.$ext"
                    val savedPath = fileStorage.saveBytesToFile(archivedFileName, bytes)
                    updatedReferenceImages.add(savedPath)
                    // 记录映射关系
                    pathMapping[originalPath] = savedPath
                } else if (originalPath.contains(sanitizedName) && originalPath.contains("assets")) {
                    // 如果路径本身已经是该模板目录下的资产路径，则直接保留，无需重复归档
                    updatedReferenceImages.add(originalPath)
                    pathMapping[originalPath] = originalPath
                }
            } catch (e: Exception) {
                org.gemini.ui.forge.utils.AppLogger.e("TemplateRepository", "归档参考图失败: $originalPath", e)
            }
        }

        // 2. 深度修正：更新所有 Page 里的 sourceImageUri 指向归档后的本地路径
        val updatedPages = projectState.pages.map { page ->
            val mappedPath = pathMapping[page.sourceImageUri] ?: page.sourceImageUri
            page.copy(sourceImageUri = mappedPath)
        }

        // 3. 更新 ProjectState 状态
        val updatedState = projectState.copy(
            referenceImages = updatedReferenceImages,
            pages = updatedPages
        )

        // 4. 持久化 JSON
        val fileName = "$sanitizedName/template.json"
        val content = json.encodeToString(updatedState)
        fileStorage.saveToFile(fileName, content)
    }

    /**
     * 删除指定的模板及其所有关联资源
     * @param templateName 模板名称
     */
    suspend fun deleteTemplate(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        fileStorage.deleteDirectory(sanitizedName)
    }

    /**
     * 将 AI 生成的图片资源（Base64）保存为模板文件夹内的物理文件，并返回其本地绝对路径
     * @param templateName 模板名称
     * @param blockId 资源所属的 UI 组件 ID
     * @param base64Data 图片的 Base64 数据
     * @return 返回保存后的文件绝对路径
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun saveResource(templateName: String, blockId: String, base64Data: String): String {
        val sanitizedName = templateName.replace(" ", "_")
        val pureBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)

        // 资源文件名格式: 模板目录/组件ID_随机数.png
        val resourceName = "$sanitizedName/${blockId}_${kotlin.random.Random.nextInt(1000000)}.png"

        return fileStorage.saveBytesToFile(resourceName, bytes)
    }

    /**
     * 获取本地已存储的所有模板列表
     * @return 返回模板名称与项目状态的键值对列表
     */
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

    /**
     * 更新模板存储目录并迁移文件
     */
    suspend fun updateStorageDir(newPath: String): Boolean {
        return fileStorage.updateDataDir(newPath)
    }

    /**
     * 获取当前模板存储目录
     */
    suspend fun getDataDir(): String {
        return fileStorage.getDataDir()
    }
}
