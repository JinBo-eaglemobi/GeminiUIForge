package org.gemini.ui.forge.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.writeBytesInternal
import org.gemini.ui.forge.data.createParentDirsInternal
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.utils.ResourceBindingValidator
import org.gemini.ui.forge.utils.looseJson

@Serializable
data class ExportedNode(
    val id: String,
    val type: String,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val description: String,
    val imagePath: String?,
    val children: List<ExportedNode> = emptyList()
)

@Serializable
data class ExportedPage(
    val id: String,
    val name: String,
    val width: Float,
    val height: Float,
    val blocks: List<ExportedNode>
)

@Serializable
data class ExportedProject(
    val projectName: String,
    val pages: List<ExportedPage>
)

/**
 * 负责将项目编译并导出到指定目录的服务。
 */
class CompilerService(private val fileStorage: LocalFileStorage) {

    private val json = Json { prettyPrint = true }

    /**
     * 将项目状态编译并导出到指定输出目录
     */
    suspend fun compileProject(
        projectName: String,
        projectState: ProjectState,
        rootDir: String,
        outputDir: String,
        resourceConfigPath: String? = null
    ): Boolean {
        return try {
            // 1. 如果配置了资源绑定路径，先加载并校验全量资源的有效性
            val configPath = resourceConfigPath
            if (!configPath.isNullOrBlank()) {
                val configBytes = org.gemini.ui.forge.data.readBytesInternal(configPath)
                if (configBytes == null) {
                    AppLogger.e("Compiler", "导出失败：未能读取资源绑定配置文件 $configPath")
                    return false
                }
                val configContent = configBytes.decodeToString()
                val configData = try {
                    val parsed = looseJson.decodeFromString<Map<String, Map<String, ResourceItem>>>(configContent)
                    parsed.mapValues { it.value.values.toList() }
                } catch (e: Exception) {
                    AppLogger.e("Compiler", "导出失败：解析资源绑定配置文件异常", e)
                    return false
                }

                // 执行失效分析弱校验
                val invalidReports = ResourceBindingValidator.validateProjectBindings(projectState, configData)
                if (invalidReports.isNotEmpty()) {
                    AppLogger.e("Compiler", "【导出拦截】导出失败：检测到 [${invalidReports.size}] 处模块的资源绑定已失效。必须在属性面板中修正或清除所有警告才可以进行导出！")
                    invalidReports.forEach { report ->
                        AppLogger.e(
                            "Compiler",
                            "-> 失效详情：页面ID [${report.pageId}] (${report.pageName}) | 模块ID [${report.blockId}] (${report.blockName}) | 断链层级 [${report.invalidIndex}] (找不到 Key: \"${report.invalidKey}\") | 完整绑定路径: ${report.fullPath.joinToString(" -> ")}"
                        )
                    }
                    return false
                }
            }

            val normalizedRoot = rootDir.replace("\\", "/").removeSuffix("/")
            val normalizedOutput = outputDir.replace("\\", "/").removePrefix("/").removeSuffix("/")
            
            // 目标资源的完整绝对路径
            val targetDirPath = if (normalizedOutput.isNotEmpty()) {
                "$normalizedRoot/$normalizedOutput"
            } else {
                normalizedRoot
            }

            // 在目标目录中需要生成的 assets 文件夹路径，用于存放图片
            val targetAssetsPath = "$targetDirPath/assets"

            // 确保目录存在
            createParentDirsInternal("$targetAssetsPath/dummy.txt")

            val exportedPages = projectState.pages.map { page ->
                val exportedBlocks = page.blocks.map { block ->
                    exportBlock(block, targetAssetsPath)
                }
                ExportedPage(
                    id = page.id,
                    name = page.nameStr,
                    width = page.width,
                    height = page.height,
                    blocks = exportedBlocks
                )
            }

            val exportedProject = ExportedProject(
                projectName = projectName,
                pages = exportedPages
            )

            val jsonContent = json.encodeToString(ExportedProject.serializer(), exportedProject)
            
            // 将配置文件写入输出目录
            writeBytesInternal("$targetDirPath/GameConfig.json", jsonContent.encodeToByteArray())
            
            AppLogger.d("Compiler", "成功导出项目到 $targetDirPath")
            true
        } catch (e: Exception) {
            AppLogger.e("Compiler", "编译导出失败", e)
            false
        }
    }

    private suspend fun exportBlock(block: UIBlock, targetAssetsPath: String): ExportedNode {
        var exportedImagePath: String? = null

        // 检查是否有配置的图片（包括 AI 生成或本地加载的图片）
        block.currentImageUri?.let { templateFile ->
            try {
                // 读取原始文件
                val imageBytes = templateFile.readBytes()
                
                if (imageBytes != null) {
                    val fileName = "${block.id}.png"
                    val destFilePath = "$targetAssetsPath/$fileName"
                    
                    // 写入目标文件夹的绝对路径
                    writeBytesInternal(destFilePath, imageBytes)
                    
                    // 返回相对于 GameConfig.json 的路径 (例如：assets/xxx.png)
                    exportedImagePath = "assets/$fileName"
                }
            } catch (e: Exception) {
                AppLogger.e("Compiler", "复制图片资源失败: ${templateFile.getAbsolutePath()}", e)
            }
        }

        // 递归处理子节点
        val exportedChildren = block.children.map { exportBlock(it, targetAssetsPath) }

        return ExportedNode(
            id = block.id,
            type = block.type.name,
            name = block.type.name,
            x = block.bounds.left,
            y = block.bounds.top,
            width = block.bounds.width,
            height = block.bounds.height,
            description = block.userPromptZh, // 将中文描述作为功能描述
            imagePath = exportedImagePath,
            children = exportedChildren
        )
    }
}
