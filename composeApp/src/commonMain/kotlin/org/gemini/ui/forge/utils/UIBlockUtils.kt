package org.gemini.ui.forge.utils

import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.model.ui.UIBlock

/**
 * UIBlock 通用算法工具集 (以 List<UIBlock> 和 UIBlock 的扩展方法形式提供)
 * 收拢项目中散落在多处的 UIBlock 递归、命中、更新等纯算法逻辑。
 */

/**
 * 递归遍历模块树，通过 ID 检索对应的 UIBlock。
 *
 * @param id 目标模块的唯一标识符
 * @return 匹配的 UIBlock 实例。若未找到则返回 null
 */
fun List<UIBlock>.findBlockById(id: String): UIBlock? {
    for (block in this) {
        if (block.id == id) return block
        val found = block.children.findBlockById(id)
        if (found != null) return found
    }
    return null
}

/**
 * 递归遍历模块树，查找目标模块的父模块 ID。
 *
 * @param targetId 目标模块的唯一标识符
 * @param currentParentId 当前级别的父模块 ID (递归累计使用)
 * @return 父模块的 ID 标识符。若未找到或其本身是顶层模块，则返回 null
 */
fun List<UIBlock>.findParentBlockId(targetId: String, currentParentId: String? = null): String? {
    for (block in this) {
        if (block.id == targetId) return currentParentId
        val found = block.children.findParentBlockId(targetId, block.id)
        if (found != null) return found
    }
    return null
}

/**
 * 递归更新模块列表中的指定模块。
 *
 * @param blockId 目标模块的唯一标识符
 * @param transform 对目标模块进行修改转换的 Lambda 函数
 * @return 更新后的模块列表（全新不可变列表副本）
 */
fun List<UIBlock>.updateBlockInList(blockId: String, transform: (UIBlock) -> UIBlock): List<UIBlock> {
    return this.map { block ->
        if (block.id == blockId) {
            transform(block)
        } else {
            val newChildren = block.children.updateBlockInList(blockId, transform)
            if (newChildren !== block.children) {
                block.copy(children = newChildren)
            } else {
                block
            }
        }
    }
}

/**
 * 递归计算指定模块在当前画布上的全局绝对逻辑位置。
 * 由于 UIBlock 树中 bounds 存储的通常是相对于直接父容器的相对偏移量，因此需要向下累计父级的坐标以得出绝对坐标。
 *
 * @param id 目标模块唯一标识符
 * @param currentX 递归调用中累计传递的绝对 X 逻辑坐标
 * @param currentY 递归调用中累计传递的绝对 Y 逻辑坐标
 * @return 目标模块的绝对坐标 Offset。如果未找到该模块则返回 null
 */
fun List<UIBlock>.calculateBlockAbsolutePosition(id: String, currentX: Float = 0f, currentY: Float = 0f): Offset? {
    for (block in this) {
        val absX = currentX + block.bounds.left
        val absY = currentY + block.bounds.top
        if (block.id == id) return Offset(absX, absY)
        val found = block.children.calculateBlockAbsolutePosition(id, absX, absY)
        if (found != null) return found
    }
    return null
}

/**
 * 递归计算指定模块在当前画布上的全局绝对逻辑矩形 (SerialRect)。
 *
 * @param id 目标模块唯一标识符
 * @return 目标模块的绝对矩形。如果未找到该模块则返回 null
 */
fun List<UIBlock>.calculateBlockAbsoluteBounds(id: String): org.gemini.ui.forge.model.ui.SerialRect? {
    val block = this.findBlockById(id) ?: return null
    val absPos = this.calculateBlockAbsolutePosition(id) ?: Offset.Zero
    val w = kotlin.math.abs(block.bounds.width)
    val h = kotlin.math.abs(block.bounds.height)
    return org.gemini.ui.forge.model.ui.SerialRect(absPos.x, absPos.y, absPos.x + w, absPos.y + h)
}

/**
 * 递归计算指定模块的父级容器在当前画布上的全局绝对逻辑偏移量 (Offset)。
 * 若模块为顶层根模块，则返回 Offset.Zero。
 *
 * @param id 目标模块唯一标识符
 * @return 父级累计绝对逻辑偏移量 Offset
 */
fun List<UIBlock>.calculateBlockParentOffset(id: String): Offset {
    val parentId = this.findParentBlockId(id) ?: return Offset.Zero
    return this.calculateBlockAbsolutePosition(parentId) ?: Offset.Zero
}

/**
 * 精确查找手势点击位置（逻辑坐标）所命中的最上层 UIBlock（支持多层嵌套检测）。
 * 当处于隔离编辑组（editingGroupId 激活）时，会优先且仅限制于在该隔离组的子模块内部进行碰撞命中检测。
 *
 * @param lx 点击位置的逻辑 X 坐标（已排除画布本身的缩放与绝对物理偏移）
 * @param ly 点击位置的逻辑 Y 坐标
 * @param parentLx 递归调用时，父容器累计的逻辑 X 偏移量
 * @param parentLy 递归调用时，父容器累计的逻辑 Y 偏移量
 * @param editingGroupId 当前激活隔离编辑的组 ID。若为 null，则在全局根节点中进行命中遍历
 * @return 命中的 UIBlock 实例。若没有任何图层命中则返回 null
 */
fun List<UIBlock>.findHitBlock(lx: Float, ly: Float, parentLx: Float = 0f, parentLy: Float = 0f, editingGroupId: String?): UIBlock? {
    if (editingGroupId == null) {
        // 全局模式下，从上至下（倒序遍历，后绘制的在最上层）检索所有命中的顶层模块
        for (i in this.indices.reversed()) {
            val block = this[i]
            if (!block.isVisible) continue
            val absL = parentLx + block.bounds.left
            val absT = parentLy + block.bounds.top
            val absR = parentLx + block.bounds.right
            val absB = parentLy + block.bounds.bottom
            if (lx in absL..absR && ly >= absT && ly <= absB) return block
        }
        return null
    }
    // 隔离编辑模式下：仅在被编辑的隔离组 of 子元素列表中检索命中
    val targetGroup = this.findBlockById(editingGroupId) ?: return null
    val groupAbsPos = this.calculateBlockAbsolutePosition(editingGroupId) ?: Offset.Zero
    for (i in targetGroup.children.indices.reversed()) {
        val child = targetGroup.children[i]
        if (!child.isVisible) continue
        val absL = groupAbsPos.x + child.bounds.left
        val absT = groupAbsPos.y + child.bounds.top
        val absR = groupAbsPos.x + child.bounds.right
        val absB = groupAbsPos.y + child.bounds.bottom
        if (lx >= absL && lx <= absR && ly >= absT && ly <= absB) return child
    }
    // 如果子元素都没命中，但点击点落在了组容器本身的 bounds 内，则返回组容器本身
    val gL = groupAbsPos.x
    val gT = groupAbsPos.y
    val gR = groupAbsPos.x + targetGroup.bounds.width
    val gB = groupAbsPos.y + targetGroup.bounds.height
    if (lx >= gL && lx <= gR && ly >= gT && ly <= gB) return targetGroup
    return null
}

/**
 * 递归判断指定的父图层是否包含（或其自身就是）目标子图层 ID。
 *
 * @param targetId 需匹配的后代子组件的 ID 标识符
 * @return true 表示存在该包含关系，false 表示不包含
 */
fun UIBlock.containsBlock(targetId: String): Boolean {
    if (this.id == targetId) return true
    return this.children.any { it.containsBlock(targetId) }
}

/**
 * 判断特定模块在隔离编辑模式下是否应该呈现半透明虚化（Dim）状态。
 * 当隔离编辑某一组件组时，只有该组件本身以及它的所有后代子图层显示高亮，画布上的其他非相关组件都要进行灰暗置度处理。
 *
 * @param editingGroupId 当前处于隔离编辑的组 ID。若为 null，则处于全局普通编辑，所有图层正常显示
 * @return true 代表需要将图层变暗虚化，false 代表正常高亮渲染
 */
fun UIBlock.shouldDim(editingGroupId: String?): Boolean {
    if (editingGroupId == null) return false
    if (this.id == editingGroupId) return false
    return !this.containsBlock(editingGroupId)
}
