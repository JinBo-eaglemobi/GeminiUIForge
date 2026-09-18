package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ai.component.FormattedCodeViewer
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 结构代码审查与左右分栏比对审核窗体 (SideBySideDiffInspectorDialog)
 *
 * 遵循 1280dp 全景宽屏 Tokens (dialogHugeWidth) 与一文件一 Composable 铁律。
 * 提供左右对称代码对比，支持局部段落 vs 全页完整代码自由切换，确保重大结构代码替换 100% 人工可控。
 *
 * @param originalSegmentJson 被替换前的局部图元 JSON 代码
 * @param newSegmentJson 大模型新生成的局部图元 JSON 代码
 * @param originalFullJson 当前页面完整的原版 JSON 代码
 * @param newFullJson 模拟替换完成后的全页完整 JSON 代码
 * @param targetBlockId 目标重塑模块 ID (用于自动高亮与定位)
 * @param onConfirm 确认应用替换回调
 * @param onDismiss 取消/放弃回调
 */
@Composable
fun SideBySideDiffInspectorDialog(
    originalSegmentJson: String,
    newSegmentJson: String,
    originalFullJson: String,
    newFullJson: String,
    targetBlockId: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var isFullPageMode by remember { mutableStateOf(false) }
    val spacing = LocalAppSpacing.current

    val leftCode = if (isFullPageMode) originalFullJson else originalSegmentJson
    val rightCode = if (isFullPageMode) newFullJson else newSegmentJson

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogHugeWidth)
                .heightIn(min = 550.dp, max = 800.dp)
                .fillMaxHeight(0.92f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.medium)
            ) {
                // 1. 顶部标题与视图范围单选切换器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "结构代码审查与差异比对",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "请仔细比对重塑前后的代码结构与属性配置，确认无误后点击应用替换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 范围单选胶囊 (局部代码段 vs 整页完整代码)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !isFullPageMode,
                            onClick = { isFullPageMode = false },
                            label = { Text("局部代码段对比") },
                            shape = AppShapes.small,
                            modifier = Modifier.tip("仅对比受本次区域重塑直接影响的目标图元代码片段")
                        )
                        FilterChip(
                            selected = isFullPageMode,
                            onClick = { isFullPageMode = true },
                            label = { Text("整页完整代码对比") },
                            shape = AppShapes.small,
                            modifier = Modifier.tip("查看并比对融入全页后的完整 JSON 配置代码")
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 2. 左右双分栏代码比对区域
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // 2.1 左栏：替换前的原代码
                    FormattedCodeViewer(
                        code = leftCode,
                        highlightKeyword = targetBlockId,
                        title = if (isFullPageMode) "原页面完整代码 (Current Page)" else "原模块代码 (Current Block)",
                        titleContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    // 2.2 右栏：重塑生成的新代码
                    FormattedCodeViewer(
                        code = rightCode,
                        highlightKeyword = targetBlockId,
                        title = if (isFullPageMode) "即将应用的新页面代码 (Merged Preview)" else "重塑生成新代码 (Generated Block)",
                        titleContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                Spacer(Modifier.height(spacing.medium))

                // 3. 底部操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFullPageMode) "当前视图: 整页全局结构" else "当前视图: 局部模块段落",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = AppShapes.medium,
                            modifier = Modifier.tip("放弃本次重塑结果，保持原工程不变")
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("放弃")
                        }

                        Button(
                            onClick = onConfirm,
                            shape = AppShapes.medium,
                            modifier = Modifier.tip("确认审核无误，将新代码正式替换写入工作区")
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("应用替换 (Apply)")
                        }
                    }
                }
            }
        }
    }
}
