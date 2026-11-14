package org.gemini.ui.forge.ui.uistudio.utils

import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.uistudio.model.UIStudioDataError
import org.gemini.ui.forge.ui.uistudio.model.UIStudioDataErrorType

/**
 * UI Studio 工程数据健康与完整性诊断分析器。
 * 纯内存无损检测，用于在界面常驻提示任何异常数据状态。
 */
object StudioDataDiagnostics {

    /**
     * 对工程全部页面的图元引用关系与结构做深度扫描诊断。
     *
     * @param project 待检测的工程状态
     * @return 异常诊断列表（若数据完全健康则返回空列表）
     */
    fun diagnoseProject(project: ProjectState?): List<UIStudioDataError> {
        if (project == null || !project.isUiStudioProject) return emptyList()

        val pages = project.studioPages
        val pageIds = pages.map { it.id }.toSet()
        val errors = mutableListOf<UIStudioDataError>()

        fun scanBlock(block: UIStudioBlock, page: UIStudioPage, parentPath: String) {
            val currentPath = if (parentPath.isEmpty()) block.id else "$parentPath > ${block.id}"
            val targetId = block.targetPageId

            if (!targetId.isNullOrBlank()) {
                // 1. 检查引用的目标页面是否存在
                if (!pageIds.contains(targetId)) {
                    errors.add(
                        UIStudioDataError(
                            pageId = page.id,
                            pageName = page.nameStr,
                            blockId = block.id,
                            targetPageId = targetId,
                            errorType = UIStudioDataErrorType.TARGET_PAGE_NOT_FOUND,
                            message = "视图「${page.nameStr}」中的组件「$currentPath」引用的目标页面「$targetId」在工程中不存在"
                        )
                    )
                }

                // 2. 检查引用图元是否包含了冗余历史 children
                if (block.children.isNotEmpty()) {
                    errors.add(
                        UIStudioDataError(
                            pageId = page.id,
                            pageName = page.nameStr,
                            blockId = block.id,
                            targetPageId = targetId,
                            errorType = UIStudioDataErrorType.REFERENCE_WITH_CHILDREN,
                            message = "视图「${page.nameStr}」中的组件「$currentPath」数据异常：已引用独立页面「$targetId」，但自身仍包含 ${block.children.size} 个历史子图元数据 (渲染时已自动忽略，数据已原样保留未破坏)"
                        )
                    )
                }
            }

            // 递归扫描子图元
            for (child in block.children) {
                scanBlock(child, page, currentPath)
            }
        }

        for (page in pages) {
            for (rootBlock in page.blocks) {
                scanBlock(rootBlock, page, "")
            }
        }

        return errors
    }
}
