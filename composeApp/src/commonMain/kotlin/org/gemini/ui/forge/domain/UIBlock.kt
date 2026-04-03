package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 最小生成单元模型：UI 功能块 (UIBlock)
 * 描述页面上的一个绝对独立和可重新生成的图层。
 * @property id 该模块的全局唯一标识符
 * @property type 该模块所属的功能分类
 * @property bounds 该模块在页面设计稿上的绝对坐标系（由大模型推断）
 * @property currentImageUri 当前选定加载的本地图片路径或远程 URL
 * @property userPrompt 用户在编辑界面为此模块添加的自定义细化 Prompt（如：深海主题、蒸汽朋克风）
 */
@Serializable
data class UIBlock(
    val id: String,
    val type: UIBlockType,
    val bounds: SerialRect,
    val currentImageUri: String? = null,
    val userPrompt: String = ""
) {
    /** 自动拼接基础类别描述与用户自定义描述，形成最终发给生成模型的完整 Prompt */
    val fullPrompt: String
        get() = "${type.defaultPrompt}, $userPrompt"
}
