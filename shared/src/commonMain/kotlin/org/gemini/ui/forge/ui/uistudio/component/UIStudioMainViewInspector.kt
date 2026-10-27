package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIStudioPage
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

/**
 * UI Studio 主视图（页面）属性检查器组件
 *
 * 当画布未选中任何具体组件或素材时默认展示，呈现当前激活视图的基础规格、尺寸分辨率、组件统计与画布操作指引。
 * 支持用户直接输入修改视图的宽度与高度（实时持久化），并提供常用游戏分辨率预设。
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun UIStudioMainViewInspector(
    view: UIStudioPage,
    viewModel: UIStudioViewModel,
    templateRepo: TemplateRepository,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val scrollState = rememberScrollState()
    val state = viewModel.state

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // 1. 视图名称与快速重命名卡片
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ViewQuilt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "当前页面视图",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = { viewModel.startRenameView(view) },
                        modifier = Modifier
                            .size(28.dp)
                            .tip("重命名视图")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = view.nameStr,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 2. 页面尺寸动态编辑 (W 与 H)
        Text(
            text = "页面分辨率与尺寸",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.outline
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.formRowSpacing)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.formColumnSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var widthText by remember(view.id, view.width) {
                        mutableStateOf(view.width.toInt().toString())
                    }
                    NumberOutlinedTextField(
                        value = widthText,
                        onValueChange = { input ->
                            widthText = input
                            val newW = input.toFloatOrNull()
                            if (newW != null && newW > 0 && newW != view.width) {
                                viewModel.updatePageDimensions(view.id, newW, view.height, templateRepo)
                            }
                        },
                        label = { Text("宽度 W (px)", fontSize = 11.sp) },
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("页面宽度 W，按住左右滑动或滚动滚轮改值")
                    )

                    var heightText by remember(view.id, view.height) {
                        mutableStateOf(view.height.toInt().toString())
                    }
                    NumberOutlinedTextField(
                        value = heightText,
                        onValueChange = { input ->
                            heightText = input
                            val newH = input.toFloatOrNull()
                            if (newH != null && newH > 0 && newH != view.height) {
                                viewModel.updatePageDimensions(view.id, view.width, newH, templateRepo)
                            }
                        },
                        label = { Text("高度 H (px)", fontSize = 11.sp) },
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("页面高度 H，按住左右滑动或滚动滚轮改值")
                    )
                }

                // 快捷分辨率预设
                Text(
                    text = "快捷分辨率预设：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        "1280×720" to (1280f to 720f),
                        "1920×1080" to (1920f to 1080f),
                        "720×1280" to (720f to 1280f),
                        "1080×1920" to (1080f to 1920f)
                    )

                    presets.forEach { (label, size) ->
                        val isSelected = view.width == size.first && view.height == size.second
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.updatePageDimensions(view.id, size.first, size.second, templateRepo)
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "组件/图层总数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "${view.blocks.size} 个",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // 3. 舞台视口缩放百分比调节 (支持滑动改值与一键还原 100%)
                val currentZoomPercent = (state.scale * 100f).roundToInt()
                var zoomText by remember(state.scale) {
                    mutableStateOf(currentZoomPercent.toString())
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NumberOutlinedTextField(
                        value = zoomText,
                        onValueChange = { input ->
                            zoomText = input
                            val percent = input.toFloatOrNull()
                            if (percent != null && percent > 0f) {
                                val targetScale = (percent / 100f).coerceIn(0.1f, 5.0f)
                                if (state.scale > 0f) {
                                    viewModel.onZoom(targetScale / state.scale, androidx.compose.ui.geometry.Offset.Zero)
                                }
                            }
                        },
                        label = { Text("舞台视口缩放 (%)", fontSize = 11.sp) },
                        isFloat = false,
                        modifier = Modifier.weight(1f).tip("舞台视口缩放比例，按住左右滑动或滚动滚轮改值")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                viewModel.resetZoomTo100()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .tip("一键还原舞台为 100% 原始缩放值 (保持当前坐标)")
                    ) {
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // 4. 画布参考底色 (用于直观区隔渲染区域与无限舞台背景)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "画布参考底色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = view.canvasBackgroundColor ?: "#1E293B",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val colorPresets = listOf(
                        "#1E293B" to "经典蓝灰",
                        "#0F172A" to "极夜深黑",
                        "#334155" to "柔和中灰",
                        "#1B3B2B" to "暗夜森林",
                        "#FFFFFF" to "纯白视口"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorPresets.forEach { (hex, name) ->
                            val isCurrent = (view.canvasBackgroundColor ?: "#1E293B").equals(hex, ignoreCase = true)
                            val presetColor = try {
                                val clean = hex.removePrefix("#")
                                val l = clean.toLong(16)
                                if (clean.length == 6) Color(l or 0xFF000000L) else Color(l)
                            } catch (_: Exception) {
                                Color(0xFF1E293B)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(presetColor)
                                    .then(
                                        if (isCurrent) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp)
                                            )
                                        } else {
                                            Modifier.border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                        }
                                    )
                                    .clickable {
                                        viewModel.updatePageCanvasBackgroundColor(view.id, hex, templateRepo)
                                    }
                                    .tip("切换底色: $name ($hex)")
                            )
                        }
                    }
                }
            }
        }

        // 3. 画布操作指引卡片
        Text(
            text = "操作指引",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.outline
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "按住右键拖动：平移主视图画布",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mouse,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "按住左键拖动：移动图层组件实体",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
