package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import androidx.compose.runtime.Stable
import org.gemini.ui.forge.model.app.PromptLanguage

/**
 * 最小生成单元模型：UI 功能块 (UIBlock) —— 传统 Slots 模板工程专用图元。
 * 继承公用父类 [BaseBlock]（几何/层级/结构），本类仅承载 Slots 生图链路专属属性
 * （图片路径、参考图、提示词、九宫格等）与 [AssetSupport] 多状态资产协议。
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
 * @property clipOverflow 是否裁剪超出范围的子内容（overflow: hidden），默认 false 为可见
 * @property parent 运行时持有的直接父级引用（@Transient 阻断序列化，copy() 自动继承）
 */
@Stable
@Serializable
data class UIBlock(
    override val id: String,
    override val type: UIBlockType,
    override val bounds: SerialRect,
    val currentImageUri: TemplateFile? = null, // 统一图片资源路径（包含分析裁剪图和用户生成图）
    val referenceImage: TemplateFile? = null, // 新增：该模块专用的参考图（裁剪自模板全局参考图）
    val resizeMode: ImageResizeMode = ImageResizeMode.STRETCH, // 新增：应用于该图片的缩放模式
    val ninePatchConfig: NinePatchConfig = NinePatchConfig(),  // 新增：应用于该图片的九宫格配置
    val scaleConfig: ImageScaleConfig = ImageScaleConfig(), // 新增：应用于图片主体自适应对齐与缩放配置
    override val cropRect: SerialRect? = null, // 新增：相对于原始图片的裁剪区域 [0..1] 或绝对像素
    val userPromptEn: String = "",
    val userPromptZh: String = "",
    override val children: List<UIBlock> = emptyList(),
    override val isVisible: Boolean = true, // 新增：图层是否可见
    override val isLocked: Boolean = false, // 新增：图层是否锁定（锁定后在画布上禁止选中和拖动）
    override val properties: BlockProperties? = null, // 新增：不同类型模块的专属属性
    val resourceBindingPath: List<String> = emptyList(), // 新增：资源绑定层级路径
    override val clipOverflow: Boolean = false, // 是否裁剪超出范围的子内容（overflow: hidden），默认 false 为可见
    override val scaleX: Float = 1.0f, // 独立水平缩放变换因数 (默认 1.0f 为原寸)
    override val scaleY: Float = 1.0f, // 独立垂直缩放变换因数 (默认 1.0f 为原寸)
    override val opacity: Float = 1.0f, // 独立不透明度 (默认 1.0f 为完全不透明，范围 0.0~1.0)
    // ★ 运行时持有直接父级引用，主构造函数声明 + @Transient 阻断 JSON 序列化，copy() 自动继承，永不断裂！
    @Transient
    override val parent: UIBlock? = null
) : BaseBlock(), AssetSupport {

    /**
     * 对图元树执行后置整理与自愈（如列表/转轴组件子级结构重塑）
     */
    fun postProcess(): UIBlock {
        val processedChildren = children.map { it.postProcess() }

        return if (type == UIBlockType.LIST) {
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

    /** 树重建协议：以新子图元列表重建副本（树内类型同质，过滤收敛安全） */
    override fun withChildren(children: List<BaseBlock>): BaseBlock {
        return copy(children = children.filterIsInstance<UIBlock>())
    }

    /** 树重建协议：以新父级引用重建副本（树内类型同质，收敛转换安全） */
    override fun withParent(parent: BaseBlock?): BaseBlock {
        return copy(parent = parent as? UIBlock)
    }

    override val assetStates: List<AssetState>
        get() = when {
            properties is BlockProperties.SpinButtonProperties -> listOf(
                AssetState("默认状态 (Spin)", ""),
                AssetState("停止状态 (Stop)", "/_stop")
            )
            type == UIBlockType.BUTTON -> listOf(
                AssetState("默认状态 (Normal)", ""),
                AssetState("点击状态 (Pressed)", "/_pressed"),
                AssetState("禁用状态 (Disabled)", "/_disabled")
            )
            else -> emptyList()
        }

    override fun getCurrentImageUri(stateIndex: Int): TemplateFile? {
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) currentImageUri else props?.stopUri
            }
            type == UIBlockType.BUTTON -> {
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
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                if (stateIndex == 0) "" else "/_stop"
            }
            type == UIBlockType.BUTTON -> {
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
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                val props = properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
                if (stateIndex == 0) {
                    copy(currentImageUri = null)
                } else {
                    copy(properties = props.copy(stopUri = null))
                }
            }
            type == UIBlockType.BUTTON -> {
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

    /** 自动拼接基础类别描述与用户自定义描述，形成最终发给生图模型的完整 Prompt */
    val fullPrompt: String
        get() = "${type.defaultPrompt}, $userPrompt"

    /** 向下兼容字段：优先返回中文描述，无则返回英文 */
    val userPrompt: String
        get() = userPromptZh.ifBlank { userPromptEn }

    override fun getPrompt(stateIndex: Int, effectiveLang: PromptLanguage): String {
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) {
                    if (effectiveLang == PromptLanguage.ZH) userPromptZh else userPromptEn
                } else {
                    if (effectiveLang == PromptLanguage.ZH) props?.stopPromptZh.orEmpty() else props?.stopPromptEn.orEmpty()
                }
            }
            type == UIBlockType.BUTTON -> {
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
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                val props = properties as? BlockProperties.SpinButtonProperties
                if (stateIndex == 0) {
                    if (effectiveLang == PromptLanguage.ZH) userPromptEn else userPromptZh
                } else {
                    if (effectiveLang == PromptLanguage.ZH) props?.stopPromptEn.orEmpty() else props?.stopPromptZh.orEmpty()
                }
            }
            type == UIBlockType.BUTTON -> {
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
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
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
            type == UIBlockType.BUTTON -> {
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

    fun updateImageUri(stateIndex: Int, newUri: TemplateFile?): UIBlock {
        return when {
            properties is BlockProperties.SpinButtonProperties -> {
                val props = properties as? BlockProperties.SpinButtonProperties ?: BlockProperties.SpinButtonProperties()
                if (stateIndex == 0) {
                    copy(currentImageUri = newUri)
                } else {
                    copy(properties = props.copy(stopUri = newUri))
                }
            }
            type == UIBlockType.BUTTON -> {
                val props = properties as? BlockProperties.ButtonProperties ?: BlockProperties.ButtonProperties()
                when (stateIndex) {
                    0 -> copy(currentImageUri = newUri)
                    1 -> copy(properties = props.copy(pressedUri = newUri))
                    2 -> copy(properties = props.copy(disabledUri = newUri))
                    else -> copy(currentImageUri = newUri)
                }
            }
            else -> copy(currentImageUri = newUri)
        }
    }
}
