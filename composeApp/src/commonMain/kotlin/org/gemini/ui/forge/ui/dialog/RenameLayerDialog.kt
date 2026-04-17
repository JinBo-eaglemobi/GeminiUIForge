package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.gemini.ui.forge.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameLayerDialog(initialId: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newId by remember { mutableStateOf(initialId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名图层") },
        text = {
            OutlinedTextField(
                value = newId,
                onValueChange = { newId = it },
                label = { Text("新 ID/名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.medium
            )
        },
        confirmButton = { Button(onClick = { onConfirm(newId) }, shape = AppShapes.medium) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss, shape = AppShapes.medium) { Text("取消") } })
}
