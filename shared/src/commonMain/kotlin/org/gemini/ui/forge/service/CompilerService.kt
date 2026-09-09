package org.gemini.ui.forge.service

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.createParentDirsInternal
import org.gemini.ui.forge.data.readBytesInternal
import org.gemini.ui.forge.data.writeBytesInternal
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.ResourceBindingValidator
import org.gemini.ui.forge.utils.looseJson
import kotlin.math.abs

@Serializable
data class ExportedNode(
    val id: String,
    val type: String,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val description: String,
    val imagePath: String?,
    val children: List<ExportedNode> = emptyList()
)

@Serializable
data class ExportedPage(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
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
class CompilerService {

    /**
     * 将项目状态编译并导出到指定输出目录。
     *
     * 该过程具体包含以下关键执行逻辑：
     * 1. **断链依赖强校验**：如果传入了 [resourceConfigPath]，本方法会自动加载该资源表并分析当前所有模块中是否
     *    存在断链/失效的键值绑定。一旦检测到任何非法绑定，将进行强拦截，打印详细的失效定位日志并返回 `false`。
     * 2. **数据标准化转换**：将 UI 页面和树形嵌套模块（[UIBlock]）递归扁平解析为通用的 [ExportedNode]，最终序列化
     *    为标准的 `GameConfig.json` 导出。
     * 3. **媒体资源同步与隔离**：自动抽取出所有设计中引用的图片（包括 AI 生图结果和本地资源），重命名拷贝至目标路径
     *    的 `assets` 目录下，并修正节点的相对引用路径。
     *
     * @param projectName 项目名称（导出标识）
     * @param projectState 当前设计的全量 UI 项目页面状态
     * @param rootDir 本地预览/运行环境的绝对根目录
     * @param outputDir 导出资源（切图、图片资源等）存放的子目录名称（相对于 rootDir，相对路径，例如 "assets"）
     * @param resourceConfigPath 可选的静态资源绑定元数据 JSON 文件路径（若存在，则触发强拦截校验）
     * @param obfuscateAssets 是否开启导出资源文件名混淆（默认开启）
     * @return 编译、解析和文件同步全部成功则返回 `true`；若由于非法绑定被强拦截或写入异常则返回 `false`
     */
    suspend fun compileProject(
        projectName: String,
        projectState: ProjectState,
        rootDir: String,
        outputDir: String,
        resourceConfigPath: String? = null,
        obfuscateAssets: Boolean = true
    ): Boolean {
        if (rootDir.isBlank()) {
            AppLogger.e("Compiler", "导出失败：未配置运行环境根目录 (rootDir)")
            return false
        }
        if (outputDir.isBlank()) {
            AppLogger.e("Compiler", "导出失败：未配置资源保存目录 (outputDir)")
            return false
        }
        return try {
            // 1. 如果配置了资源绑定路径，先加载并校验全量资源的有效性
            val configPath = resourceConfigPath
            if (!configPath.isNullOrBlank()) {
                val configBytes = readBytesInternal(configPath)
                if (configBytes == null) {
                    AppLogger.e("Compiler", "导出失败：未能读取资源绑定配置文件 $configPath")
                    return false
                }
                val configContent = configBytes.decodeToString()
                val configData = try {
                    looseJson.decodeFromString<List<ResourceItem>>(configContent)
                } catch (e: Exception) {
                    AppLogger.e("Compiler", "导出失败：解析资源绑定配置文件异常", e)
                    return false
                }

                // 执行失效 analysis 弱校验
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
            val targetAssetsPath = if (normalizedOutput.isNotEmpty()) {
                "$normalizedRoot/$normalizedOutput"
            } else {
                normalizedRoot
            }

            // 确保目录存在
            createParentDirsInternal("$targetAssetsPath/dummy.txt")

            val exportedPages = projectState.pages.map { page ->
                val exportedBlocks = page.blocks.map { block ->
                    exportBlock(block, targetAssetsPath, normalizedOutput, obfuscateAssets)
                }
                ExportedPage(
                    id = page.id,
                    name = page.nameStr,
                    width = page.width.toInt(),
                    height = page.height.toInt(),
                    blocks = exportedBlocks
                )
            }

            val exportedProject = ExportedProject(
                projectName = projectName,
                pages = exportedPages
            )

            val jsonContent = looseJson.encodeToString(ExportedProject.serializer(), exportedProject)
            
            // 将配置文件写入输出目录
            writeBytesInternal("$targetAssetsPath/GameConfig.json", jsonContent.encodeToByteArray())
            
            AppLogger.d("Compiler", "成功导出项目到 $targetAssetsPath")
            true
        } catch (e: Exception) {
            AppLogger.e("Compiler", "编译导出失败", e)
            false
        }
    }

    /**
     * 将输入转换为符合 Java 标准的驼峰命名 (camelCase)，且首字母小写。
     */
    private fun toCamelCase(input: String): String {
        if (input.isBlank()) return ""
        // 如果包含下划线、连字符或空格，则按边界拆分转换
        if (input.contains("_") || input.contains("-") || input.contains(" ")) {
            val tokens = input.split(Regex("[_\\-\\s]+"))
            val sb = StringBuilder()
            for (i in tokens.indices) {
                val token = tokens[i]
                if (token.isEmpty()) continue
                if (i == 0) {
                    sb.append(token.lowercase())
                } else {
                    sb.append(token.substring(0, 1).uppercase() + token.substring(1).lowercase())
                }
            }
            return sb.toString()
        } else {
            // 如果本来就是驼峰或单一单词，直接将首字母小写
            return input.substring(0, 1).lowercase() + input.substring(1)
        }
    }

    /**
     * 计算二进制数据的 FNV-1a 32位哈希值并返回8位十六进制字符串
     */
    private fun calculateFnv1aHash(data: ByteArray): String {
        var hash = 0x811c9dc5L
        for (b in data) {
            hash = hash xor (b.toInt() and 0xFF).toLong()
            hash = (hash * 0x01000193) and 0xFFFFFFFFL
        }
        return hash.toString(16).padStart(8, '0')
    }

    private suspend fun exportBlock(
        block: UIBlock, 
        targetAssetsPath: String, 
        normalizedOutput: String,
        obfuscateAssets: Boolean
    ): ExportedNode {
        var exportedImagePath: String? = null

        // 检查是否有配置的图片（包括 AI 生成或本地加载的图片）
        block.currentImageUri?.let { templateFile ->
            try {
                // 读取原始文件
                val imageBytes = templateFile.readBytes()
                
                if (imageBytes != null) {
                    val baseName = if (block.resourceBindingPath.isNotEmpty()) {
                        block.resourceBindingPath.last()
                    } else {
                        block.id
                    }
                    val fileName = if (obfuscateAssets) {
                        val hash = calculateFnv1aHash(imageBytes)
                        "$hash.png"
                    } else {
                        "$baseName.png"
                    }
                    val destFilePath = "$targetAssetsPath/$fileName"
                    
                    // 写入目标文件夹的绝对路径
                    writeBytesInternal(destFilePath, imageBytes)
                    
                    // 返回相对于 GameConfig.json 的路径 (例如：assets/xxx.png)
                    val relativePrefix = if (normalizedOutput.isNotEmpty()) "$normalizedOutput/" else ""
                    exportedImagePath = "$relativePrefix$fileName"
                }
            } catch (e: Exception) {
                AppLogger.e("Compiler", "复制图片资源失败: ${templateFile.getAbsolutePath()}", e)
            }
        }

        // 递归处理子节点
        val exportedChildren = block.children.map { exportBlock(it, targetAssetsPath, normalizedOutput, obfuscateAssets) }

        // 节点命名：有绑定资源则使用绑定资源名字，没有则用 id。均需小驼峰且首字母小写。
        val originalName = if (block.resourceBindingPath.isNotEmpty()) {
            block.resourceBindingPath.last()
        } else {
            block.id
        }
        val camelName = toCamelCase(originalName)

        return ExportedNode(
            id = block.id,
            type = block.type.name,
            name = camelName,
            x = block.bounds.left.toInt(),
            y = block.bounds.top.toInt(),
            width = block.bounds.width.toInt(),
            height = block.bounds.height.toInt(),
            description = block.userPromptZh, // 将中文描述作为功能描述
            imagePath = exportedImagePath,
            children = exportedChildren
        )
    }
}
