package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable
import org.gemini.ui.forge.model.app.PromptLanguage

/**
 * 最小生成单元模型：UI 功能块 (UIBlock)
 * 描述页面上的一个绝对独立和可重新生成的图层。
 * @property id 该模块的全局唯一标识符
 * @property type 该模块所属的功能分类
 * @property bounds 该模块在页面设计稿上的绝对坐标系（由大模型推断）
 * @property currentImageUri 当前选定加载的本地图片路径或远程 URL
 * @property userPromptEn 详细的英文提示词 (High-quality English prompt for image generation, including style, material, and lighting)
 * @property userPromptZh 详细的中文提示词 (对应的详尽中文描述，包含组件功能、设计意图和视觉特征)
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
    val cropRect: SerialRect? = null, // 新增：相对于原始图片的裁剪区域 [0..1] 或绝对像素
    val userPromptEn: String = "",
    val userPromptZh: String = "",
    val children: List<UIBlock> = emptyList(),
    val isVisible: Boolean = true, // 新增：图层是否可见
    val properties: BlockProperties? = null, // 新增：不同类型模块的专属属性
    val resourceBindingPath: List<String> = emptyList() // 新增：资源绑定层级路径
) : AssetSupport {
    /** 自动拼接基础类别描述与用户自定义描述，形成最终发给生图模型的完整 Prompt */
    val fullPrompt: String
        get() = "${type.defaultPrompt}, $userPrompt"

    /** 向下兼容字段：优先返回中文描述，无则返回英文 */
    val userPrompt: String
        get() = userPromptZh.ifBlank { userPromptEn }


    fun postProcess(): UIBlock {
        // 递归处理子级
        val processedChildren = children.map { it.postProcess() }

        return if (type == UIBlockType.REEL) {
            // 如果是转轴且包含子级，则将其子级直接作为 items 并入属性中，然后清空子级
            if (processedChildren.isNotEmpty()) {
                val currentProps = properties as? BlockProperties.ReelProperties ?: BlockProperties.ReelProperties()
                // 直接使用 processedChildren 作为新的 items
                val updatedProps = currentProps.copy(items = currentProps.items + processedChildren)
                copy(properties = updatedProps, children = emptyList())
            } else {
                // 没有子级说明已经解析过，或者是一个空的 REEL，无需覆盖原有属性
                copy(children = processedChildren)
            }
        } else {
            copy(children = processedChildren)
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
 * 资产状态配置信息数据类。
 */
data class AssetState(
    val name: String,
    val historicalIdSuffix: String
)

