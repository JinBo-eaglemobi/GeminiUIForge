package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerDialog(onDismiss: () -> Unit, onConfirm: (String, UIBlockType, Float, Float) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(UIBlockType.VIEW) }
    var widthStr by remember { mutableStateOf("200") }
    var heightStr by remember { mutableStateOf("200") }
    var expandedType by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加新图层") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("图层名称/ID (可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }) {
                    OutlinedTextField(
                        value = stringResource(selectedType.getDisplayNameRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模块类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = AppShapes.medium
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        UIBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(type.getDisplayNameRes())) },
                                onClick = { selectedType = type; expandedType = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = { widthStr = it },
                        label = { Text("宽度") },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("高度") },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    name,
                    selectedType,
                    widthStr.toFloatOrNull() ?: 200f,
                    heightStr.toFloatOrNull() ?: 200f
                )
            }, shape = AppShapes.medium) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = AppShapes.medium) { Text("取消") } }
    )
}