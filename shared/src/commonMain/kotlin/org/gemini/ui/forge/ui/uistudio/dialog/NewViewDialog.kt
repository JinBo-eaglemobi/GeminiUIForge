package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.dialog.rememberDraggableDialogState
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 新建视图界面弹窗
 *
 * 用于在当前 UI Studio 工程中添加新的视图界面 (UIStudioPage)，
 * 支持自定义视图名称及画布分辨率。
 *
 * @param defaultWidth 默认画布宽度
 * @param defaultHeight 默认画布高度
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
    var selectedResolution by remember { mutableStateOf("${defaultWidth.toInt()} × ${defaultHeight.toInt()}") }
    var customWidth by remember { mutableStateOf(defaultWidth.toInt().toString()) }
    var customHeight by remember { mutableStateOf(defaultHeight.toInt().toString()) }
    val draggableState = rememberDraggableDialogState()

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
                    text = "新建视图界面",
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
                    label = { Text("视图名称 (如 Game_HUD)") },
                    placeholder = { Text("请输入视图唯一英文/中文名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // 分辨率选择
                Text(
                    text = "画布分辨率",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val resolutions = listOf(
                    "${defaultWidth.toInt()} × ${defaultHeight.toInt()} (继承工程)",
                    "1920 × 1080 (横屏大屏)",
                    "1280 × 720 (横屏标清)",
                    "1080 × 1920 (竖屏高清)",
                    "720 × 1280 (移动端小屏)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    resolutions.forEach { resText ->
                        val isSelected = selectedResolution == resText
                        Surface(
                            onClick = { selectedResolution = resText },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall + 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedResolution = resText }
                                )
                                Spacer(modifier = Modifier.width(spacing.extraSmall))
                                Text(
                                    text = resText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
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
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Button(
                        onClick = {
                            val finalWidth = when {
                                selectedResolution.contains("1920 × 1080") -> 1920f
                                selectedResolution.contains("1280 × 720") -> 1280f
                                selectedResolution.contains("1080 × 1920") -> 1080f
                                selectedResolution.contains("720 × 1280") -> 720f
                                else -> defaultWidth
                            }
                            val finalHeight = when {
                                selectedResolution.contains("1920 × 1080") -> 1080f
                                selectedResolution.contains("1280 × 720") -> 720f
                                selectedResolution.contains("1080 × 1920") -> 1920f
                                selectedResolution.contains("720 × 1280") -> 1280f
                                else -> defaultHeight
                            }
                            onConfirm(viewName.trim(), finalWidth, finalHeight)
                        },
                        enabled = viewName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tip("创建该视图并立即切换到画布")
                    ) {
                        Text("创建视图")
                    }
                }
            }
        }
    }
}
