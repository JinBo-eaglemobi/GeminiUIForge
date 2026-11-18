package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.TemplateFile

/**
 * 具有文本排版和渲染样式特征的组件属性接口。
 */
interface TextStyled {
    /** 文本字符串内容 */
    val text: String
    /** 十六进制文本颜色代码（如 "#FFFFFF"） */
    val textColor: String
    /** 字体大小（sp/pt） */
    val textSize: Int
    /** 是否加粗 */
    val isBold: Boolean
    /** 是否倾斜 */
    val isItalic: Boolean
    /** 水平对齐方式（LEFT, CENTER, RIGHT） */
    val horizontalAlign: String
    /** 垂直对齐方式（TOP, CENTER, BOTTOM） */
    val verticalAlign: String
    /** 文字外描边颜色（如 "#000000"，空表示无描边） */
    val strokeColor: String
    /** 文字外描边宽度（像素） */
    val strokeWidth: Float
}

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
     * @property pressedPrompt 按下状态（Pressed）的独立生图提示词。
     * @property disabledPrompt 禁用状态（Disabled）的独立生图提示词。
     * @property downEffect 按压视觉反馈效果（0: 无, 1: 滤镜变暗, 2: 中心向心缩放）。
     * @property downEffectValue 按压反馈系数（例如变暗 0.8 / 0.65，缩放 0.92）。
     * @property mode 按钮交互模式（Common: 普通点击, Check: 勾选复选, Radio: 单选互斥）。
     * @property relatedController 单选关联控制器名。
     * @property pageId 单选对应的页面标识。
     * @property sound 按键音效名。
     */
    @Serializable
    data class ButtonProperties(
        val text: String = "",
        val isMultiState: Boolean = false,
        val pressedUri: TemplateFile? = null,
        val disabledUri: TemplateFile? = null,
        val pressedPrompt: String = "",
        val disabledPrompt: String = "",
        val downEffect: Int = 1,
        val downEffectValue: Float = 0.8f,
        val mode: String = "Common",
        val relatedController: String = "",
        val pageId: String = "",
        val sound: String = ""
    ) : BlockProperties()

    /**
     * 视图容器组件/复合组件（COMPONENT）的属性配置。
     *
     * @property backgroundColor 视图的背景颜色，通常采用十六进制字符串格式（例如："#FFFFFF"）。
     * @property clipOverflow 是否裁剪/隐藏超出视图范围的子内容（overflow: hidden）。
     * @property layoutType 面板子级排版模型（"none": 绝对定位, "vertical": 垂直一维流, "horizontal": 横向一维流）。
     * @property gap 一维流式排版下子元件的固定物理间距（像素）。
     * @property flexWrap 横向一维流（horizontal）是否开启弹性自适应换行折行。
     * @property alignItems 交叉轴统一对齐模型（"flex-start", "center", "flex-end", "stretch"）。
     * @property justifyContent 主轴统一对齐模型（"flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly"）。
     * @property scrollType 容器触控滚动控制（-1: 无滚动, 0: 横向滚动, 1: 纵向滚动, 2: 双向滚动）。
     * @property overflow 容器溢出裁剪状态（0: Visible, 1: Hidden, 2: Scroll）。
     * @property paddingTop 顶边安全内边距（像素）。
     * @property paddingBottom 底边安全内边距（像素）。
     * @property paddingLeft 左边安全内边距（像素）。
     * @property paddingRight 右边安全内边距（像素）。
     */
    @Serializable
    data class ViewProperties(
        val backgroundColor: String = "",
        val clipOverflow: Boolean = false,
        val layoutType: String = "none",
        val gap: Int = 0,
        val flexWrap: Boolean = false,
        val alignItems: String = "flex-start",
        val justifyContent: String = "flex-start",
        val scrollType: Int = -1,
        val overflow: Int = 0,
        val paddingTop: Int = 0,
        val paddingBottom: Int = 0,
        val paddingLeft: Int = 0,
        val paddingRight: Int = 0
    ) : BlockProperties()

    /**
     * 文本显示组件的属性配置。
     *
     * @property text 要显示的文本内容。
     * @property textColor 文本的字体颜色，采用十六进制字符串格式（例如："#000000"）。
     * @property textSize 文本的字体大小（像素）。
     * @property isBold 是否加粗。
     * @property isItalic 是否倾斜。
     * @property horizontalAlign 水平对齐方式: LEFT, CENTER, RIGHT。
     * @property verticalAlign 垂直对齐方式: TOP, CENTER, BOTTOM。
     * @property strokeColor 描边颜色。
     * @property strokeWidth 描边宽度。
     * @property isUbb 是否开启 UBB 富文本解析（支持颜色 [color] / 加粗 [b] 等）。
     * @property leading 文本行间距（像素）。
     * @property letterSpacing 字符间距（像素）。
     * @property underline 是否添加下划线。
     * @property singleLine 是否限制为单行文本。
     */
    @Serializable
    data class TextProperties(
        override val text: String = "",
        override val textColor: String = "#000000",
        override val textSize: Int = 14,
        override val isBold: Boolean = false,
        override val isItalic: Boolean = false,
        override val horizontalAlign: String = "CENTER",
        override val verticalAlign: String = "CENTER",
        override val strokeColor: String = "",
        override val strokeWidth: Float = 0f,
        val isUbb: Boolean = false,
        val leading: Int = 4,
        val letterSpacing: Int = 0,
        val underline: Boolean = false,
        val singleLine: Boolean = false
    ) : BlockProperties(), TextStyled

    /**
     * 文本输入框组件的属性配置。
     *
     * @property hintText 输入框为空时显示的提示文案（Placeholder）。
     * @property textColor 输入文本的字体颜色。
     * @property textSize 输入文本的字体大小。
     * @property maxLength 允许输入的最大字符长度，设置为 -1 表示不限制长度。
     * @property isBold 是否加粗。
     * @property isItalic 是否倾斜。
     * @property horizontalAlign 水平对齐方式: LEFT, CENTER, RIGHT。
     * @property verticalAlign 垂直对齐方式: TOP, CENTER, BOTTOM。
     * @property strokeColor 描边颜色。
     * @property strokeWidth 描边宽度。
     * @property isPassword 是否为密码隐藏输入模式。
     * @property singleLine 是否限制为单行输入。
     */
    @Serializable
    data class InputProperties(
        val hintText: String = "",
        override val textColor: String = "#808080",
        override val textSize: Int = 14,
        val maxLength: Int = -1,
        override val isBold: Boolean = false,
        override val isItalic: Boolean = false,
        override val horizontalAlign: String = "LEFT",
        override val verticalAlign: String = "CENTER",
        override val strokeColor: String = "",
        override val strokeWidth: Float = 0f,
        val isPassword: Boolean = false,
        val singleLine: Boolean = true
    ) : BlockProperties(), TextStyled {
        override val text: String get() = hintText
    }

    /**
     * 动态滚动列表/网格容器组件（LIST，对齐 fgui.GList / PureList）的属性配置。
     *
     * @property listLayout 列表空间布局分布（"SingleColumn": 垂直单列, "SingleRow": 水平单行, "FlowHorizontal": 横向换行网格, "FlowVertical": 纵向换列网格, "Pagination": 分页网格）。
     * @property lineGap 列表子项（行）之间的间距像素值。
     * @property columnGap 列表子项（列）之间的间距像素值。
     * @property itemWidth 每一个卡片子项的默认预设宽度（0 表示自适应）。
     * @property itemHeight 每一个卡片子项的默认预设高度（0 表示自适应）。
     * @property align 列表整体在水平方向的对齐方式（"left", "center", "right"）。
     * @property verticalAlign 列表整体在垂直方向的对齐方式（"top", "middle", "bottom"）。
     * @property scrollType 容器触控滚动控制（-1: 无滚动, 0: 横向滚动, 1: 纵向滚动, 2: 双向滚动）。
     * @property overflow 容器溢出裁剪状态（0: Visible, 1: Hidden, 2: Scroll）。
     * @property lineCount 物理行数（在 FlowVertical 或 Pagination 时有效）。
     * @property columnCount 物理列数（在 FlowHorizontal 或 Pagination 时有效）。
     * @property autoResizeItem 行未排满时是否自动放大该行元素铺满整行。
     */
    @Serializable
    data class ListProperties(
        val listLayout: String = "SingleColumn",
        val lineGap: Int = 0,
        val columnGap: Int = 0,
        val itemWidth: Int = 0,
        val itemHeight: Int = 0,
        val align: String = "left",
        val verticalAlign: String = "top",
        val scrollType: Int = 1,
        val overflow: Int = 2,
        val lineCount: Int = 0,
        val columnCount: Int = 0,
        val autoResizeItem: Boolean = false,
        val defaultItem: String = "",
        val selectionMode: String = "None",
        val foldHiddenItems: Boolean = false,
        val scrollItemToViewFully: Boolean = true
    ) : BlockProperties() {
        /** 确保行距非负 */
        val safeLineGap: Int get() = lineGap.coerceAtLeast(0)
        /** 确保列距非负 */
        val safeColumnGap: Int get() = columnGap.coerceAtLeast(0)
        /** 确保子项宽度非负 */
        val safeItemWidth: Int get() = itemWidth.coerceAtLeast(0)
        /** 确保子项高度非负 */
        val safeItemHeight: Int get() = itemHeight.coerceAtLeast(0)
        /** 确保行数非负 */
        val safeLineCount: Int get() = lineCount.coerceAtLeast(0)
        /** 确保列数非负 */
        val safeColumnCount: Int get() = columnCount.coerceAtLeast(0)
    }

    /**
     * 进度条组件（PROGRESS_BAR，对齐 fgui.GProgressBar）的属性配置。
     *
     * @property max 最大进度数值。
     * @property value 当前进度数值。
     * @property titleType 进度文本展示模式（"Percent": 百分比, "ValueAndMax": 当前/最大值, "Value": 纯数值, "None": 不显示）。
     */
    @Serializable
    data class ProgressBarProperties(
        val max: Double = 100.0,
        val value: Double = 50.0,
        val titleType: String = "Percent"
    ) : BlockProperties()

    /**
     * 滑动条组件（SLIDER，对齐 fgui.GSlider）的属性配置。
     *
     * @property min 最小数值。
     * @property max 最大数值。
     * @property value 当前数值。
     * @property step 滑动步长。
     * @property wholeNumbers 是否仅取整数值。
     */
    @Serializable
    data class SliderProperties(
        val min: Double = 0.0,
        val max: Double = 100.0,
        val value: Double = 50.0,
        val step: Double = 1.0,
        val wholeNumbers: Boolean = true
    ) : BlockProperties()

    /**
     * 下拉选择框组件（COMBO_BOX，对齐 fgui.GComboBox）的属性配置。
     *
     * @property items 选项字符串列表。
     * @property selectedIndex 默认选中项索引。
     * @property visibleItemCount 下拉展开时最大可见项数。
     */
    @Serializable
    data class ComboBoxProperties(
        val items: List<String> = emptyList(),
        val selectedIndex: Int = 0,
        val visibleItemCount: Int = 5
    ) : BlockProperties()

    /**
     * 动态装载器/占位图元组件（LOADER，对齐 fgui.GLoader）的属性配置。
     *
     * @property url 默认装载的资源 URL 或相对路径。
     * @property fill 图像缩放填充模式（"none", "scale", "scalefree", "scalenoborder", "scalematchheight", "scalematchwidth"）。
     * @property clip 是否启用视口边缘裁剪。
     * @property align 水平对齐（"left", "center", "right"）。
     * @property verticalAlign 垂直对齐（"top", "middle", "bottom"）。
     */
    @Serializable
    data class LoaderProperties(
        val url: String = "",
        val fill: String = "none",
        val clip: Boolean = false,
        val align: String = "center",
        val verticalAlign: String = "middle"
    ) : BlockProperties()

    /**
     * 骨骼动画组件（SPINE，对齐 GLoader3D / tsCore.GSpineSkeleton）的属性配置。
     *
     * @property animationName 默认播放的动画名称（如 "idle", "win"）。
     * @property skinName 骨骼皮肤名称（默认 "default"）。
     * @property loop 是否循环播放。
     * @property timeScale 动画播放速度倍率。
     */
    @Serializable
    data class SpineProperties(
        val animationName: String = "idle",
        val skinName: String = "default",
        val loop: Boolean = true,
        val timeScale: Float = 1.0f
    ) : BlockProperties()

    /**
     * 滚动条组件（SCROLL_BAR，对齐 fgui.GScrollBar）的属性配置。
     *
     * @property fixedGripSize 是否固定滚动滑块大小。
     * @property arrowButton 是否显示两端微调箭头按钮。
     */
    @Serializable
    data class ScrollBarProperties(
        val fixedGripSize: Boolean = false,
        val arrowButton: Boolean = false
    ) : BlockProperties()

    /**
     * 矢量图形组件（GRAPH，对齐 fgui.GGraph）的属性配置。
     *
     * @property fillColor 矢量填充颜色（Hex，如 "#FFFFFF"）。
     * @property lineSize 描边线条宽度。
     * @property lineColor 描边颜色。
     * @property cornerRadius 圆角半径（像素）。
     */
    @Serializable
    data class GraphProperties(
        val fillColor: String = "",
        val lineSize: Int = 0,
        val lineColor: String = "",
        val cornerRadius: Int = 0
    ) : BlockProperties()

    /**
     * 转轴组件的属性配置。
     * 支持全形态 Slot 网格（标准对称、单行多列、金字塔异形/动态变轴）。
     *
     * @property rows 转轴的基准行数（默认：3；当单行多列时为 1）。
     * @property columns 转轴的列数（例如：5）。
     * @property columnRowCounts 每列独立的行数数组（例如 [1,1,1,1] 单行，[3,4,5,4,3] 菱形金字塔，[2,3,4,3,2] 阶梯）。为空时走默认 rows × columns 规则矩阵。
     * @property reelLayoutType 转轴排版形态：STANDARD(标准规则矩阵), SINGLE_ROW(单行多列), ASYMMETRIC(异形/金字塔), TOP_EXTRA(顶部附加轴)。
     * @property items 转轴中包含的可选元素集。每一个元素都是一个完整的 UIBlock。
     * @property showBackground 是否显示转轴背景板。
     * @property rollSeed 滚动渲染随机种子，用于生成伪随机图案展示。
     */
    @Serializable
    data class ReelProperties(
        val rows: Int = 3,
        val columns: Int = 5,
        val columnRowCounts: List<Int> = emptyList(),
        val reelLayoutType: String = "STANDARD",
        val items: List<UIBlock> = emptyList(),
        val showBackground: Boolean = true,
        val rollSeed: Int = 0
    ) : BlockProperties()

    /**
     * SPIN_BUTTON (旋转/抽奖按钮) 组件的属性配置。
     *
     * @property stopUri 停止状态（Stop）时的图片资源路径。
     * @property stopPromptZh 停止状态（Stop）的中文生成 Prompt。
     * @property stopPromptEn 停止状态（Stop）的英文生成 Prompt。
     * @property spinResourceBindingPath Spin 状态导出的资源绑定结构路径。
     * @property stopResourceBindingPath Stop 状态导出的资源绑定结构路径。
     */
    @Serializable
    data class SpinButtonProperties(
        val stopUri: TemplateFile? = null,
        val stopPromptZh: String = "",
        val stopPromptEn: String = "",
        val spinResourceBindingPath: List<String> = emptyList(),
        val stopResourceBindingPath: List<String> = emptyList()
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

/**
 * 根据基础 [UIBlockType] 创建对应的默认 [BlockProperties] 实例。
 */
fun createDefaultPropertiesForType(type: UIBlockType): BlockProperties {
    return when (type) {
        UIBlockType.BUTTON -> BlockProperties.ButtonProperties()
        UIBlockType.COMPONENT -> BlockProperties.ViewProperties()
        UIBlockType.TEXT -> BlockProperties.TextProperties()
        UIBlockType.IMAGE -> BlockProperties.ImageProperties()
        UIBlockType.LIST -> BlockProperties.ListProperties()
        UIBlockType.SPINE -> BlockProperties.SpineProperties()
        UIBlockType.COMBO_BOX -> BlockProperties.ComboBoxProperties()
        UIBlockType.PROGRESS_BAR -> BlockProperties.ProgressBarProperties()
        UIBlockType.POPUP_MENU -> BlockProperties.ComboBoxProperties()
        UIBlockType.LOADER -> BlockProperties.LoaderProperties()
        UIBlockType.SCROLL_BAR -> BlockProperties.ScrollBarProperties()
        UIBlockType.SLIDER -> BlockProperties.SliderProperties()
        UIBlockType.INPUT -> BlockProperties.InputProperties()
    }
}
