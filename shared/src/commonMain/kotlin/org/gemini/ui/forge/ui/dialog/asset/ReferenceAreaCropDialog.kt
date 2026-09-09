package org.gemini.ui.forge.ui.dialog.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.component.selector.RegionImageSource
import org.gemini.ui.forge.ui.component.selector.UniversalImageRegionSelector
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 设置区域参考图对话框（基于 UniversalImageRegionSelector 通用选区组件升级版）。
 *
 * 允许用户在指定的参考图片上框选一块区域，这块区域将用于后续的视觉重塑或局部生图参考。
 *
 * 核心特性：
 * 1. 接入通用 [UniversalImageRegionSelector]：提供 8 方向独立控制手柄、Alt 对称缩放与三分法参考线；
 * 2. 实时尺寸 HUD 胶囊展示；
 * 3. 严格遵循 [LocalAppSpacing] 间距体系与 [Modifier.tip] 悬浮提示规范。
 *
 * @param blockId 目标模块唯一标识符
 * @param imageUri 用作参考底图的文件对象
 * @param pageWidth 显示底图的画布区域宽度
 * @param pageHeight 显示底图的画布区域高度
 * @param onDismiss 点击取消时的回调
 * @param onConfirm 确认框选区域时的回调，返回相对坐标的 SerialRect
 */
@Composable
fun ReferenceAreaCropDialog(
    blockId: String,
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (SerialRect) -> Unit
) {
    // 当前框选的矩形区域状态
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    val spacing = LocalAppSpacing.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 顶部标题与说明
                Text(
                    text = "设置区域参考图 - 模块: $blockId",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "框选当前模块在原图上的对应区域，生成时将仅以该区域作为参考图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(spacing.small))

                // 尺寸统计与选区信息条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.small)
                        .padding(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("底图尺寸: ${pageWidth.toInt()} x ${pageHeight.toInt()}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(spacing.medium))

                    val rect = selectedRect
                    if (rect != null) {
                        val selW = abs(rect.width).toInt()
                        val selH = abs(rect.height).toInt()
                        Text(
                            text = "当前选区大小: $selW x $selH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "当前选区大小: 未选择（在底图上任意拖拽即可拉出选区）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 一键整图全选按钮
                    OutlinedButton(
                        onClick = {
                            selectedRect = SerialRect(0f, 0f, pageWidth, pageHeight)
                        },
                        shape = AppShapes.small,
                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .tip(stringResource(Res.string.btn_select_full_image_tip))
                    ) {
                        Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text(stringResource(Res.string.btn_select_full_image), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 通用图片选区组件 (UniversalImageRegionSelector)
                UniversalImageRegionSelector(
                    imageSource = imageUri?.let { RegionImageSource.FromFile(it) },
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    initialRect = selectedRect,
                    showThirdsGrid = true,
                    showDimensionBadge = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(AppShapes.medium),
                    onSelectionChange = { selectedRect = it },
                    onSelectionConfirmed = { selectedRect = it }
                )

                Spacer(Modifier.height(spacing.medium))

                // 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = AppShapes.medium,
                        modifier = Modifier.tip("取消操作")
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(spacing.small))
                    Button(
                        onClick = { selectedRect?.let { onConfirm(it) } },
                        enabled = selectedRect != null,
                        shape = AppShapes.medium,
                        modifier = Modifier.tip("保存当前框选区域为局部参考图")
                    ) {
                        Text("保存局部参考")
                    }
                }
            }
        }
    }
}
