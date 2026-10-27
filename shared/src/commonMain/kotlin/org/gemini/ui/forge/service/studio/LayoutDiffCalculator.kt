package org.gemini.ui.forge.service.studio

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIStudioBlock
import kotlin.math.abs

/**
 * 单个图元的几何或尺寸变更差异条目
 */
data class BlockGeometryDiff(
    val blockId: String,
    val name: String,
    val oldBounds: SerialRect?,
    val newBounds: SerialRect?,
    val isMoved: Boolean = false,
    val isResized: Boolean = false
)

/**
 * 界面布局整体比对差异摘要结果
 */
data class LayoutDiffResult(
    val addedBlocks: List<UIStudioBlock> = emptyList(),
    val modifiedBlocks: List<BlockGeometryDiff> = emptyList(),
    val removedBlocks: List<UIStudioBlock> = emptyList()
) {
    val totalChanges: Int get() = addedBlocks.size + modifiedBlocks.size + removedBlocks.size
    val hasChanges: Boolean get() = totalChanges > 0
}

/**
 * AI 布局变更 Diff 计算器
 *
 * 高性能比对旧图元列表与新图元列表，计算新增、修改几何属性与移除的图元差分。
 */
object LayoutDiffCalculator {

    private const val EPSILON = 0.5f

    /**
     * 计算两组图元树之间的差分
     *
     * @param oldBlocks 旧图元树
     * @param newBlocks 新图元树
     * @return 结构化 Diff 结果
     */
    fun calculateDiff(oldBlocks: List<UIStudioBlock>, newBlocks: List<UIStudioBlock>): LayoutDiffResult {
        val oldMap = oldBlocks.associateBy { it.id }
        val newMap = newBlocks.associateBy { it.id }

        val added = mutableListOf<UIStudioBlock>()
        val modified = mutableListOf<BlockGeometryDiff>()
        val removed = mutableListOf<UIStudioBlock>()

        for (newBlock in newBlocks) {
            val oldBlock = oldMap[newBlock.id]
            if (oldBlock == null) {
                added.add(newBlock)
            } else {
                val oldB = oldBlock.bounds
                val newB = newBlock.bounds
                val isMoved = abs(oldB.left - newB.left) > EPSILON || abs(oldB.top - newB.top) > EPSILON
                val isResized = abs(oldB.width - newB.width) > EPSILON || abs(oldB.height - newB.height) > EPSILON

                if (isMoved || isResized) {
                    modified.add(
                        BlockGeometryDiff(
                            blockId = newBlock.id,
                            name = "${newBlock.type.name} (${newBlock.id})",
                            oldBounds = oldB,
                            newBounds = newB,
                            isMoved = isMoved,
                            isResized = isResized
                        )
                    )
                }
            }
        }

        for (oldBlock in oldBlocks) {
            if (!newMap.containsKey(oldBlock.id)) {
                removed.add(oldBlock)
            }
        }

        return LayoutDiffResult(
            addedBlocks = added,
            modifiedBlocks = modified,
            removedBlocks = removed
        )
    }
}
