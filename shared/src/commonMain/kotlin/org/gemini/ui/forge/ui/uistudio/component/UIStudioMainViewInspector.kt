package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 主视图（页面）属性检查器组件。
 * 全行统一使用 [InspectorPropRow] 实现 0 误差左对齐，尺度与字号动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]。
 */
@Composable
fun UIStudioMainViewInspector(
    view: UIStudioPage,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val dynamicTitleStyle = if (spacing.isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val dynamicChipStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val scrollState = rememberScrollState()
    val state = viewModel.state

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = spacing.small)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // 1. 视图名称流式区块
            InspectorSectionHeader(
                title = "视图页面信息",
                isExpanded = true,
                onToggle = {},
                trailingContent = {
                    AppIconButton(
                        imageVector = Icons.Default.Edit,
                        onClick = { viewModel.canvasEditor.startRenameView(view) },
                        tier = AppIconButtonTier.COMPACT,
                        tint = MaterialTheme.colorScheme.primary,
                        tooltip = "重命名视图"
                    )
                }
            )

            InspectorPropRow(
                label = "当前视图",
                modifier = Modifier.padding(horizontal = spacing.extraSmall)
            ) {
                Text(
                    text = view.id,
                    maxLines = 1,
                    style = dynamicTitleStyle.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // 2. 页面尺寸动态编辑 (W 与 H)
            InspectorSectionHeader(
                title = "页面尺寸与分辨率",
                isExpanded = true,
                onToggle = {}
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.extraSmall)
            ) {
                var widthText by remember(view.id, view.width) {
                    mutableStateOf(view.width.toInt().toString())
                }
                var heightText by remember(view.id, view.height) {
                    mutableStateOf(view.height.toInt().toString())
                }

                InspectorPropRow(label = "分辨率") {
                    NumberOutlinedTextField(
                        value = widthText,
                        onValueChange = { input ->
                            widthText = input
                            val newW = input.toFloatOrNull()
                            if (newW != null && newW > 0 && newW != view.width) {
                                viewModel.pageManager.updatePageDimensions(view.id, newW, view.height, templateRepo)
                            }
                        },
                        prefixTag = "W",
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("页面宽度 W (px)")
                    )
                    NumberOutlinedTextField(
                        value = heightText,
                        onValueChange = { input ->
                            heightText = input
                            val newH = input.toFloatOrNull()
                            if (newH != null && newH > 0 && newH != view.height) {
                                viewModel.pageManager.updatePageDimensions(view.id, view.width, newH, templateRepo)
                            }
                        },
                        prefixTag = "H",
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("页面高度 H (px)")
                    )
                }

                // 快捷分辨率预设
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.extraSmall)
                ) {
                    val presets = listOf(
                        "1280×720" to (1280f to 720f),
                        "1920×1080" to (1920f to 1080f),
                        "720×1280" to (720f to 1280f),
                        "1080×1920" to (1080f to 1920f)
                    )

                    presets.forEach { (label, size) ->
                        val isSelected = view.width == size.first && view.height == size.second
                        val chipShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.inspectorChipHeight)
                                .clip(chipShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = chipShape
                                )
                                .clickable {
                                    viewModel.pageManager.updatePageDimensions(view.id, size.first, size.second, templateRepo)
                                }
                        ) {
                            Text(
                                text = label,
                                style = dynamicChipStyle,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. 舞台控制与统计
            InspectorSectionHeader(
                title = "舞台视口与统计",
                isExpanded = true,
                onToggle = {}
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.extraSmall)
            ) {
                InspectorPropRow(label = "图层总数") {
                    Text(
                        text = "${view.blocks.size} 个图元组件",
                        style = dynamicBodyStyle.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                val currentZoomPercent = (state.scale * 100f).roundToInt()
                var zoomText by remember(state.scale) { mutableStateOf(currentZoomPercent.toString()) }
                InspectorPropRow(label = "舞台缩放") {
                    NumberOutlinedTextField(
                        value = zoomText,
                        onValueChange = { input ->
                            zoomText = input
                            val percent = input.toFloatOrNull()
                            if (percent != null && percent > 0f) {
                                val targetScale = (percent / 100f).coerceIn(0.1f, 5.0f)
                                if (state.scale > 0f) {
                                    viewModel.viewport.onZoom(targetScale / state.scale, androidx.compose.ui.geometry.Offset.Zero)
                                }
                            }
                        },
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("舞台视口缩放比例 (%)")
                    )

                    val btnShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(spacing.inspectorFieldHeight)
                            .clip(btnShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            .clickable { viewModel.viewport.resetZoomTo100() }
                            .tip("一键还原舞台为 100% 原始缩放值")
                    ) {
                        Text(
                            text = "100%",
                            style = dynamicChipStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
