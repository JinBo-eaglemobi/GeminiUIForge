package org.gemini.ui.forge.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 资源绑定有效性验证工具，实现弱校验与断链检测
 */
object ResourceBindingValidator {

    /**
     * 校验给定的完整路径是否在 configData 中合法存在。
     * 如果存在失效，返回第一个失效的 key 信息及层级，或者返回 null 表示完全有效。
     * 
     * @param path 待校验的绑定路径
     * @param configData 当前加载的配置文件列表（树形结构）
     * @return 失效的信息对 (层级索引, 失效的key)；若完全有效则返回 null
     */
    fun findFirstInvalidKey(path: List<String>, configData: List<ResourceItem>): Pair<Int, String>? {
        if (path.isEmpty()) return null

        var currentItems = configData
        for (i in path.indices) {
            val currentKey = path[i]
            val matchItem = currentItems.find { it.key == currentKey }
            if (matchItem == null) {
                return Pair(i, currentKey)
            }
            if (i < path.size - 1) {
                currentItems = matchItem.child ?: return Pair(i + 1, path[i + 1])
            }
        }

        return null
    }

    /**
     * 校验整个项目中的所有绑定路径，收集所有失效的绑定信息。
     */
    fun validateProjectBindings(
        projectState: ProjectState,
        configData: List<ResourceItem>
    ): List<InvalidBindingReport> {
        val result = mutableListOf<InvalidBindingReport>()
        
        fun checkBlock(pageId: String, pageName: String, block: UIBlock) {
            val path = block.resourceBindingPath
            if (path.isNotEmpty()) {
                val invalidInfo = findFirstInvalidKey(path, configData)
                if (invalidInfo != null) {
                    result.add(
                        InvalidBindingReport(
                            pageId = pageId,
                            pageName = pageName,
                            blockId = block.id,
                            blockName = block.id, // 用 id 替换未定义的 nameStr
                            invalidIndex = invalidInfo.first,
                            invalidKey = invalidInfo.second,
                            fullPath = path
                        )
                    )
                }
            }
            block.children.forEach { checkBlock(pageId, pageName, it) }
        }

        projectState.pages.forEach { page ->
            page.blocks.forEach { block ->
                checkBlock(page.id, page.nameStr, block)
            }
        }

        return result
    }
}

/**
 * 资源失效校验详细报告数据类
 */
data class InvalidBindingReport(
    val pageId: String,
    val pageName: String,
    val blockId: String,
    val blockName: String,
    val invalidIndex: Int,
    val invalidKey: String,
    val fullPath: List<String>
)
