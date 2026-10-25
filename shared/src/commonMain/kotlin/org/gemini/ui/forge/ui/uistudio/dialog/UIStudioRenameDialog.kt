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
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.dialog.rememberDraggableDialogState
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * UI Studio 通用重命名弹窗组件。
 *
 * 支持对素材、文件夹及视图界面进行名称编辑。
 *
 * @param title 弹窗标题
 * @param initialName 初始名称
 * @param onConfirm 确认修改回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun UIStudioRenameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    var nameInput by remember(initialName) { mutableStateOf(initialName) }
    val draggableState = rememberDraggableDialogState()

    AppDraggableDialog(
        onDismissRequest = onDismiss,
        state = draggableState
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(spacing.dialogAlertWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableDialogHandle(draggableState)
                )

                SelectAllOutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                onConfirm(nameInput.trim())
                            }
                        },
                        enabled = nameInput.isNotBlank() && nameInput != initialName
                    ) {
                        Text("确认修改")
                    }
                }
            }
        }
    }
}
