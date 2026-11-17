package org.gemini.ui.forge.utils

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import kotlin.math.max

/**
 * 模块组布局规范化与几何原点归零算法引擎 (UIBlockLayoutNormalizer)
 *
 * 纯函数实现，代数守恒保证：
 * 1. 若子组件中存在背景图元，直接以背景的大小和坐标定义父模块的大小和坐标，背景相对坐标归零；
 * 2. 若无背景，保持原有显示大小，父模块绝对坐标锚定至子组件的最左与最顶边界 (Xmin, Ymin)；
 *    最顶子图元相对 Y 归零为 0，最左子图元相对 X 归零为 0，所有子组件在全景图上的绝对像素坐标 100% 保持守恒！
 */
object UIBlockLayoutNormalizer {

    /**
     * 判断某个模块是否属于“背景”图元
     */
    fun isBackgroundBlock(block: UIBlock): Boolean {
        val lowerName = block.id.lowercase()
        return lowerName == "bg" ||
                lowerName.contains("背景") ||
                lowerName.contains("background") ||
                lowerName.endsWith("_bg") ||
                lowerName.startsWith("bg_")
    }

    /**
     * 判断模块在逻辑上是否应被系统识别为无独立图片资产的布局容器。
     */
    fun shouldBePureContainer(block: UIBlock): Boolean {
        return block.currentImageUri == null &&
                (block.type == UIBlockType.COMPONENT || block.type == UIBlockType.LIST || block.children.isNotEmpty())
    }

    /**
     * 顶层模块列表全局自愈：强制校验顶层背景模块铺满全屏画布
     */
    fun normalizePageBlocks(blocks: List<UIBlock>, canvasWidth: Float, canvasHeight: Float): List<UIBlock> {
        val fullCanvas = SerialRect(0f, 0f, canvasWidth, canvasHeight)
        return blocks.map { block ->
            if (isBackgroundBlock(block) && block.parent == null) {
                if (block.bounds != fullCanvas || block.cropRect != fullCanvas) {
                    AppLogger.i("Normalizer", "🖼️ 顶层背景模块【${block.id}】自愈纠偏为全屏画布尺寸 [0, 0, $canvasWidth, $canvasHeight]")
                    block.copy(bounds = fullCanvas, cropRect = fullCanvas)
                } else {
                    block
                }
            } else {
                block
            }
        }
    }

    /**
     * 对目标父模块进行容器尺寸自适应与相对坐标归零推导。
     *
     * @param parentBlock 待处理的父模块（必须包含 children）
     * @return 重新校准后的父模块 UIBlock（含已更新坐标的 children），若无子组件则原样返回
     */
    fun normalizeContainerAndChildren(parentBlock: UIBlock): UIBlock {
        val children = parentBlock.children
        if (children.isEmpty()) return parentBlock

        // 1. 探查是否存在背景图元
        val bgBlock = children.firstOrNull { isBackgroundBlock(it) }

        return if (bgBlock != null) {
            // === 分支 A：存在背景图元 ===
            val bgAbs = bgBlock.absoluteBounds
            val originalParentAbs = parentBlock.absoluteBounds

            val deltaX = bgAbs.left - originalParentAbs.left
            val deltaY = bgAbs.top - originalParentAbs.top

            val newParentBounds = SerialRect(
                left = parentBlock.bounds.left + deltaX,
                top = parentBlock.bounds.top + deltaY,
                right = parentBlock.bounds.left + deltaX + bgAbs.width,
                bottom = parentBlock.bounds.top + deltaY + bgAbs.height
            )

            val tempParent = parentBlock.copy(bounds = newParentBounds)

            val updatedChildren = children.map { child ->
                if (child.id == bgBlock.id) {
                    child.copy(bounds = SerialRect(0f, 0f, bgAbs.width, bgAbs.height))
                } else {
                    val childAbs = child.absoluteBounds
                    child.copy(
                        bounds = SerialRect(
                            left = childAbs.left - bgAbs.left,
                            top = childAbs.top - bgAbs.top,
                            right = (childAbs.left - bgAbs.left) + child.bounds.width,
                            bottom = (childAbs.top - bgAbs.top) + child.bounds.height
                        )
                    )
                }
            }
            tempParent.copy(children = updatedChildren)

        } else {
            // === 分支 B：没有背景图元 ===
            val childAbsList = children.map { it.absoluteBounds }
            val minX = childAbsList.minOf { it.left }
            val minY = childAbsList.minOf { it.top }
            val maxX = childAbsList.maxOf { it.right }
            val maxY = childAbsList.maxOf { it.bottom }

            val contentWidth = maxX - minX
            val contentHeight = maxY - minY

            val finalWidth = max(parentBlock.bounds.width, contentWidth)
            val finalHeight = max(parentBlock.bounds.height, contentHeight)

            val originalParentAbs = parentBlock.absoluteBounds
            val deltaX = minX - originalParentAbs.left
            val deltaY = minY - originalParentAbs.top

            val newParentBounds = SerialRect(
                left = parentBlock.bounds.left + deltaX,
                top = parentBlock.bounds.top + deltaY,
                right = parentBlock.bounds.left + deltaX + finalWidth,
                bottom = parentBlock.bounds.top + deltaY + finalHeight
            )

            val updatedChildren = children.map { child ->
                val childAbs = child.absoluteBounds
                val relLeft = childAbs.left - minX
                val relTop = childAbs.top - minY
                child.copy(
                    bounds = SerialRect(
                        left = relLeft,
                        top = relTop,
                        right = relLeft + child.bounds.width,
                        bottom = relTop + child.bounds.height
                    )
                )
            }

            parentBlock.copy(bounds = newParentBounds, children = updatedChildren)
        }
    }
}
