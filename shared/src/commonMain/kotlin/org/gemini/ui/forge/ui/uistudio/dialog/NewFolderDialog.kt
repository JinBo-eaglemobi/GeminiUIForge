package org.gemini.ui.forge.ui.uistudio.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 新建素材文件夹弹窗
 *
 * 用于在素材库中新建分类文件夹，支持指定文件夹名称，
 * 并同步在本地磁盘工程目录下创建物理文件夹。
 *
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认创建回调 (文件夹名称)
 */
@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (folderName: String) -> Unit
) {
    val spacing = LocalAppSpacing.current
    var folderName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
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
                Text(
                    text = "新建素材文件夹",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "创建用于分类存放切片与图元素材的文件夹，本地磁盘将同步创建对应物理目录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称 (如 icons, buttons)") },
                    placeholder = { Text("请输入文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

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
                        onClick = { onConfirm(folderName.trim()) },
                        enabled = folderName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tip("确认创建文件夹并同步磁盘")
                    ) {
                        Text("创建文件夹")
                    }
                }
            }
        }
    }
}
