package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.import_conflict_keep_both
import geminiuiforge.composeapp.generated.resources.import_conflict_message
import geminiuiforge.composeapp.generated.resources.import_conflict_overwrite
import geminiuiforge.composeapp.generated.resources.import_conflict_skip
import geminiuiforge.composeapp.generated.resources.import_conflict_title
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.ui.component.dialog.AppDialogHeader
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.model.ImportConflictStrategy

/**
 * 外部素材导入重名冲突决策弹窗（对齐市场主流三选一：替换 / 保留两者 / 跳过）。
 * 批量导入时展示全部冲突文件名列表（视口高度受 [AppSpacing.dialogListMaxHeight] 约束），
 * 所选策略统一应用于全部冲突项。
 *
 * @param conflictNames 产生重名冲突的文件名清单
 * @param onResolve 策略决策回调（OVERWRITE / KEEP_BOTH / SKIP）
 * @param onDismiss 关闭或取消弹窗回调
 */
@Composable
fun AppImportConflictDialog(
    conflictNames: List<String>,
    onResolve: (ImportConflictStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current

    AppDraggableDialog(
        onDismissRequest = onDismiss
    ) { draggableState ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.width(spacing.dialogConfigWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.medium)
            ) {
                // 1. 顶部紧凑 48dp 标题栏（集成拖拽抓手、双击居中复位与右侧关闭按钮）
                AppDialogHeader(
                    title = stringResource(Res.string.import_conflict_title),
                    state = draggableState,
                    onClose = onDismiss
                )

                // 2. 正文提示文案
                Text(
                    text = stringResource(Res.string.import_conflict_message, conflictNames.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )

                // 3. 冲突文件滚动列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .heightIn(max = spacing.dialogListMaxHeight)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(spacing.small)
                        .verticalScroll(rememberScrollState())
                ) {
                    conflictNames.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = spacing.extraSmall)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                // 4. 底部操作按钮区域（跳过 | 保留两者 | 替换[警示红]）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onResolve(ImportConflictStrategy.SKIP) }
                    ) {
                        Text(stringResource(Res.string.import_conflict_skip))
                    }

                    Spacer(modifier = Modifier.width(spacing.small))

                    TextButton(
                        onClick = { onResolve(ImportConflictStrategy.KEEP_BOTH) }
                    ) {
                        Text(stringResource(Res.string.import_conflict_keep_both))
                    }

                    Spacer(modifier = Modifier.width(spacing.small))

                    Button(
                        onClick = { onResolve(ImportConflictStrategy.OVERWRITE) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(Res.string.import_conflict_overwrite))
                    }
                }
            }
        }
    }
}
