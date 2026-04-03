package org.gemini.ui.forge.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.domain.ProjectState

/**
 * 模板持久化仓库类，负责项目模板及关联资源的保存、读取和删除
 * @property fileStorage 本地文件存储抽象层
 */
class TemplateRepository(val fileStorage: LocalFileStorage = LocalFileStorage()) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * 保存项目模板到本地
     * @param templateName 模板/项目名称
     * @param projectState 项目的状态数据
     */
    suspend fun saveTemplate(templateName: String, projectState: ProjectState) {
        val sanitizedName = templateName.replace(" ", "_")

        var updatedState = projectState
        val originalCover = projectState.coverImage

        // 如果 coverImage 存在且是一个本地路径（不是 http 也不是 data:base64），将其复制到模板专属文件夹内
        if (originalCover != null && !originalCover.startsWith("data:") && !originalCover.startsWith("http")) {
            val bytes = org.gemini.ui.forge.utils.readLocalFileBytes(originalCover)
            if (bytes != null) {
                val ext = originalCover.substringAfterLast(".", "png")
                val coverFileName = "$sanitizedName/cover.$ext"
                val savedPath = fileStorage.saveBytesToFile(coverFileName, bytes)
                updatedState = updatedState.copy(coverImage = savedPath)
            }
        }

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
