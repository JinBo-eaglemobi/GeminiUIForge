package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.dialog.AppDialogHeader
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全平台通用视口自适应可拖拽操作确认弹窗组件。
 * 标题栏支持鼠标悬浮手型、平滑按住拖拽移动，以及双击瞬时居中复位。
 *
 * @param title 弹窗标题
 * @param message 弹窗的内容文案
 * @param confirmText 确认按钮的文案，默认为 "确认"
 * @param isDestructive 是否为破坏性操作（如删除）。如果为 true，确认按钮将显示为红色警告色。
 * @param onConfirm 用户点击确认按钮的回调
 * @param onDismiss 用户点击关闭按钮或点击弹窗外部的回调
 */
@Composable
@Preview
fun AppConfirmDialog(
    title: String = "",
    message: String = "",
    confirmText: String = "确认",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
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
            modifier = Modifier.width(spacing.dialogConfirmWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.medium)
            ) {
                // 1. 顶部紧凑 48dp 标题栏（集成拖拽抓手、双击居中复位与右侧关闭按钮）
                AppDialogHeader(
                    title = title,
                    state = draggableState,
                    onClose = onDismiss
                )

                // 2. 正文提示文案
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )

                Spacer(modifier = Modifier.height(spacing.small))

                // 3. 底部操作按钮区域（右对齐确认按钮）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onConfirm,
                        colors = if (isDestructive) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}
