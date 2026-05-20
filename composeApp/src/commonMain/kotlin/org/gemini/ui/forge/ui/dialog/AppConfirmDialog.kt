package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.gemini.ui.forge.ui.component.tip

/**
 * 通用的操作确认弹窗组件。
 * 默认在标题栏右上角提供一个原生的关闭 (X) 图标，不再使用底部的取消按钮。
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.tip("关闭")) {
                    Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Text(message)
        },
        confirmButton = {
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
    )
}
