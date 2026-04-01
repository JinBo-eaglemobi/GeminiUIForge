package org.gemini.ui.forge.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.domain.ProjectState

class TemplateRepository(val fileStorage: LocalFileStorage = LocalFileStorage()) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveTemplate(templateName: String, projectState: ProjectState) {
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

    fun deleteTemplate(templateName: String) {
        val sanitizedName = templateName.replace(" ", "_")
        fileStorage.deleteDirectory(sanitizedName)
    }

    /**
     * 将选中的候选图（Base64）保存为模板文件夹内的物理文件，并返回本地绝对路径
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun saveResource(templateName: String, blockId: String, base64Data: String): String {
        val sanitizedName = templateName.replace(" ", "_")
        val pureBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
        val bytes = kotlin.io.encoding.Base64.Default.decode(pureBase64)

        // 资源文件名格式: 模板目录/组件ID_时间戳.png
        val resourceName = "$sanitizedName/${blockId}_${kotlin.random.Random.nextInt(1000000)}.png"

        return fileStorage.saveBytesToFile(resourceName, bytes)
    }

    fun getTemplates(): List<Pair<String, ProjectState>> {
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
}