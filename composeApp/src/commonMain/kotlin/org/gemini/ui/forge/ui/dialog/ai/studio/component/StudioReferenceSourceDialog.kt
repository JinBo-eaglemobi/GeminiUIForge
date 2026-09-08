package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 视觉工作室参考底图配置中心弹窗
 *
 * 提供原图重新框选（延时按需切图）、本地磁盘选图、初始参考图还原与清除四大专业途径。
 */
@Composable
fun StudioReferenceSourceDialog(
    canCropFromPage: Boolean,
    hasCurrentReference: Boolean,
    hasInitialReference: Boolean,
    onStartRegionSelect: () -> Unit,
    onPickLocalImage: () -> Unit,
    onRestoreInitial: () -> Unit,
    onClearReference: () -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 460.dp, max = 560.dp)
                .wrapContentHeight()
                .padding(spacing.medium),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                // 1. 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "配置与管理参考底图",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).tip("关闭")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "为当前模块指定以图生图或局部微调的基准底图。支持原图自由框选（发送时才物理落盘）或本地选择：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                // 2. 途径列表
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    // 选项 1：在页面原图上自由框选范围
                    ReferenceActionCard(
                        icon = Icons.Default.Crop,
                        title = "在页面原图上重新框选范围",
                        subtitle = if (canCropFromPage) "调起全屏画框工具，自由选取原图局部（内存实时预览，发送时自动按需物理切图）"
                                   else "当前页面未加载原参考底图，暂不可用",
                        enabled = canCropFromPage,
                        isPrimary = true,
                        onClick = {
                            onDismiss()
                            onStartRegionSelect()
                        }
                    )

                    // 选项 2：从电脑本地选择图片
                    ReferenceActionCard(
                        icon = Icons.Default.FolderOpen,
                        title = "从电脑本地选择图片",
                        subtitle = "直接从本地磁盘加载任意 PNG、JPG 或 WebP 外部图片作为参考底图",
                        enabled = true,
                        isPrimary = false,
                        onClick = {
                            onDismiss()
                            onPickLocalImage()
                        }
                    )

                    // 选项 3：还原模块初始参考图（若有）
                    // 选项 3：还原模块初始参考图（点击若无初始图将引导前往原图画框）
                    ReferenceActionCard(
                        icon = Icons.Default.Restore,
                        title = "还原为模块初始参考图",
                        subtitle = if (hasInitialReference) "恢复该模块进入工作台前最初绑定的参考图"
                                   else "该模块尚未设置过初始参考图（点击引导前往原图画框）",
                        enabled = true,
                        isPrimary = false,
                        onClick = {
                            onDismiss()
                            onRestoreInitial()
                        }
                    )

                    // 选项 4：清除当前参考底图（若有）
                    if (hasCurrentReference) {
                        ReferenceActionCard(
                            icon = Icons.Default.Delete,
                            title = "清除参考底图",
                            subtitle = "清空当前参考底图，并切换至纯文字从零生图模式",
                            enabled = true,
                            isDestructive = true,
                            onClick = {
                                onDismiss()
                                onClearReference()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 3. 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = AppShapes.medium
                    ) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * 来源动作交互卡片
 */
@Composable
private fun ReferenceActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .border(
                1.dp,
                if (!enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                else if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                AppShapes.medium
            )
            .clickable(enabled = enabled, onClick = onClick),
        color = if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                else if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = AppShapes.small,
                color = if (!enabled) MaterialTheme.colorScheme.surfaceVariant
                        else if (isPrimary) MaterialTheme.colorScheme.primary
                        else if (isDestructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                               else if (isPrimary) MaterialTheme.colorScheme.onPrimary
                               else if (isDestructive) MaterialTheme.colorScheme.onError
                               else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else if (isDestructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
