package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.model.ui.createDefaultPropertiesForType
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * UI Studio 专属组件配置分发总入口卡片。
 * 监听选中的 [UIStudioBlock]，根据其类型 [UIBlockType] 动态呈现各组件专属的属性表单，
 * 修改即时通知外部 [onPropertiesChanged] 回调。
 *
 * @param block 当前选中的图元对象
 * @param onPropertiesChanged 属性修改回调
 * @param availablePages 当前项目中可选的页面列表
 * @param draggedPageId 当前正在拖拽的视图页面 ID
 * @param dragCursorPosition 当前拖拽光标在全局窗口中的坐标
 * @param modifier 外部修饰符
 */
@Composable
fun UIStudioSpecificProperties(
    block: UIStudioBlock,
    onPropertiesChanged: (BlockProperties) -> Unit,
    availablePages: List<String> = emptyList(),
    draggedPageId: String? = null,
    dragCursorPosition: androidx.compose.ui.geometry.Offset? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val currentProps = block.properties ?: createDefaultPropertiesForType(block.type)

    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.inspectorRowSpacing),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 依据组件类型分发具体的配置表单
        when (block.type) {
                UIBlockType.LIST -> {
                    val listProps = currentProps as? BlockProperties.ListProperties
                        ?: BlockProperties.ListProperties()
                    ListPropertiesSection(
                        properties = listProps,
                        onPropertiesChanged = onPropertiesChanged,
                        availablePages = availablePages,
                        draggedPageId = draggedPageId,
                        dragCursorPosition = dragCursorPosition
                    )
                }

                UIBlockType.BUTTON -> {
                    val btnProps = currentProps as? BlockProperties.ButtonProperties
                        ?: BlockProperties.ButtonProperties()
                    ButtonPropertiesSection(
                        properties = btnProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.TEXT -> {
                    val textProps = currentProps as? BlockProperties.TextProperties
                        ?: BlockProperties.TextProperties()
                    TextPropertiesSection(
                        properties = textProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.INPUT -> {
                    val inputProps = currentProps as? BlockProperties.InputProperties
                        ?: BlockProperties.InputProperties()
                    InputPropertiesSection(
                        properties = inputProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.PROGRESS_BAR -> {
                    val barProps = currentProps as? BlockProperties.ProgressBarProperties
                        ?: BlockProperties.ProgressBarProperties()
                    ProgressBarPropertiesSection(
                        properties = barProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.SLIDER -> {
                    val sliderProps = currentProps as? BlockProperties.SliderProperties
                        ?: BlockProperties.SliderProperties()
                    SliderPropertiesSection(
                        properties = sliderProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.COMBO_BOX, UIBlockType.POPUP_MENU -> {
                    val comboProps = currentProps as? BlockProperties.ComboBoxProperties
                        ?: BlockProperties.ComboBoxProperties()
                    ComboBoxPropertiesSection(
                        properties = comboProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.LOADER -> {
                    val loaderProps = currentProps as? BlockProperties.LoaderProperties
                        ?: BlockProperties.LoaderProperties()
                    LoaderPropertiesSection(
                        properties = loaderProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.SPINE -> {
                    val spineProps = currentProps as? BlockProperties.SpineProperties
                        ?: BlockProperties.SpineProperties()
                    SpinePropertiesSection(
                        properties = spineProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.SCROLL_BAR -> {
                    val scrollProps = currentProps as? BlockProperties.ScrollBarProperties
                        ?: BlockProperties.ScrollBarProperties()
                    ScrollBarPropertiesSection(
                        properties = scrollProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.COMPONENT -> {
                    val compProps = currentProps as? BlockProperties.ViewProperties
                        ?: BlockProperties.ViewProperties()
                    ComponentPropertiesSection(
                        properties = compProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }

                UIBlockType.IMAGE -> {
                    val imgProps = currentProps as? BlockProperties.ImageProperties
                        ?: BlockProperties.ImageProperties()
                    ImagePropertiesSection(
                        properties = imgProps,
                        onPropertiesChanged = onPropertiesChanged
                    )
                }
        }
    }
}
