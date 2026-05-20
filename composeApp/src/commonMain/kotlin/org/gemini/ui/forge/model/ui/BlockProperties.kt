package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.TemplateFile

/**
 * 定义不同 UI 组件特有的属性配置模型。
 * 这是一个密封类，每个子类对应一种特定类型的 UI 组件属性。
 */
@Serializable
sealed class BlockProperties {

    /**
     * 图片或图标组件的属性配置。
     *
     * @property resizeMode 图片的缩放模式，默认为 [ImageResizeMode.STRETCH]。
     * @property ninePatchConfig 当缩放模式为 [ImageResizeMode.NINE_PATCH] 时使用的九宫格配置信息。
     */
    @Serializable
    data class ImageProperties(
        val resizeMode: ImageResizeMode = ImageResizeMode.STRETCH,
        val ninePatchConfig: NinePatchConfig = NinePatchConfig()
    ) : BlockProperties()

    /**
     * 按钮组件的属性配置。
     *
     * @property text 按钮上显示的文案。
     * @property isMultiState 指示是否为按钮生成多种状态（如：正常、按下、禁用）的图片。
     * @property pressedUri 按钮处于按下状态（Pressed）时的图片资源路径。
     * @property disabledUri 按钮处于禁用状态（Disabled）时的图片资源路径。
     */
    @Serializable
    data class ButtonProperties(
        val text: String = "",
        val isMultiState: Boolean = false,
        val pressedUri: TemplateFile? = null,
        val disabledUri: TemplateFile? = null,
        val pressedPrompt: String = "",
        val disabledPrompt: String = ""
    ) : BlockProperties()

    /**
     * 普通视图或容器组件的属性配置。
     *
     * @property backgroundColor 视图的背景颜色，通常采用十六进制字符串格式（例如："#FFFFFF"）。
     */
    @Serializable
    data class ViewProperties(
        val backgroundColor: String = ""
    ) : BlockProperties()

    /**
     * 文本显示组件的属性配置。
     *
     * @property text 要显示的文本内容。
     * @property textColor 文本的字体颜色，采用十六进制字符串格式（例如："#000000"）。
     * @property textSize 文本的字体大小（单位通常为 sp 或像素，取决于平台实现）。
     * @property isBold 是否加粗
     * @param isItalic 是否倾斜
     * @param horizontalAlign 水平对齐方式: LEFT, CENTER, RIGHT
     * @param verticalAlign 垂直对齐方式: TOP, CENTER, BOTTOM
     * @param strokeColor 描边颜色
     * @param strokeWidth 描边宽度
     */
    @Serializable
    data class TextProperties(
        val text: String = "",
        val textColor: String = "#000000",
        val textSize: Int = 14,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val horizontalAlign: String = "CENTER",
        val verticalAlign: String = "CENTER",
        val strokeColor: String = "",
        val strokeWidth: Float = 0f
    ) : BlockProperties()

    /**
     * 文本输入框组件的属性配置。
     *
     * @property hintText 输入框为空时显示的提示文案（Placeholder）。
     * @property textColor 输入文本的字体颜色。
     * @property textSize 输入文本的字体大小。
     * @property maxLength 允许输入的最大字符长度，设置为 -1 表示不限制长度。
     * @param isBold 是否加粗
     * @param isItalic 是否倾斜
     * @param horizontalAlign 水平对齐方式: LEFT, CENTER, RIGHT
     * @param verticalAlign 垂直对齐方式: TOP, CENTER, BOTTOM
     * @param strokeColor 描边颜色
     * @param strokeWidth 描边宽度
     */
    @Serializable
    data class InputProperties(
        val hintText: String = "",
        val textColor: String = "#808080",
        val textSize: Int = 14,
        val maxLength: Int = -1,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val horizontalAlign: String = "LEFT",
        val verticalAlign: String = "CENTER",
        val strokeColor: String = "",
        val strokeWidth: Float = 0f
    ) : BlockProperties()

    /**
     * 转轴组件的属性配置。
     *
     * @property rows 转轴的行数（例如：3）。
     * @property columns 转轴的列数（例如：5）。
     * @property items 转轴中包含的可选元素集。每一个元素都是一个完整的 UIBlock。
     * @property showBackground 是否显示转轴背景板。
     */
    @Serializable
    data class ReelProperties(
        val rows: Int = 3,
        val columns: Int = 5,
        val items: List<UIBlock> = emptyList(),
        val showBackground: Boolean = true,
        val rollSeed: Int = 0
    ) : BlockProperties()
}

/**
 * 图片缩放模式。
 * 用于指定图片在目标区域内的显示和拉伸行为。
 */
@Serializable
enum class ImageResizeMode {
    /**
     * 强制拉伸：不保持宽高比，直接将图片拉伸以填满整个目标区域。
     */
    STRETCH,

    /**
     * 等比缩放补白：保持图片的原始宽高比进行缩放，使图片能完整显示在目标区域内。如果目标区域比例不同，剩余部分将留白。
     */
    FIT_WITH_PADDING,

    /**
     * 九宫格拉伸：利用九宫格原理，保护图片的边角区域不被拉伸，仅拉伸中间区域。适用于带圆角或边框的背景图。
     */
    NINE_PATCH,

    /**
     * 等比铺满裁剪：保持图片的原始宽高比进行缩放，使其填满整个目标区域。如果比例不一致，超出目标区域的部分将被裁剪。
     */
    CROP_TO_FILL
}

/**
 * 九宫格配置信息。
 * 定义了图片在进行九宫格拉伸时，四周需要保持不变形的边距大小。
 *
 * @property left 左侧不拉伸区域的宽度（像素）。
 * @property top 顶部不拉伸区域的高度（像素）。
 * @property right 右侧不拉伸区域的宽度（像素）。
 * @property bottom 底部不拉伸区域的高度（像素）。
 */
@Serializable
data class NinePatchConfig(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)
