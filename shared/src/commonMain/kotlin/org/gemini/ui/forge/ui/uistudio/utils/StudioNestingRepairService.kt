package org.gemini.ui.forge.ui.uistudio.utils

import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.UIStudioNestingContract
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.utils.bindParents
import kotlin.math.ceil

/**
 * UI Studio 内嵌子页面一键修复服务 (StudioNestingRepairService)。
 *
 * 核心原理：
 * 1. 利用 [UIStudioBlock.bounds] 本就是"相对直接父容器的局部坐标"这一几何结构优势，
 *    内嵌 children 提升为新页面根级 blocks 时坐标零换算，天然保持与原视觉对齐；
 * 2. 迭代收敛拆分：每轮收集违规内联 COMPONENT（豁免 LIST 预置 item），自动生成
 *    独立页面并转为 targetPageId 引用实例，直至全工程 0 违规（防死循环上限 10 轮）；
 * 3. 严格遵循人机授权原则：由用户在确认对话框中显式同意后方可触发，全程可撤销。
 */
object StudioNestingRepairService {

    /**
     * 修复结果报告
     */
    data class RepairResult(
        val repairedProject: ProjectState,
        val createdPageIds: List<String>,
        val repairedBlockPaths: List<String>,
        val summaryLines: List<String>
    )

    private const val MAX_ROUNDS = 10

    /**
     * 根据图元 ID 生成不冲突的新页面 ID（如 lineStake -> LineStakeView；若冲突自增 _2, _3）
     */
    private fun derivePageId(blockId: String, existingIds: Set<String>): String {
        val cleaned = blockId.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        val base = (if (cleaned.isNotBlank()) cleaned else "SubView").replaceFirstChar { it.uppercase() } + "View"
        if (base !in existingIds) return base
        var idx = 2
        while ("${base}_$idx" in existingIds) {
            idx++
        }
        return "${base}_$idx"
    }

    /**
     * 递归收集图元树中所有处于非 LIST item 位的内嵌 COMPONENT
     */
    private fun collectInlineComponents(
        blocks: List<UIStudioBlock>,
        isListItemChild: Boolean,
        consumer: (UIStudioBlock) -> Unit
    ) {
        for (b in blocks) {
            if (UIStudioNestingContract.isAutoFixable(b, isListItemChild)) {
                consumer(b)
            }
            if (b.children.isNotEmpty()) {
                collectInlineComponents(b.children, b.type == UIBlockType.LIST, consumer)
            }
        }
    }

    /**
     * 递归替换图元树中的指定图元
     */
    private fun replaceBlockById(
        blocks: List<UIStudioBlock>,
        targetId: String,
        transform: (UIStudioBlock) -> UIStudioBlock
    ): List<UIStudioBlock> {
        return blocks.map { b ->
            if (b.id == targetId) {
                transform(b)
            } else if (b.children.isNotEmpty()) {
                b.copy(children = replaceBlockById(b.children, targetId, transform))
            } else {
                b
            }
        }
    }

    /**
     * 对工程全部页面执行迭代收敛拆分修复。
     *
     * @param project 待修复的工程实体
     * @return 修复后的工程与新建页面清单
     */
    fun repairAll(project: ProjectState): RepairResult {
        var pages = project.studioPages.toMutableList()
        val createdIds = mutableListOf<String>()
        val repairedPaths = mutableListOf<String>()
        val summaryLines = mutableListOf<String>()

        data class PendingTarget(val pageId: String, val block: UIStudioBlock)

        repeat(MAX_ROUNDS) {
            val pendingList = mutableListOf<PendingTarget>()
            for (page in pages) {
                collectInlineComponents(page.blocks, isListItemChild = false) { b ->
                    pendingList.add(PendingTarget(page.id, b))
                }
            }

            if (pendingList.isEmpty()) {
                // 已完全收敛，全工程 0 违规内联容器
                return@repeat
            }

            val existingIds = pages.map { it.id }.toMutableSet()
            for (p in pendingList) {
                val newPageId = derivePageId(p.block.id, existingIds)
                existingIds.add(newPageId)

                // 1. 新页面：尺寸 = 组件当前外包矩形宽高（向上取整保视觉完整），blocks = 原 children
                val w = ceil(p.block.bounds.width).coerceAtLeast(10f)
                val h = ceil(p.block.bounds.height).coerceAtLeast(10f)
                val newPage = UIStudioPage(
                    id = newPageId,
                    width = w,
                    height = h,
                    blocks = p.block.children.bindParents()
                )
                pages.add(newPage)
                createdIds.add(newPageId)

                // 2. 原宿主页面中的组件转为 targetPageId 引用实例
                val pageIndex = pages.indexOfFirst { it.id == p.pageId }
                if (pageIndex != -1) {
                    val hostPage = pages[pageIndex]
                    val updatedBlocks = replaceBlockById(hostPage.blocks, p.block.id) { old ->
                        old.copy(
                            type = UIBlockType.COMPONENT,
                            targetPageId = newPageId,
                            children = emptyList(),
                            assetHash = null,
                            cropRect = null
                        )
                    }.bindParents()
                    pages[pageIndex] = hostPage.copy(blocks = updatedBlocks)
                }

                val pathDesc = "${p.pageId} > ${p.block.id}"
                repairedPaths.add(pathDesc)
                summaryLines.add("组件「${p.block.id}」→ 拆分为独立页面 [$newPageId] (${w.toInt()}×${h.toInt()}) 并转为引用实例")
            }
        }

        val updatedProject = project.copy(studioPages = pages)
        return RepairResult(
            repairedProject = updatedProject,
            createdPageIds = createdIds,
            repairedBlockPaths = repairedPaths,
            summaryLines = summaryLines
        )
    }
}
