package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable

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
) {
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
                val updatedProps = currentProps.copy(items = currentProps.items + processedChildren.onEach { })
                copy(properties = updatedProps, children = emptyList())
            } else {
                // 没有子级说明已经解析过，或者是一个空的 REEL，无需覆盖原有属性
                copy(children = processedChildren)
            }
        } else {
            copy(children = processedChildren)
        }
    }
}
