package org.gemini.ui.forge.ui.dialog.settings


import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.jetbrains.compose.resources.stringResource


/**
 * 快捷键配置设置区块
 *
 * 允许用户查看并修改应用内各种操作（如撤销、重做、保存等）绑定的快捷键。
 *
 * @param shortcuts 当前快捷键绑定映射，键为操作类型，值为快捷键组合字符串
 * @param onShortcutSaved 快捷键修改保存回调
 */
@Composable
fun ShortcutSettings(
    shortcuts: Map<ShortcutAction, String>,
    onShortcutSaved: (ShortcutAction, String) -> Unit
) {
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp

    SettingSectionTitle(stringResource(Res.string.settings_shortcuts_title))

    shortcuts.forEach { (action, currentKey) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(vertical = LocalAppSpacing.current.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelStr = when (action) {
                ShortcutAction.UNDO -> stringResource(Res.string.shortcut_undo)
                ShortcutAction.REDO -> stringResource(Res.string.shortcut_redo)
                ShortcutAction.SAVE -> stringResource(Res.string.shortcut_save)
                ShortcutAction.RENAME -> stringResource(Res.string.shortcut_rename)
                ShortcutAction.DELETE -> stringResource(Res.string.shortcut_delete)
                ShortcutAction.COPY -> stringResource(Res.string.shortcut_copy)
                ShortcutAction.PASTE -> stringResource(Res.string.shortcut_paste)
                ShortcutAction.CUT -> stringResource(Res.string.shortcut_cut)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(labelStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    action.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            var editingKey by remember(currentKey) { mutableStateOf(currentKey) }

            SelectAllOutlinedTextField(
                value = editingKey,
                onValueChange = {
                    editingKey = it
                    onShortcutSaved(action, it)
                },
                modifier = Modifier.width(160.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                shape = AppShapes.medium
            )
        }
    }
}