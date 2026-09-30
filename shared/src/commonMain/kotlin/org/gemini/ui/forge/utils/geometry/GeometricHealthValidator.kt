package org.gemini.ui.forge.utils.geometry

import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import kotlin.math.max
import kotlin.math.min

/**
 * 几何健康度缺陷严重级别
 */
enum class GeometricIssueLevel {
    BLOCKER,  // 阻断级：尺寸非法、完全飞脱画布、非从属同级业务图元大面积重叠冲突
    WARNING   // 警告级：轻微越界等
}

/**
 * 几何健康度缺陷条目
 *
 * @property blockId 产生缺陷的图元 ID
 * @property level 缺陷严重级别（阻断级或警告级）
 * @property description 缺陷的具体几何成因与位置描述
 */
data class GeometricIssue(
    val blockId: String,
    val level: GeometricIssueLevel,
    val description: String
)

/**
 * 纯通用图元几何健康度审查器 (GeometricHealthValidator)
 *
 * 不依赖任何特定游戏或业务类型命名，基于通用物理几何法则（尺寸有效性、视口包含性、实体独立性）
 * 进行死门禁核查，杜绝未拆分大框、位置飞脱、错位重叠半成品流入交付。
 */
object GeometricHealthValidator {

    /**
     * 校验图元列表的几何健康度
     *
     * @param blocks 已绑定 parent 的图元树列表（含顶层与嵌套子图元）
     * @param canvasWidth 画布宽度
     * @param canvasHeight 画布高度
     * @return 发现的几何缺陷列表，按阻断级优先排序
     */
    fun validate(
        blocks: List<UIBlock>,
        canvasWidth: Float,
        canvasHeight: Float
    ): List<GeometricIssue> {
        val issues = mutableListOf<GeometricIssue>()

        // 展平所有图元树节点
        fun flatten(list: List<UIBlock>): List<UIBlock> {
            val result = mutableListOf<UIBlock>()
            for (item in list) {
                result.add(item)
                if (item.children.isNotEmpty()) {
                    result.addAll(flatten(item.children))
                }
            }
            return result
        }

        val allBlocks = flatten(blocks)
        // 筛选出参与实体审查的业务图元（排除全屏背景和纯占位容器）
        val businessBlocks = allBlocks.filter {
            it.type != UIBlockType.BACKGROUND && !it.isPureContainer
        }

        // 1. 尺寸有效性与完全飞脱视口核查 (Blocker)
        for (b in businessBlocks) {
            val abs = b.toAbsoluteBounds()
            if (abs.width <= 1f || abs.height <= 1f) {
                issues.add(
                    GeometricIssue(
                        blockId = b.id,
                        level = GeometricIssueLevel.BLOCKER,
                        description = "图元【${b.id}】尺寸非法 (${abs.width}x${abs.height})，必须具有正向有效宽高"
                    )
                )
            }

            // 完全飞脱出画布视口 [0, 0, canvasWidth, canvasHeight]
            if (abs.right <= 0f || abs.left >= canvasWidth || abs.bottom <= 0f || abs.top >= canvasHeight) {
                issues.add(
                    GeometricIssue(
                        blockId = b.id,
                        level = GeometricIssueLevel.BLOCKER,
                        description = "图元【${b.id}】坐标 [${abs.left}, ${abs.top}, ${abs.right}, ${abs.bottom}] 完全飞脱出画布视口 [0, 0, $canvasWidth, $canvasHeight]"
                    )
                )
            }
        }

        // 2. 独立实体之间大面积严重重叠冲突核查 (Blocker)
        // 两个独立的业务图元（彼此无父子从属关系）不应发生 > 60% 的面积重合
        for (i in 0 until businessBlocks.size) {
            val b1 = businessBlocks[i]
            val rect1 = b1.toAbsoluteBounds()
            val area1 = rect1.width * rect1.height
            if (area1 <= 1f) continue

            for (j in i + 1 until businessBlocks.size) {
                val b2 = businessBlocks[j]
                // 排除父子从属树链关系
                if (isAncestorOrDescendant(b1, b2)) continue

                val rect2 = b2.toAbsoluteBounds()
                val area2 = rect2.width * rect2.height
                if (area2 <= 1f) continue

                val interLeft = max(rect1.left, rect2.left)
                val interTop = max(rect1.top, rect2.top)
                val interRight = min(rect1.right, rect2.right)
                val interBottom = min(rect1.bottom, rect2.bottom)

                val interW = max(0f, interRight - interLeft)
                val interH = max(0f, interBottom - interTop)
                val interArea = interW * interH

                val minArea = min(area1, area2)
                if (minArea > 0f) {
                    val overlapRatio = interArea / minArea
                    if (overlapRatio > 0.6f) {
                        issues.add(
                            GeometricIssue(
                                blockId = b1.id,
                                level = GeometricIssueLevel.BLOCKER,
                                description = "图元【${b1.id}】与【${b2.id}】发生严重大面积重叠冲突 (重叠度 ${(overlapRatio * 100).toInt()}%)，疑似存在粗暴大框打包未拆分或位置飞脱"
                            )
                        )
                    }
                }
            }
        }

        return issues.sortedBy { it.level }
    }

    /**
     * 判断两个图元是否存在祖先-后代从属关系
     */
    private fun isAncestorOrDescendant(a: UIBlock, b: UIBlock): Boolean {
        var curr = a.parent
        while (curr != null) {
            if (curr.id == b.id) return true
            curr = curr.parent
        }
        curr = b.parent
        while (curr != null) {
            if (curr.id == a.id) return true
            curr = curr.parent
        }
        return false
    }
}
