package org.gemini.ui.forge.ui.feature.gameproject.create.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 目标目录冲突确认弹窗组件。
 * 目标目录已存在时由用户选择处理方式：覆盖重建（删除后全新克隆）/ 继续使用（增量拉取）/ 取消创建。
 * 遵循一文件一 Composable 规范。
 */
@Composable
fun ExistingDirConfirmDialog(
    dirPath: String,
    onOverwrite: () -> Unit,
    onReuse: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.gp_clone_dir_exists_title)) },
        text = {
            Text(
                text = stringResource(Res.string.gp_clone_dir_exists_msg, dirPath),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(stringResource(Res.string.gp_clone_dir_overwrite))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReuse) {
                    Text(stringResource(Res.string.gp_clone_dir_reuse))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.gp_clone_cancel))
                }
            }
        }
    )
}
