package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.model.app.PromptLanguage

/**
 * 最小生成单元模型：UI 功能块 (UIBlock)
 * 描述页面上的一个绝对独立和可重新生成的图层。
 *
 * @property id 该模块的全局唯一标识符
 * @property type 该模块所属的功能分类
 * @property bounds 该模块相对于其直接父容器的局部相对坐标矩形（持久化保存此值）
 * @property currentImageUri 当前选定加载的本地图片路径或远程 URL
 * @property referenceImage 该模块专用的参考底图（裁剪自模板全局参考大图）
 * @property resizeMode 应用于该图片的拉伸缩放模式（如 STRETCH, NINE_PATCH 等）
 * @property ninePatchConfig 应用于该图片的九宫格拉伸安全边距配置
 * @property scaleConfig 应用于图片不透明主体自适应对齐与缩放的微调配置
 * @property cropRect 相对于原始图片的局部裁切区域坐标
 * @property userPromptEn 详细的英文提示词 (High-quality English prompt for image generation, including style, material, and lighting)
 * @property userPromptZh 详细的中文提示词 (对应的详尽中文描述，包含组件功能、设计意图和视觉特征)
 * @property children 嵌套在其内部的子图元列表
 * @property isVisible 图层当前是否在画布和预览中可见
 * @property properties 不同组件分类的专属附加属性（如 ButtonProperties, SpinProperties 等）
 * @property resourceBindingPath 资源绑定与导出的层级结构路径
 * @property isPureContainer 纯容器/占位层标记：true 表示该模块仅用于排版或组合，不参与任何 AI 图片资源生成
 * @property clipOverflow 是否裁剪超出范围的子内容（overflow: hidden），默认 false 为可见
 * @property parent 运行时持有的直接父级引用（@Transient 阻断序列化，copy() 自动继承）
 */
@Stable
@Serializable
data class UIBlock(
    val id: String,
    val type: UIBlockType,
    val bounds: SerialRect,
    val currentImageUri: TemplateFile? = null, // 统一图片资源路径（包含分析裁剪图和用户生成图）
    val referenceImage: TemplateFile? = null, // 新增：该模块专用的参考图（裁剪自模板全局参考图）
    val resizeMode: ImageResizeMode = ImageResizeMode.STRETCH, // 新增：应用于该图片的缩放模式
    val ninePatchConfig: NinePatchConfig = NinePatchConfig(),  // 新增：应用于该图片的九宫格配置
    val scaleConfig: ImageScaleConfig = ImageScaleConfig(), // 新增：应用于图片主体自适应对齐与缩放配置
    val cropRect: SerialRect? = null, // 新增：相对于原始图片的裁剪区域 [0..1] 或绝对像素
    val userPromptEn: String = "",
    val userPromptZh: String = "",
    val children: List<UIBlock> = emptyList(),
    val isVisible: Boolean = true, // 新增：图层是否可见
    val properties: BlockProperties? = null, // 新增：不同类型模块的专属属性
    val resourceBindingPath: List<String> = emptyList(), // 新增：资源绑定层级路径
    val isPureContainer: Boolean = false, // 纯容器/占位层：表示该模块没有任何资源生成，仅占位排版或作为组合层
    val clipOverflow: Boolean = false, // 是否裁剪超出范围的子内容（overflow: hidden），默认 false 为可见
    // ★ 运行时持有直接父级引用，主构造函数声明 + @Transient 阻断 JSON 序列化，copy() 自动继承，永不断裂！
    @Transient
    val parent: UIBlock? = null
) : AssetSupport {

    /**
     * 判断当前模块是否开启了溢出子内容裁剪/隐藏 (overflow: hidden)
     */
    val shouldClipOverflow: Boolean
        get() = clipOverflow || (properties as? BlockProperties.ViewProperties)?.clipOverflow == true

    /**
     * 当前模块在页面全景大图上的全局绝对逻辑矩形 (核心只读计算属性)
     *
     * 性能与数学铁律：
     * 1. 纯二维平移变换，模块宽高绝对恒定，100% 不参与平移计算；
     * 2. 扁平 while 循环直接累加原始浮点数位移，中间过程 0 临时对象分配 (Zero Allocation)。
     *
     * @return 当前模块在整张页面全景大图坐标系下的全局绝对逻辑矩形 [SerialRect]
     */
    val absoluteBounds: SerialRect
        get() {
            var cur = parent
            var ox = 0f
            var oy = 0f
            while (cur != null) {
                ox += cur.bounds.left
                oy += cur.bounds.top
                cur = cur.parent
            }
            val absLeft = bounds.left + ox
            val absTop = bounds.top + oy
            return SerialRect(
                left = absLeft,
                top = absTop,
                right = absLeft + bounds.width,
                bottom = absTop + bounds.height
            )
        }

    /**
     * 无参直接获取全局绝对逻辑矩形（函数式别名，等价于直接访问 [absoluteBounds] 属性）
     *
     * @return 当前模块在全景大图坐标系下的全局绝对逻辑矩形 [SerialRect]
     */
    fun toAbsoluteBounds(): SerialRect = absoluteBounds

    /**
     * 将全局绝对矩形逆向换算回相对于当前模块直接父容器的局部相对矩形 (对称逆向方法)
     *
     * 宽高恒定保持一致，仅做原点平移反算，中间过程 0 临时对象创建。
     *
     * @param absRect 在整页全景大图绝对坐标系下的目标矩形（如用户拖拽、画框选区或画布吸附计算出的绝对矩形）
     * @return 逆向扣除所有父级累计位移后，适合直接持久化写回当前模块 [bounds] 的局部相对矩形 [SerialRect]
     */
    fun toLocalBounds(absRect: SerialRect): SerialRect {
        var cur = parent
        var ox = 0f
        var oy = 0f
        while (cur != null) {
            ox += cur.bounds.left
            oy += cur.bounds.top
            cur = cur.parent
        }
        val relLeft = absRect.left - ox
        val relTop = absRect.top - oy
        return SerialRect(
            left = relLeft,
            top = relTop,
            right = relLeft + absRect.width,
            bottom = relTop + absRect.height
        )
    }

    /** 自动拼接基础类别描述与用户自定义描述，形成最终发给生图模型的完整 Prompt */
    val fullPrompt: String
        get() = "${type.defaultPrompt}, $userPrompt"

    /** 向下兼容字段：优先返回中文描述，无则返回英文 */
    val userPrompt: String
        get() = userPromptZh.ifBlank { userPromptEn }

    /**
     * 对图元树执行后置整理与自愈（如转轴 Reel 组件子级结构重塑）
     *
     * 递归遍历所有子模块，并将 REEL 特殊容器的子图元同步挂入 properties.items 列表。
     *
     * @return 后置处理完毕后的全新 [UIBlock] 副本
     */
    fun postProcess(): UIBlock {
        // 递归处理子级
        val processedChildren = children.map { it.postProcess() }

        return if (type == UIBlockType.REEL) {
            // 如果是转轴且包含子级，同步并入 items 属性中，同时完整保留 children 子图层树，绝不清空
            if (processedChildren.isNotEmpty()) {
                val currentProps = properties as? BlockProperties.ReelProperties ?: BlockProperties.ReelProperties()
                val updatedProps = currentProps.copy(items = currentProps.items + processedChildren)
                copy(properties = updatedProps, cropRect = cropRect, children = processedChildren)
            } else {
                copy(cropRect = cropRect, children = processedChildren)
            }
        } else {
            copy(cropRect = cropRect, children = processedChildren)
        }
    }

    override val assetStates: List<AssetState>
        get() = when (type) {
            UIBlockType.SPIN_BUTTON -> listOf(
                AssetState("默认状态 (Spin)", ""),
                AssetState("停止状态 (Stop)", "/_stop")
            )
            UIBlockType.BUTTON -> listOf(
                AssetState("默认状态 (Normal)", ""),
                AssetState("点击状态 (Pressed)", "/_pressed"),
                AssetState("禁用状态 (Disabled)", "/_disabled")
            )
            else -> emptyList()
        }

    override fun getCurrentImageUri(stateIndex: Int): TemplateFile? {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) currentImageUri else props?.stopUri
            }
            UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties
                when (stateIndex) {
                    0 -> currentImageUri
                    1 -> props?.pressedUri
                    2 -> props?.disabledUri
                    else -> currentImageUri
                }
            }
            else -> currentImageUri
        }
    }

    override fun getHistoricalIdSuffix(stateIndex: Int): String {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                if (stateIndex == 0) "" else "/_stop"
            }
            UIBlockType.BUTTON -> {
                when (stateIndex) {
                    0 -> ""
                    1 -> "/_pressed"
                    2 -> "/_disabled"
                    else -> ""
                }
            }
            else -> ""
        }
    }

    override fun clearImageUri(stateIndex: Int): UIBlock {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                val props = properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
                if (stateIndex == 0) {
                    copy(currentImageUri = null)
                } else {
                    copy(properties = props.copy(stopUri = null))
                }
            }
            UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
                when (stateIndex) {
                    0 -> copy(currentImageUri = null)
                    1 -> copy(properties = props.copy(pressedUri = null))
                    2 -> copy(properties = props.copy(disabledUri = null))
                    else -> copy(currentImageUri = null)
                }
            }
            else -> copy(currentImageUri = null)
        }
    }

    override fun getPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) {
                    if (effectiveLang == PromptLanguage.ZH) userPromptZh else userPromptEn
                } else {
                    if (effectiveLang == PromptLanguage.ZH) props?.stopPromptZh.orEmpty() else props?.stopPromptEn.orEmpty()
                }
            }
            UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties
                when (stateIndex) {
                    0 -> if (effectiveLang == PromptLanguage.ZH) userPromptZh else userPromptEn
                    1 -> props?.pressedPrompt.orEmpty()
                    2 -> props?.disabledPrompt.orEmpty()
                    else -> if (effectiveLang == PromptLanguage.ZH) userPromptZh else userPromptEn
                }
            }
            else -> if (effectiveLang == PromptLanguage.ZH) userPromptZh else userPromptEn
        }
    }

    override fun getOtherPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) {
                    if (effectiveLang == PromptLanguage.ZH) userPromptEn else userPromptZh
                } else {
                    if (effectiveLang == PromptLanguage.ZH) props?.stopPromptEn.orEmpty() else props?.stopPromptZh.orEmpty()
                }
            }
            UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties
                when (stateIndex) {
                    0 -> if (effectiveLang == PromptLanguage.ZH) userPromptEn else userPromptZh
                    1 -> ""
                    2 -> ""
                    else -> if (effectiveLang == PromptLanguage.ZH) userPromptEn else userPromptZh
                }
            }
            else -> if (effectiveLang == PromptLanguage.ZH) userPromptEn else userPromptZh
        }
    }

    override fun updatePrompt(stateIndex: Int, effectiveLang: PromptLanguage, newValue: String): UIBlock {
        return when (type) {
            UIBlockType.SPIN_BUTTON -> {
                val props = properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
                if (stateIndex == 0) {
                    if (effectiveLang == PromptLanguage.ZH) copy(userPromptZh = newValue) else copy(userPromptEn = newValue)
                } else {
                    val updatedProps = if (effectiveLang == PromptLanguage.ZH) {
                        props.copy(stopPromptZh = newValue)
                    } else {
                        props.copy(stopPromptEn = newValue)
                    }
                    copy(properties = updatedProps)
                }
            }
            UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
                when (stateIndex) {
                    0 -> if (effectiveLang == PromptLanguage.ZH) copy(userPromptZh = newValue) else copy(userPromptEn = newValue)
                    1 -> copy(properties = props.copy(pressedPrompt = newValue))
                    2 -> copy(properties = props.copy(disabledPrompt = newValue))
                    else -> if (effectiveLang == PromptLanguage.ZH) copy(userPromptZh = newValue) else copy(userPromptEn = newValue)
                }
            }
            else -> if (effectiveLang == PromptLanguage.ZH) copy(userPromptZh = newValue) else copy(userPromptEn = newValue)
        }
    }
}

/**
 * 资产生成支持协议接口。
 * 允许具有特殊资产状态的组件向生图控制面板提供自适应读写、解绑和提示词配置。
 */
interface AssetSupport {
    /** 资产的状态列表。如果为空表示是普通单态组件 */
    val assetStates: List<AssetState>

    /** 获取当前状态绑定的图片路径 */
    fun getCurrentImageUri(stateIndex: Int): TemplateFile?

    /** 获取当前状态的历史后缀 */
    fun getHistoricalIdSuffix(stateIndex: Int): String

    /** 清除/解绑当前状态的图片，返回更新后的 UIBlock */
    fun clearImageUri(stateIndex: Int): UIBlock

    /** 获取当前状态指定语言的提示词 */
    fun getPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String

    /** 获取当前状态非选中语言的提示词（作 placeholder） */
    fun getOtherPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String

    /** 更新当前状态指定语言的提示词，返回更新后的 UIBlock */
    fun updatePrompt(stateIndex: Int, effectiveLang: PromptLanguage, newValue: String): UIBlock
}

/**
 * 资产状态配置信息数据类
 *
 * @property name 状态友好显示名称（如"默认状态"、"按下状态"、"禁用状态"）
 * @property historicalIdSuffix 生成资产历史库与报文归档时使用的路径标识后缀
 */
data class AssetState(
    val name: String,
    val historicalIdSuffix: String
)

/**
 * 获取用于切片、采样或参考区域核验的有效边界矩形（遵循无损回退与安全 Padding 规范）。
 * 1. 若图元已显式指定专属 cropRect，则尊重并直接返回 cropRect（不附加额外 padding）；
 * 2. 若 cropRect 为 null（缺失）：兼容回退使用该图元的全局绝对 bounds，并自动向四周外扩 [fallbackPadding] 安全边距，
 *    同时自动约束在画布视口 [0, 0, canvasWidth, canvasHeight] 物理范围内，确保发光边框、立体外壳与边缘阴影完整不被截断。
 */
fun UIBlock.getEffectiveCropBounds(
    canvasWidth: Float = Float.MAX_VALUE,
    canvasHeight: Float = Float.MAX_VALUE,
    fallbackPadding: Float = 24f
): SerialRect {
    val explicit = this.cropRect
    if (explicit != null) {
        return explicit
    }
    val abs = this.toAbsoluteBounds()
    val paddedLeft = (abs.left - fallbackPadding).coerceAtLeast(0f)
    val paddedTop = (abs.top - fallbackPadding).coerceAtLeast(0f)
    val paddedRight = (abs.right + fallbackPadding).coerceAtMost(canvasWidth)
    val paddedBottom = (abs.bottom + fallbackPadding).coerceAtMost(canvasHeight)
    return SerialRect(paddedLeft, paddedTop, paddedRight, paddedBottom)
}

