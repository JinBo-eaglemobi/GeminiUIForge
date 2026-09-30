package org.gemini.ui.forge.model.ui

import org.gemini.ui.forge.data.TemplateFile
import kotlinx.serialization.Serializable

/**
 * 页面模型：UI 页面 (UIPage)
 * 包含多个散落 UIBlock 的单张视图环境。例如主游戏界面或 Bonus 奖励界面。
 * @property id 页面的唯一标识符
 * @property nameStr 页面的标题名称
 * @property width 页面逻辑宽度
 * @property height 页面逻辑高度
 * @property sourceImageUri 该页面所关联的原始参考图的本地归档路径
 * @property blocks 页面包含的所有子功能块列表
 */
@Serializable
data class UIPage(
    val id: String,
    val nameStr: String = "Page",
    val width: Float = 1080f,
    val height: Float = 1920f,
    val sourceImageUri: TemplateFile? = null,
    val blocks: List<UIBlock> = emptyList()
) {
    /**
     * 对整页图元执行后置处理与全屏规则自愈
     *
     * 1. 递归触发各子图元的 [UIBlock.postProcess]；
     * 2. 贯彻全屏游戏背景底图规范：将顶层 BACKGROUND 图元的尺寸和裁剪框强制对齐画布尺寸 [width] x [height]。
     *
     * @return 规则校验与自愈处理后的新 [UIPage] 副本
     */
    fun postProcess(): UIPage {
        return copy(
            blocks = blocks.map { block ->
                val processed = block.postProcess()
                if (processed.type == UIBlockType.BACKGROUND && processed.parent == null) {
                    // ★ 规则铁律：背景模块作为全屏背景底图，其大小与坐标恒与屏幕画布大小一致
                    processed.copy(
                        bounds = SerialRect(0f, 0f, width, height),
                        cropRect = processed.cropRect ?: SerialRect(0f, 0f, width, height)
                    )
                } else {
                    processed
                }
            }
        )
    }
}
