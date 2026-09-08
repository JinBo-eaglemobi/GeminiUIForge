package org.gemini.ui.forge.ui.feature.workspace.property

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.readBytesInternal
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.*
import coil3.compose.AsyncImage
import org.gemini.ui.forge.ui.dialog.asset.ImageEditorDialog
import org.gemini.ui.forge.ui.dialog.asset.ResourceBindingDialog
import org.gemini.ui.forge.ui.dialog.ai.BlockRefinementDialog
import org.gemini.ui.forge.ui.dialog.ai.studio.UniversalVisualChatStudioDialog
import org.gemini.ui.forge.ui.dialog.system.AppConfirmDialog
import org.gemini.ui.forge.utils.calculateBlockParentOffset
import org.gemini.ui.forge.model.app.PromptLanguage
import androidx.compose.ui.geometry.Offset
import org.gemini.ui.forge.ui.feature.workspace.BlockSpecificProperties
import org.gemini.ui.forge.ui.feature.workspace.CollapsibleSection
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.ResourceBindingValidator
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.utils.looseJson
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource

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
    var showImg2ImgStudioDialog by remember { mutableStateOf(false) }
    var showImageEditor by remember { mutableStateOf(false) }
    var configData by remember { mutableStateOf<List<ResourceItem>?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.resourceConfigPath, state.resourceConfigRefreshTrigger) {
        val path = state.resourceConfigPath
        if (path.isNullOrBlank()) {
            configData = null
            configError = null
        } else {
            try {
                val bytes = readBytesInternal(path)
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
                        shape = AppShapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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
                            ResourceBindingValidator.findFirstInvalidKey(bindingPath, currentConfig)
                        }
                        val isBindingInvalid = hasBinding && firstInvalidInfo != null

                        LaunchedEffect(isBindingInvalid, firstInvalidInfo, bindingPath) {
                            if (isBindingInvalid) {
                                AppLogger.w(
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

                            if (isBindingInvalid) {
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
                            ResourceBindingDialog(
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


                // 调整大小与提示词按钮：调起全项目公用的 BlockRefinementDialog 弹窗进行原图聚焦微调与文案修改
                var showRefineDialog by remember(selectedBlock.id) { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showRefineDialog = true },
                    modifier = Modifier.fillMaxWidth().tip(stringResource(Res.string.action_adjust_bounds_prompt_tip)),
                    shape = AppShapes.medium
                ) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_adjust_bounds_prompt), style = MaterialTheme.typography.labelLarge)
                }

                if (showRefineDialog) {
                    BlockRefinementDialog(
                        block = selectedBlock,
                        imageUri = state.referenceImageUri,
                        pageWidth = state.currentPage?.width ?: 1080f,
                        pageHeight = state.currentPage?.height ?: 1920f,
                        onDismiss = { showRefineDialog = false },
                        onConfirm = { updatedBlock ->
                            viewModel.assetManager.updateBlockPrompt(updatedBlock.id, PromptLanguage.ZH, updatedBlock.userPromptZh)
                            viewModel.assetManager.updateBlockPrompt(updatedBlock.id, PromptLanguage.EN, updatedBlock.userPromptEn)
                            viewModel.updateBlockBounds(
                                updatedBlock.id,
                                updatedBlock.bounds.left,
                                updatedBlock.bounds.top,
                                updatedBlock.bounds.right,
                                updatedBlock.bounds.bottom
                            )
                            showRefineDialog = false
                        }
                    )
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

            // 独立图片资产展示与操作板块
            val currentBoundFile = selectedBlock.currentImageUri
            CollapsibleSection(
                title = "已绑定图片资产",
                expanded = "已绑定图片资产" !in blockCollapsedSet,
                onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "已绑定图片资产", !it) }
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. 独立图片预览显示区域（点击图片本身直接进入九宫格切图与物理加工界面）
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(AppShapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppShapes.medium)
                            .clickable(enabled = currentBoundFile != null) { showImageEditor = true }
                            .tip(if (currentBoundFile != null) "点击进入九宫格切图与物理加工界面" else "当前未绑定图片资产"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = AppShapes.medium
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentBoundFile != null) {
                                AsyncImage(
                                    model = currentBoundFile.getAbsolutePath(),
                                    contentDescription = "Bound Asset Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                // 右下角精致小编辑画笔提示图标
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    shape = AppShapes.extraSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            text = "点击加工",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "未绑定图片资产",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // 2. 独立操作按钮行：历史/切换 与 解绑
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.showHistoricalDialog(selectedBlock.id) },
                            modifier = Modifier.weight(1f).height(36.dp).tip("从历史生成记录或资产库中选择图片"),
                            shape = AppShapes.small,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("历史/切换", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { viewModel.assetManager.clearSelectedImage(selectedBlock.id) },
                            enabled = currentBoundFile != null,
                            modifier = Modifier.weight(1f).height(36.dp).tip("解除当前模块的图片资产绑定"),
                            shape = AppShapes.small,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("解绑", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 烘焙与物理加工对话框
            if (showImageEditor && currentBoundFile != null) {
                ImageEditorDialog(
                    block = selectedBlock,
                    initialImageUri = currentBoundFile.getAbsolutePath(),
                    onDismiss = { showImageEditor = false },
                    onConfirm = { bytes, mode, config, cropBytes ->
                        viewModel.assetManager.bakeBlockImage(
                            selectedBlock.id,
                            mode,
                            config,
                            selectedBlock.bounds.width.toInt(),
                            selectedBlock.bounds.height.toInt(),
                            selectedBlock.bounds.width.toInt(),
                            selectedBlock.bounds.height.toInt(),
                            bytes,
                            cropBytes
                        )
                        showImageEditor = false
                    }
                )
            }

            val hasSpecificProps = selectedBlock.type.hasSpecificProperties
            if (hasSpecificProps) {
                CollapsibleSection(
                    title = "专属属性配置",
                    expanded = "专属属性配置" !in blockCollapsedSet,
                    onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "专属属性配置", !it) }
                ) {
                    BlockSpecificProperties(
                        viewModel = viewModel,
                        state = state,
                        apiKey = apiKey
                    )
                }
            }

            CollapsibleSection(
                title = "高级与破坏性操作",
                expanded = "高级与破坏性操作" !in blockCollapsedSet,
                onToggle = { viewModel.toggleSectionCollapsed(selectedBlock.id, "高级与破坏性操作", !it) }
            ) {
                // 1. AI 视觉智能生图 / 对话工作室（零门槛直接打开，支持从零创建新图或基于参考图以图生图）
                Button(
                    onClick = { showImg2ImgStudioDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp).tip("打开 AI 视觉智能对话工作室，支持直接创建新图或基于参考图修改抠图"),
                    shape = AppShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !state.isGenerating
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI 视觉智能生图 / 对话工作室", style = MaterialTheme.typography.labelMedium)
                }

                // 2. 参考区域与 AI 结构重塑
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.showReferenceArea(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("从原图中截取局部区域作为该模块的 AI 生成参考图")
                    ) {
                        Icon(Icons.Default.CropRotate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("设置参考区域")
                    }

                    OutlinedButton(
                        onClick = { viewModel.showVisualRefine(selectedBlock.id) },
                        modifier = Modifier.weight(1f).tip("通过 AI 自动分析并重塑该模块的内部层级结构")
                    ) {
                        Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("区域重塑")
                    }
                }

                // 3. 删除模块
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

            // 智能多轮视觉交互工作室（零门槛打开，支持从零生图、原图定位自动截取或基于参考图生图）
            if (showImg2ImgStudioDialog) {
                val coroutineScope = rememberCoroutineScope()
                UniversalVisualChatStudioDialog(
                    scopeId = selectedBlock.id,
                    projectName = state.projectName,
                    block = selectedBlock,
                    initialReferenceImageUri = selectedBlock.referenceImage?.getAbsolutePath(),
                    pageSourceImageUri = state.currentPage?.sourceImageUri?.getAbsolutePath(),
                    pageWidth = state.currentPage?.width ?: 1080f,
                    pageHeight = state.currentPage?.height ?: 1920f,
                    currentLang = state.currentLang,
                    apiKey = apiKey,
                    storage = viewModel.storage,
                    aiService = viewModel.aiService,
                    templateRepo = viewModel.templateRepo,
                    onApplyAsset = { imagePath ->
                        coroutineScope.launch {
                            try {
                                val tFile = if (imagePath.startsWith("data:image")) {
                                    val base64Data = if (imagePath.contains(",")) imagePath.substringAfter(",") else imagePath
                                    val bytes = kotlin.io.encoding.Base64.decode(base64Data)
                                    val isPng = imagePath.contains("image/png")
                                    viewModel.templateRepo.saveBlockResource(
                                        templateName = state.projectName,
                                        blockId = selectedBlock.id,
                                        fileNamePrefix = "chat_gen",
                                        bytes = bytes,
                                        isPng = isPng
                                    )
                                } else {
                                    val fileBytes = org.gemini.ui.forge.utils.readLocalFileBytes(imagePath)
                                    if (fileBytes != null) {
                                        viewModel.templateRepo.saveBlockResource(
                                            templateName = state.projectName,
                                            blockId = selectedBlock.id,
                                            fileNamePrefix = "chat_gen",
                                            bytes = fileBytes,
                                            isPng = imagePath.endsWith(".png", ignoreCase = true)
                                        )
                                    } else {
                                        TemplateFile(imagePath)
                                    }
                                }
                                viewModel.assetManager.onImageSelected(tFile)
                                showImg2ImgStudioDialog = false
                                Toast.show("已成功将生成图片应用到当前模块", ToastType.SUCCESS)
                            } catch (e: Exception) {
                                AppLogger.e("LayoutProperty", "应用资产失败", e)
                                Toast.show("应用资产失败: ${e.message}", ToastType.ERROR)
                            }
                        }
                    },
                    onDismiss = { showImg2ImgStudioDialog = false }
                )
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
    NumberOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier.tip("输入数字以精确调整坐标或尺寸"),
        isFloat = false
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

