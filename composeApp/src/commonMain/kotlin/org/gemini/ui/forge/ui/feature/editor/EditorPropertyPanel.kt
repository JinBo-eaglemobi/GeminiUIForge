package org.gemini.ui.forge.ui.feature.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.TemplateEditorState
import org.gemini.ui.forge.viewmodel.TemplateEditorViewModel
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

/**
 * 属性面板。
 * 接收 ViewModel 以便在内部处理用户对具体属性（ID、坐标、提示词等）的修改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPropertyPanel(
    state: TemplateEditorState,
    viewModel: TemplateEditorViewModel,
    apiKey: String,
    currentLang: PromptLanguage,
    onSwitchLang: (PromptLanguage) -> Unit,
    onRefineClick: (String) -> Unit,
    onSetReferenceAreaClick: (String) -> Unit
) {
    val selectedBlock = state.selectedBlock

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.editor_properties),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedBlock == null) {
            state.currentPage?.let { page ->
                Text(
                    "页面属性 (${page.nameStr})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = AppShapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableInfoItem(
                                label = "宽度 (W)",
                                value = page.width.toInt().toString(),
                                onValueChange = {
                                    val w = it.toFloatOrNull() ?: page.width
                                    viewModel.updatePageSize(w, page.height)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            EditableInfoItem(
                                label = "高度 (H)",
                                value = page.height.toInt().toString(),
                                onValueChange = {
                                    val h = it.toFloatOrNull() ?: page.height
                                    viewModel.updatePageSize(page.width, h)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.stageBackgroundColor,
                            onValueChange = { viewModel.updateStageBackgroundColor(it) },
                            label = { Text("临时背景色 (HEX)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.small,
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true
                        )
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.prop_select_block), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // ID 编辑
            OutlinedTextField(
                value = selectedBlock.id,
                onValueChange = { if (it.isNotBlank()) viewModel.renameBlock(selectedBlock.id, it) },
                label = { Text(stringResource(Res.string.prop_block_id)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                shape = AppShapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // 物理参数编辑组
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "物理坐标与尺寸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditableInfoItem(
                            label = "X",
                            value = selectedBlock.bounds.left.toInt().toString(),
                            onValueChange = { newValue ->
                                val x = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    x,
                                    selectedBlock.bounds.top,
                                    x + selectedBlock.bounds.width,
                                    selectedBlock.bounds.bottom
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "Y",
                            value = selectedBlock.bounds.top.toInt().toString(),
                            onValueChange = { newValue ->
                                val y = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    y,
                                    selectedBlock.bounds.right,
                                    y + selectedBlock.bounds.height
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditableInfoItem(
                            label = "W",
                            value = selectedBlock.bounds.width.toInt().toString(),
                            onValueChange = { newValue ->
                                val w = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    selectedBlock.bounds.top,
                                    selectedBlock.bounds.left + w,
                                    selectedBlock.bounds.bottom
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditableInfoItem(
                            label = "H",
                            value = selectedBlock.bounds.height.toInt().toString(),
                            onValueChange = { newValue ->
                                val h = newValue.toFloatOrNull() ?: return@EditableInfoItem
                                viewModel.updateBlockBounds(
                                    selectedBlock.id,
                                    selectedBlock.bounds.left,
                                    selectedBlock.bounds.top,
                                    selectedBlock.bounds.right,
                                    selectedBlock.bounds.top + h
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 组件类型切换
            var expanded by remember { mutableStateOf(false) }
            Text(
                stringResource(Res.string.prop_type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = stringResource(selectedBlock.type.getDisplayNameRes()),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    shape = AppShapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    UIBlockType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.getDisplayNameRes())) },
                            onClick = { viewModel.updateBlockType(selectedBlock.id, type); expanded = false }
                        )
                    }
                }
            }

            // 提示词多语言编辑
            Text(
                text = stringResource(Res.string.editor_prompt_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = currentLang == lang,
                        onClick = { onSwitchLang(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(lang.displayName) }
                    )
                }
            }

            val displayPrompt =
                if (currentLang == PromptLanguage.EN) selectedBlock.userPromptEn else selectedBlock.userPromptZh
            OutlinedTextField(
                value = displayPrompt,
                onValueChange = { viewModel.onUserPromptChanged(selectedBlock.id, it, currentLang) },
                label = { Text("${stringResource(Res.string.label_description_content)} (${currentLang.displayName})") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                maxLines = 10,
                enabled = !state.isGenerating,
                shape = AppShapes.medium
            )

            // AI 辅助工具
            var useChatContext by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = useChatContext,
                    onCheckedChange = { useChatContext = it }
                )
                Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.optimizePrompt(selectedBlock.id, apiKey, currentLang, useChatContext) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating && displayPrompt.isNotBlank(),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.prop_optimize_prompt))
                }

                OutlinedButton(
                    onClick = { onRefineClick(selectedBlock.id) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating,
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.action_refine_area))
                }
            }
            
            if (state.currentPage?.sourceImageUri != null) {
                OutlinedButton(
                    onClick = { onSetReferenceAreaClick(selectedBlock.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !state.isGenerating,
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp)) // 可以换个图标，如 Crop
                    Spacer(Modifier.width(4.dp))
                    Text("设置区域参考图")
                }
                
                if (selectedBlock.referenceImage != null) {
                    Text(
                        "当前已设置局部参考图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 块删除
            Button(
                onClick = { viewModel.requestDeleteBlock(selectedBlock.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                enabled = !state.isGenerating
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_delete_block))
            }
        }
    }
}

/**
 * 封装的数字输入项
 */
@Composable
fun EditableInfoItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectAllOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isNotEmpty() && newValue.toFloatOrNull() != null) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        maxLines = 1
    )
}
