package org.gemini.ui.forge.model.ui

import kotlinx.serialization.Serializable

import org.gemini.ui.forge.data.TemplateFile

/**
 * 定义不同 UI 组件特有的属性配置模型
 */
@Serializable
sealed class BlockProperties {

    /**
     * 按钮组件属性
     * @param isMultiState 是否生成多态图片（正常、点击、禁用）
     * @param pressedUri 点击态图片的存储路径
     * @param disabledUri 禁用态图片的存储路径
     */
    @Serializable
    data class ButtonProperties(
        val isMultiState: Boolean = false,
        val pressedUri: TemplateFile? = null,
        val disabledUri: TemplateFile? = null
    ) : BlockProperties()

    /**
     * 视图/容器组件属性
     * @param backgroundColor 背景颜色 (例如 "#FFFFFF")
     */
    @Serializable
    data class ViewProperties(
        val backgroundColor: String = ""
    ) : BlockProperties()

    /**
     * 文本组件属性
     * @param text 文本内容
     * @param textColor 字体颜色
     * @param textSize 字体大小
     */
    @Serializable
    data class TextProperties(
        val text: String = "",
        val textColor: String = "#000000",
        val textSize: Int = 14
    ) : BlockProperties()

    /**
     * 输入框组件属性
     * @param hintText 默认提示文案
     * @param textColor 字体颜色
     * @param textSize 字体大小
     * @param maxLength 输入最大长度限制，-1表示无限制
     */
    @Serializable
    data class InputProperties(
        val hintText: String = "",
        val textColor: String = "#000000",
        val textSize: Int = 14,
        val maxLength: Int = -1
    ) : BlockProperties()
}
