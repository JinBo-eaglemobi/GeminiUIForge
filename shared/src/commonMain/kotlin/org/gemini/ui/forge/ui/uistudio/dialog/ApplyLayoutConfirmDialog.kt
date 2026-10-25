package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.service.studio.UiStudioLayoutService
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.dialog.rememberDraggableDialogState
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * AI 生成界面排版应用确认弹窗
 *
 * 遵循《一文件一 Composable》与 Design Tokens 规范。
 * 允许用户在应用大模型生成的排版时，自由决策：
 * 1. 【替换当前视图】：将新排版覆盖到正在编辑的当前视图；
 * 2. 【作为新视图创建】：保留已有视图，新建独立视图并支持重命名。
 *
 * @param layoutResult 大模型生成的排版结果
 * @param currentViewName 当前正在编辑的视图名称
 * @param onDismiss 放弃应用回调
 * @param onConfirm 确认应用回调 (replaceCurrent: Boolean, customName: String)
 */
@Composable
fun ApplyLayoutConfirmDialog(
    layoutResult: UiStudioLayoutService.ApplyLayoutResult,
    currentViewName: String,
    onDismiss: () -> Unit,
    onConfirm: (replaceCurrent: Boolean, customName: String) -> Unit
) {
    val spacing = LocalAppSpacing.current
    var replaceCurrent by remember { mutableStateOf(false) }
    var customViewName by remember(layoutResult.viewName) {
        mutableStateOf(layoutResult.viewName.ifBlank { "NewView" })
    }
    val draggableState = rememberDraggableDialogState()

    val page = layoutResult.createdPage
    val widthInt = page?.width?.toInt() ?: 1280
    val heightInt = page?.height?.toInt() ?: 720
    val blockCount = layoutResult.blockCount
    val matchedAssetCount = layoutResult.matchedAssetCount

    AppDraggableDialog(
        onDismissRequest = onDismiss,
        state = draggableState
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogConfigWidth)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // 1. 顶部 Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableDialogHandle(draggableState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.apply_layout_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.apply_layout_dialog_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).tip(stringResource(Res.string.apply_layout_btn_cancel))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                // 2. 排版方案概览卡片 (含切片精准拼装统计)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "建议视图: ${layoutResult.viewName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // 拼装切片素材成功率徽章
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (matchedAssetCount > 0) Color(0xFF1B5E20).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (matchedAssetCount > 0) Color(0xFF4CAF50).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = stringResource(Res.string.apply_layout_stats_assets, matchedAssetCount, blockCount),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (matchedAssetCount > 0) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(Res.string.apply_layout_stats_summary, widthInt, heightInt, blockCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // 3. 应用模式单选卡片组
                Text(
                    text = "应用目标选择",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 选项 A: 替换当前视图
                val replaceBorder = if (replaceCurrent) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (replaceCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    border = replaceBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceCurrent = true }
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = replaceCurrent,
                            onClick = { replaceCurrent = true },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(spacing.small))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.apply_layout_mode_replace_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.apply_layout_mode_replace_desc, currentViewName.ifBlank { "当前视图" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 选项 B: 作为新视图创建
                val newBorder = if (!replaceCurrent) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!replaceCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    border = newBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceCurrent = false }
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !replaceCurrent,
                                onClick = { replaceCurrent = false },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(spacing.small))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.apply_layout_mode_new_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(Res.string.apply_layout_mode_new_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 当选择新建视图时，展开新视图名称输入框
                        AnimatedVisibility(
                            visible = !replaceCurrent,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp, top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SelectAllOutlinedTextField(
                                    value = customViewName,
                                    onValueChange = { customViewName = it },
                                    label = { Text(stringResource(Res.string.apply_layout_input_view_name)) },
                                    placeholder = { Text(stringResource(Res.string.apply_layout_input_view_name_placeholder)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 4. 底部行动按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(Res.string.apply_layout_btn_cancel))
                    }
                    Spacer(Modifier.width(spacing.medium))
                    Button(
                        onClick = {
                            val finalName = if (replaceCurrent) currentViewName else customViewName.ifBlank { "NewView" }
                            onConfirm(replaceCurrent, finalName)
                        },
                        enabled = replaceCurrent || customViewName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.apply_layout_btn_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
