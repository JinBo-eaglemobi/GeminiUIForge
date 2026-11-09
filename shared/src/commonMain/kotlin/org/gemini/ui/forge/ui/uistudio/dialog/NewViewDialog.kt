package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import geminiuiforge.composeapp.generated.resources.*
import geminiuiforge.composeapp.generated.resources.Res
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.dialog.rememberDraggableDialogState
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * 视图尺寸预设项内部数据契约
 */
private data class ViewSizePresetItem(
    val nameZh: String,
    val width: Float,
    val height: Float
)

/**
 * 新建视图界面弹窗
 *
 * 用于在当前 UI Studio 工程中添加新的视图界面 (UIStudioPage)，
 * 支持自定义画布宽高（默认带入当前工程尺寸）与快捷预设一键配置。
 *
 * @param defaultWidth 默认画布宽度（通常继承当前工程全局画布宽度）
 * @param defaultHeight 默认画布高度（通常继承当前工程全局画布高度）
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认创建回调 (名称, 宽度, 高度)
 */
@Composable
fun NewViewDialog(
    defaultWidth: Float,
    defaultHeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Float, height: Float) -> Unit
) {
    val spacing = LocalAppSpacing.current

    var viewName by remember { mutableStateOf("") }
    var widthText by remember(defaultWidth) { mutableStateOf(defaultWidth.toInt().toString()) }
    var heightText by remember(defaultHeight) { mutableStateOf(defaultHeight.toInt().toString()) }

    val draggableState = rememberDraggableDialogState()

    val parsedWidth = widthText.toIntOrNull()
    val parsedHeight = heightText.toIntOrNull()
    val isWidthValid = parsedWidth != null && parsedWidth in 100..10000
    val isHeightValid = parsedHeight != null && parsedHeight in 100..10000
    val canConfirm = viewName.isNotBlank() && isWidthValid && isHeightValid

    val presets = remember {
        listOf(
            ViewSizePresetItem("横屏大屏", 1920f, 1080f),
            ViewSizePresetItem("横屏标清", 1280f, 720f),
            ViewSizePresetItem("竖屏高清", 1080f, 1920f),
            ViewSizePresetItem("移动端小屏", 720f, 1280f)
        )
    }

    val doConfirm: () -> Unit = {
        if (canConfirm && parsedWidth != null && parsedHeight != null) {
            onConfirm(viewName.trim(), parsedWidth.toFloat(), parsedHeight.toFloat())
        }
    }

    AppDraggableDialog(
        onDismissRequest = onDismiss,
        state = draggableState
    ) {
        Surface(
            modifier = Modifier.width(spacing.dialogAlertWidth),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // 顶部标题
                Text(
                    text = stringResource(Res.string.new_view_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableDialogHandle(draggableState)
                )

                // 界面名称输入框
                SelectAllOutlinedTextField(
                    value = viewName,
                    onValueChange = { viewName = it },
                    label = { Text(stringResource(Res.string.new_view_name_label)) },
                    placeholder = { Text(stringResource(Res.string.new_view_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    onEnterConfirm = doConfirm
                )

                // 主选项：自定义画布尺寸输入区
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Text(
                        text = stringResource(Res.string.new_view_size_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberOutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = { Text(stringResource(Res.string.new_view_width_label)) },
                            isFloat = false,
                            step = 10f,
                            minVal = 100f,
                            maxVal = 10000f,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            onEnterConfirm = doConfirm
                        )

                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        NumberOutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text(stringResource(Res.string.new_view_height_label)) },
                            isFloat = false,
                            step = 10f,
                            minVal = 100f,
                            maxVal = 10000f,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            onEnterConfirm = doConfirm
                        )
                    }
                }

                // 次级选项：快捷预设配置选择
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Text(
                        text = stringResource(Res.string.new_view_presets_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val matchingPresetIndex = remember(parsedWidth, parsedHeight) {
                        if (parsedWidth != null && parsedHeight != null) {
                            presets.indexOfFirst { it.width.toInt() == parsedWidth && it.height.toInt() == parsedHeight }
                        } else {
                            -1
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        presets.forEachIndexed { index, preset ->
                            val isSelected = index == matchingPresetIndex
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            val border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            } else null

                            Surface(
                                onClick = {
                                    widthText = preset.width.toInt().toString()
                                    heightText = preset.height.toInt().toString()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = containerColor,
                                border = border
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${preset.width.toInt()} × ${preset.height.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = preset.nameZh,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(Res.string.new_view_cancel))
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Button(
                        onClick = doConfirm,
                        enabled = canConfirm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tip(stringResource(Res.string.new_view_confirm_tip))
                    ) {
                        Text(stringResource(Res.string.new_view_confirm))
                    }
                }
            }
        }
    }
}
