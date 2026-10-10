package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.model.assetsheet.NormalizedAssetItem
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType

/**
 * 校对预审工具模式
 */
enum class VerifyToolMode {
    /** 鼠标选择与 8 向拉伸变换模式 */
    SELECT,
    /** 手动画框新增图元模式 */
    DRAW_RECT
}

/**
 * 可编辑图元切片项
 *
 * @property id 唯一标识符
 * @property name 图元名称
 * @property category 业务分类
 * @property bounds 原图上的物理绝对坐标与尺寸
 * @property blockType 启发式推导的组件类型
 * @property isSelected 当前是否被选中
 */
data class EditableSliceItem(
    val id: String,
    val name: String,
    val category: String = "Default",
    val bounds: SerialRect,
    val blockType: UIBlockType = UIBlockType.IMAGE,
    val isSelected: Boolean = false
) {
    /**
     * 转回标准的归一化图元项
     */
    fun toNormalizedItem(): NormalizedAssetItem {
        return NormalizedAssetItem(
            id = id,
            name = name,
            category = category,
            type = blockType,
            bounds = bounds
        )
    }

    companion object {
        /**
         * 从归一化项构造可编辑项
         */
        fun fromNormalizedItem(item: NormalizedAssetItem, isSelected: Boolean = false): EditableSliceItem {
            return EditableSliceItem(
                id = item.id,
                name = item.name,
                category = item.category,
                bounds = item.bounds,
                blockType = item.type,
                isSelected = isSelected
            )
        }
    }
}
