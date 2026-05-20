package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.feature.workspace.BlockSpecificProperties
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.gemini.ui.forge.ui.feature.workspace.CollapsibleSection

/**
 * 渲染布局编辑相关的属性内容。
 * 包含页面设置、批量生成入口、模块物理坐标、ID、类型切换及删除操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutPropertyContent(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    onRefineClick: (String?) -> Unit,
    onSetReferenceAreaClick: (String) -> Unit,
    onDeleteRequest: (String) -> Unit
) {
    val selectedBlock = state.selectedBlock

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedBlock == null) {
            // 1. 未选中模块时显示页面级属性
            state.currentPage?.let { page ->
                CollapsibleSection(title = "页面与属性") {
                    // 页面切换器
                    if (state.project.pages.size > 1) {
                        var pageMenuExpanded by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { pageMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth().tip("点击切换当前编辑的页面"),
                                shape = AppShapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Pages, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("当前页面: ${page.id}", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = pageMenuExpanded,
                                onDismissRequest = { pageMenuExpanded = false },
                                modifier = Modifier.width(260.dp)
                            ) {
                                state.project.pages.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.id, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = { viewModel.switchPage(p.id); pageMenuExpanded = false },
                                        leadingIcon = {
                                            if (p.id == page.id) Icon(
                                                Icons.Default.Check,
                                                null,
                                                Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 物理尺寸与背景
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = AppShapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EditableInfoItem(
                                    label = "宽度 (W)",
                                    value = page.width.toInt().toString(),
                                    onValueChange = { viewModel.updatePageSize(it.toFloat(), page.height) },
                                    modifier = Modifier.weight(1f)
                                )
                                EditableInfoItem(
                                    label = "高度 (H)",
                                    value = page.height.toInt().toString(),
                                    onValueChange = { viewModel.updatePageSize(page.width, it.toFloat()) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SelectAllOutlinedTextField(
                                value = state.stageBackgroundColor,
                                onValueChange = { viewModel.updateStageBackgroundColor(it) },
                                label = { Text("画布背景色 (HEX)", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.small,
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                        }
                    }
                }

                CollapsibleSection(title = "AI 辅助高级功能", defaultExpanded = true) {
                    // AI 辅助全局功能
                    Button(
                        onClick = { onRefineClick(null) },
                        modifier = Modifier.fillMaxWidth().tip("基于 AI 视觉识别重构整个页面的布局结构")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("全局区域重塑")
                    }

                    OutlinedButton(
                        onClick = { viewModel.updateState { it.copy(showBatchGenDialog = true) } },
                        modifier = Modifier.fillMaxWidth().tip("为页面中所有缺失资源的模块自动生成资源图")
                    ) {
                        Icon(Icons.Default.AutoAwesomeMotion, null)
                        Spacer(Modifier.width(8.dp))
                        Text("一键批量生成")
                    }
                }
            } ?: Text("请选择模块", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 2. 选中模块后显示具体物理参数
            CollapsibleSection(title = "基础物理属性") {
                // ID 编辑
                SelectAllOutlinedTextField(
                    value = selectedBlock.id,
                    onValueChange = { if (it.isNotBlank()) viewModel.layoutEditor.renameBlock(selectedBlock.id, it) },
                    label = { Text(stringResource(Res.string.prop_block_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppShapes.medium
                )

                // 物理坐标与尺寸实时输入
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = AppShapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "坐标与尺寸",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableInfoItem(
                                label = "X",
                                value = selectedBlock.bounds.left.toInt().toString(),
                                onValueChange = {
                                    viewModel.updateBlockBounds(
                                        selectedBlock.id,
                                        it.toFloat(),
                                        selectedBlock.bounds.top,
                                        it.toFloat() + selectedBlock.bounds.width,
                                        selectedBlock.bounds.bottom
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            EditableInfoItem(
                                label = "Y",
                                value = selectedBlock.bounds.top.toInt().toString(),
                                onValueChange = {
                                    viewModel.updateBlockBounds(
                                        selectedBlock.id,
                                        selectedBlock.bounds.left,
                                        it.toFloat(),
                                        selectedBlock.bounds.right,
                                        it.toFloat() + selectedBlock.bounds.height
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableInfoItem(
                                label = "W",
                                value = selectedBlock.bounds.width.toInt().toString(),
                                onValueChange = {
                                    viewModel.updateBlockBounds(
                                        selectedBlock.id,
                                        selectedBlock.bounds.left,
                                        selectedBlock.bounds.top,
                                        selectedBlock.bounds.left + it.toFloat(),
                                        selectedBlock.bounds.bottom
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            EditableInfoItem(
                                label = "H",
                                value = selectedBlock.bounds.height.toInt().toString(),
                                onValueChange = {
                                    viewModel.updateBlockBounds(
                                        selectedBlock.id,
                                        selectedBlock.bounds.left,
                                        selectedBlock.bounds.top,
                                        selectedBlock.bounds.right,
                                        selectedBlock.bounds.top + it.toFloat()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 模块类型动态切换
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    SelectAllOutlinedTextField(
                        value = stringResource(selectedBlock.type.getDisplayNameRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.prop_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        shape = AppShapes.medium
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        UIBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(type.getDisplayNameRes())) },
                                onClick = { viewModel.updateBlockType(selectedBlock.id, type); typeExpanded = false })
                        }
                    }
                }
            }

            val hasSpecificProps = selectedBlock.type in listOf(UIBlockType.BUTTON, UIBlockType.VIEW, UIBlockType.TEXT, UIBlockType.INPUT, UIBlockType.REEL)
            if (hasSpecificProps) {
                CollapsibleSection(title = "专属属性配置") {
                    BlockSpecificProperties(
                        blockType = selectedBlock.type,
                        properties = selectedBlock.properties,
                        apiKey = apiKey,
                        viewModel = viewModel,
                        state = state,
                        onPropertiesChanged = { viewModel.assetManager.updateBlockProperties(selectedBlock.id, it) }
                    )
                }
            }

            CollapsibleSection(title = "高级与破坏性操作", defaultExpanded = true) {
                // AI 结构重塑与参考区域
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRefineClick(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("通过 AI 自动分析并重塑该模块的内部层级结构")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("区域重塑")
                    }

                    OutlinedButton(
                        onClick = { onSetReferenceAreaClick(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("从原图中截取局部区域作为该模块的 AI 生成参考图")
                    ) {
                        Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("参考区域")
                    }
                }

                // 删除模块
                Button(
                    onClick = { onDeleteRequest(selectedBlock.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().tip("从项目中永久移除此模块及其子模块"),
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
}

/**
 * 带有数值校验的单行数字输入项。
 */
@Composable
private fun EditableInfoItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectAllOutlinedTextField(
        value = value,
        onValueChange = { if (it.isEmpty() || it.toFloatOrNull() != null) onValueChange(it) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier.tip("输入数字以精确调整坐标或尺寸"),
        shape = AppShapes.small,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
