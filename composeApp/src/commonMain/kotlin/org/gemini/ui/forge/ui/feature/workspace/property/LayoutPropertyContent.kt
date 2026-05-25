package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.feature.workspace.BlockSpecificProperties
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.looseJson
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.KeyboardType
import org.gemini.ui.forge.ui.feature.workspace.CollapsibleSection
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.ui.component.ToastType
/**
 * 渲染布局编辑相关的属性内容。
 * 包含页面设置、批量生成入口、模块物理坐标、ID、类型切换及删除操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutPropertyContent(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String
) {
    val selectedBlock = state.selectedBlock

    var showBindingDialog by remember { mutableStateOf(false) }
    var configData by remember { mutableStateOf<List<ResourceItem>?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.resourceConfigPath, state.resourceConfigRefreshTrigger) {
        val path = state.resourceConfigPath
        if (path.isNullOrBlank()) {
            configData = null
            configError = null
        } else {
            try {
                val bytes = org.gemini.ui.forge.data.readBytesInternal(path)
                if (bytes != null) {
                    val content = bytes.decodeToString()
                    val parsed = looseJson.decodeFromString<List<ResourceItem>>(content)
                    configData = parsed
                    configError = null
                    if (state.resourceConfigRefreshTrigger > 0L) {
                        Toast.show("配置文件刷新成功", ToastType.SUCCESS)
                    }
                } else {
                    configData = null
                    configError = "无法读取配置文件"
                    if (state.resourceConfigRefreshTrigger > 0L) {
                        Toast.show("配置文件刷新失败: 无法读取文件", ToastType.ERROR)
                    }
                }
            } catch (e: Exception) {
                configData = null
                configError = e.message ?: "解析失败"
                if (state.resourceConfigRefreshTrigger > 0L) {
                    Toast.show("配置文件刷新失败: ${e.message ?: "解析失败"}", ToastType.ERROR)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedBlock == null) {
            val collapsedSet = state.collapsedSections["global"] ?: emptySet()
            // 1. 未选中模块时显示页面级属性
            state.currentPage?.let { page ->
                CollapsibleSection(
                    title = "页面与属性",
                    expanded = "页面与属性" !in collapsedSet,
                    onToggle = { viewModel.toggleSectionCollapsed("global", "页面与属性", !it) }
                ) {
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

                CollapsibleSection(
                    title = "AI 辅助高级功能",
                    expanded = "AI 辅助高级功能" !in collapsedSet,
                    onToggle = { viewModel.toggleSectionCollapsed("global", "AI 辅助高级功能", !it) }
                ) {
                    // AI 辅助全局功能
                    Button(
                        onClick = { viewModel.showVisualRefine(null) },
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
            val blockCollapsedSet = state.collapsedSections[selectedBlock.id] ?: emptySet()
            CollapsibleSection(
                title = "基础物理属性",
                expanded = "基础物理属性" !in blockCollapsedSet,
                onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "基础物理属性", !it) }
            ) {
                // ID 编辑
                SelectAllOutlinedTextField(
                    value = selectedBlock.id,
                    onValueChange = { if (it.isNotBlank()) viewModel.layoutEditor.renameBlock(selectedBlock.id, it) },
                    label = { Text(stringResource(Res.string.prop_block_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppShapes.medium
                )

                // 绑定导出资源规范组件
                val currentConfig = configData
                val currentError = configError
                if (!state.resourceConfigPath.isNullOrBlank()) {
                    if (currentError != null) {
                        SelectionContainer {
                            Text(
                                text = "加载配置文件失败:\n$currentError",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    if (currentConfig != null) {
                        val bindingPath = selectedBlock.resourceBindingPath
                        val hasBinding = bindingPath.isNotEmpty()
                        
                        val firstInvalidInfo = remember(bindingPath, currentConfig) {
                            org.gemini.ui.forge.utils.ResourceBindingValidator.findFirstInvalidKey(bindingPath, currentConfig)
                        }
                        val isBindingInvalid = hasBinding && firstInvalidInfo != null

                        LaunchedEffect(isBindingInvalid, firstInvalidInfo, bindingPath) {
                            if (isBindingInvalid && firstInvalidInfo != null) {
                                org.gemini.ui.forge.utils.AppLogger.w(
                                    "ResourceBinding",
                                    "检测到模块 (ID: ${selectedBlock.id}) 的资源绑定失效：未能在最新 JSON 配置中定位到层级[${firstInvalidInfo.first}]的Key[\"${firstInvalidInfo.second}\"]。当前完整路径为：${bindingPath.joinToString(" -> ")}"
                                )
                            }
                        }

                        val lastDescription = remember(bindingPath, currentConfig) {
                            if (hasBinding && !isBindingInvalid) {
                                var currentItems = currentConfig
                                var foundItem: ResourceItem? = null
                                for (key in bindingPath) {
                                    foundItem = currentItems?.find { it.key == key }
                                    currentItems = foundItem?.child
                                }
                                foundItem?.description
                            } else null
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(Res.string.res_binding_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBindingInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.refreshResourceConfig() },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .tip("重新读取并解析最新的配置表"),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "刷新配置",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { showBindingDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tip(lastDescription ?: stringResource(Res.string.res_binding_btn_tip_placeholder)),
                                shape = AppShapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isBindingInvalid) {
                                        MaterialTheme.colorScheme.error
                                    } else if (hasBinding) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (isBindingInvalid) Icons.Default.Warning else if (hasBinding) Icons.Default.Link else Icons.Default.LinkOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (hasBinding) stringResource(Res.string.res_binding_btn_bound, bindingPath.joinToString(" > ")) 
                                           else stringResource(Res.string.res_binding_btn_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }

                            if (isBindingInvalid && firstInvalidInfo != null) {
                                Text(
                                    text = "⚠️ 绑定资源失效 (找不到: \"${firstInvalidInfo.second}\")",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        if (showBindingDialog) {
                            val parentBlock = remember(selectedBlock, state.currentPage?.blocks) {
                                state.currentPage?.blocks?.let { findClosestBoundAncestor(it, selectedBlock.id) }
                            }
                            org.gemini.ui.forge.ui.dialog.ResourceBindingDialog(
                                block = selectedBlock,
                                parentBlock = parentBlock,
                                configData = currentConfig,
                                onDismiss = { showBindingDialog = false },
                                onConfirm = { finalPath ->
                                    viewModel.updateBlockResourceBinding(selectedBlock.id, finalPath)
                                    showBindingDialog = false
                                }
                            )
                        }
                    }
                }


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
                CollapsibleSection(
                    title = "专属属性配置",
                    expanded = "专属属性配置" !in blockCollapsedSet,
                    onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "专属属性配置", !it) }
                ) {
                    BlockSpecificProperties(
                        blockType = selectedBlock.type,
                        properties = selectedBlock.properties,
                        apiKey = apiKey,
                        viewModel = viewModel,
                        state = state,
                        onShowHistory = { viewModel.showHistoricalDialog(it) },
                        onPropertiesChanged = { viewModel.assetManager.updateBlockProperties(selectedBlock.id, it) }
                    )
                }
            }

            CollapsibleSection(
                title = "高级与破坏性操作",
                expanded = "高级与破坏性操作" !in blockCollapsedSet,
                onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "高级与破坏性操作", !it) }
            ) {
                // AI 结构重塑与参考区域
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.showVisualRefine(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("通过 AI 自动 analysis 并重塑该模块的内部层级结构")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("区域重塑")
                    }

                    OutlinedButton(
                        onClick = { viewModel.showReferenceArea(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("从原图中截取局部区域作为该模块的 AI 生成参考图")
                    ) {
                        Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("参考区域")
                    }
                }

                // 删除模块
                Button(
                    onClick = { viewModel.showDeleteConfirmation(selectedBlock.id) },
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

@Composable
private fun EditableInfoItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    org.gemini.ui.forge.ui.component.NumberOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier.tip("输入数字以精确调整坐标或尺寸"),
        isFloat = true
    )
}

/**
 * 递归辅助寻找指定 blockId 在 blocks 列表中的所有祖先节点（从根到直接父节点）。
 */
private fun findAncestorChain(blocks: List<UIBlock>, targetId: String, currentChain: List<UIBlock> = emptyList()): List<UIBlock>? {
    for (block in blocks) {
        if (block.id == targetId) {
            return currentChain
        }
        val found = findAncestorChain(block.children, targetId, currentChain + block)
        if (found != null) {
            return found
        }
    }
    return null
}

/**
 * 寻找指定 blockId 的最近一个已绑定资源的祖先节点。
 * 如果所有祖先节点都没有绑定资源，则返回其直接父节点（或 null）。
 */
private fun findClosestBoundAncestor(blocks: List<UIBlock>, targetId: String): UIBlock? {
    val chain = findAncestorChain(blocks, targetId) ?: return null
    // 从最近的父级（列表尾部）开始向上寻找
    for (ancestor in chain.reversed()) {
        if (ancestor.resourceBindingPath.isNotEmpty()) {
            return ancestor
        }
    }
    // 如果没有任何祖先绑定了资源，则回退/兼容至直接父节点
    return chain.lastOrNull()
}

