package org.gemini.ui.forge.ui.feature.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.GeminiModel
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.dialog.ButtonStateGenDialog
import org.gemini.ui.forge.ui.dialog.ImageEditorDialog
import org.gemini.ui.forge.ui.feature.assetgen.BlockSpecificProperties
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.rememberImagePicker
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 统一属性面板：集成布局编辑、物理参数、AI 生成配置及组件特有属性。
 * 采用选项卡（Tabs）结构，支持在不同职责间无缝切换。
 *
 * @param state 当前工作区状态快照。
 * @param viewModel 统一工作区 ViewModel。
 * @param apiKey AI 服务密钥。
 * @param onRefineClick 触发区域重塑的回调。
 * @param onSetReferenceAreaClick 触发参考区域设置的回调。
 * @param onShowHistory 触发历史资源选择的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPropertyPanel(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    onRefineClick: (String?) -> Unit,
    onSetReferenceAreaClick: (String) -> Unit,
    onShowHistory: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部导航选项卡
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Box(Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(Res.string.editor_properties), style = MaterialTheme.typography.labelLarge)
                }
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Box(Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(Res.string.editor_gen_settings), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 内容滚动区
        Box(
            modifier = Modifier.weight(1f).padding(LocalAppSpacing.current.medium).verticalScroll(rememberScrollState())
        ) {
            if (selectedTab == 0) {
                LayoutPropertyContent(state, viewModel, apiKey, onRefineClick, onSetReferenceAreaClick, onDeleteRequest)
            } else {
                AssetGenPropertyContent(state, viewModel, apiKey, onShowHistory)
            }
        }
    }
}

/**
 * 渲染布局编辑相关的属性内容。
 * 包含页面设置、批量生成入口、模块物理坐标、ID、类型切换及删除操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutPropertyContent(
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
 * 渲染资产生成相关的属性内容。
 * 包含风格设定、AI 模型切换、资源预览、固化加工、多态配置及生成选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetGenPropertyContent(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    apiKey: String,
    onShowHistory: (String) -> Unit
) {
    val selectedBlock = state.selectedBlock ?: return
    var showImageEditor by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var useChatContext by remember { mutableStateOf(false) }

    // 按钮多态专用弹窗
    ButtonStateGenDialog(state, viewModel, apiKey)

    // 图像编辑器弹窗（物理加工/Bake）
    if (showImageEditor) {
        ImageEditorDialog(
            block = selectedBlock,
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

    // 全局风格设定弹窗
    if (showAdvancedSettings) {
        AdvancedSettingsDialog(state, viewModel, onDismiss = { showAdvancedSettings = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 选中模块标识
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Fingerprint,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "正在编辑: ${selectedBlock.id}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            SuggestionChip(
                onClick = { },
                label = { Text(stringResource(selectedBlock.type.getDisplayNameRes()), style = MaterialTheme.typography.labelSmall) },
                shape = AppShapes.small,
                border = null,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        // 顶部快捷工具：风格与模型
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showAdvancedSettings = true },
                modifier = Modifier.weight(1f).height(40.dp).tip("设置全项目通用的 AI 风格关键词和参考图"),
                shape = AppShapes.small
            ) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("全局风格", style = MaterialTheme.typography.labelSmall)
            }
            ModelSelector(state, viewModel, Modifier.weight(1.2f).tip("选择当前生图任务使用的 AI 模型"))
        }

        // 模块资源预览图：点击进入物理加工
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(AppShapes.medium).background(Color.Black.copy(alpha = 0.05f))
                .clickable { if (selectedBlock.currentImageUri != null) showImageEditor = true }
                .tip(if (selectedBlock.currentImageUri != null) "点击进入物理加工与固化流程" else "暂无绑定的资源")
        ) {
            if (selectedBlock.currentImageUri != null) {
                AsyncImage(
                    model = selectedBlock.currentImageUri.getAbsolutePath(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HideImage,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "尚未绑定资源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // 资源管理按钮组
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onShowHistory(selectedBlock.id) },
                modifier = Modifier.weight(1f).tip("查看该模块的历史生成记录"),
                shape = AppShapes.medium
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("历史/切换", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { viewModel.assetManager.clearSelectedImage(selectedBlock.id) },
                modifier = Modifier.weight(0.7f).tip("解除当前绑定的资源图"),
                shape = AppShapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("解绑", style = MaterialTheme.typography.labelSmall)
            }
        }

        // 物理加工显式入口
        if (selectedBlock.currentImageUri != null) {
            Button(
                onClick = { showImageEditor = true },
                modifier = Modifier.fillMaxWidth().height(40.dp).tip("进入物理加工、裁剪或九宫格固化流程"),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("物理加工与固化", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(modifier = Modifier.alpha(0.2f))

        // AI 提示词编辑区
        val systemLang = androidx.compose.ui.text.intl.Locale.current.language
        val effectiveLang = state.currentLang.resolve(systemLang)
        val prompt =
            if (effectiveLang == PromptLanguage.ZH) selectedBlock.userPromptZh else selectedBlock.userPromptEn
        val otherPrompt =
            if (effectiveLang == PromptLanguage.ZH) selectedBlock.userPromptEn else selectedBlock.userPromptZh

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "AI 提示词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                // 语言快速切换器
                SingleChoiceSegmentedButtonRow {
                    PromptLanguage.entries.filter { it != PromptLanguage.AUTO }.forEachIndexed { index, lang ->
                        SegmentedButton(
                            selected = effectiveLang == lang,
                            onClick = { viewModel.switchLang(lang) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            SelectAllOutlinedTextField(
                value = prompt,
                onValueChange = { viewModel.assetManager.updateBlockPrompt(selectedBlock.id, effectiveLang, it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    if (otherPrompt.isNotBlank()) {
                        Text("当前语言为空，系统将使用: ${otherPrompt.take(20)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    } else {
                        Text(stringResource(Res.string.prop_prompt_hint), style = MaterialTheme.typography.bodySmall)
                    }
                },
                maxLines = 8,
                enabled = !state.isGenerating
            )

            // 会话上下文与优化入口
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useChatContext,
                    onCheckedChange = { useChatContext = it },
                    modifier = Modifier.tip("开启后将携带历史对话记录以获得更连贯的生成效果")
                )
                Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        viewModel.layoutEditor.optimizePrompt(
                            selectedBlock.id,
                            apiKey,
                            effectiveLang,
                            useChatContext
                        )
                    },
                    enabled = !state.isGenerating && (prompt.isNotBlank() || otherPrompt.isNotBlank()),
                    modifier = Modifier.tip("通过 AI 润色和扩充当前提示词")
                ) {
                    Icon(Icons.Default.AutoFixHigh, "优化提示词", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 资源生成详细选项
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = AppShapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isGenerateTransparent,
                        onCheckedChange = { checked -> viewModel.updateState { it.copy(isGenerateTransparent = checked) } },
                        modifier = Modifier.tip("如果模型支持，则尝试生成带有 alpha 通道的透明图")
                    )
                    Text("生成透明背景 (PNG)", style = MaterialTheme.typography.bodySmall)
                }
                if (state.isGenerateTransparent) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                        Checkbox(
                            checked = state.isPrioritizeCloudRemoval,
                            onCheckedChange = { checked -> viewModel.updateState { it.copy(isPrioritizeCloudRemoval = checked) } },
                            modifier = Modifier.tip("优先使用线上高质量 AI 接口执行抠图，失败后回退至本地模型")
                        )
                        Text("优先云端抠图", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 触发生成
        Button(
            onClick = {
                viewModel.assetGen.onRequestGeneration(
                    apiKey,
                    if (prompt.isNotBlank()) prompt else otherPrompt
                )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).tip("调用 AI 模型开始生成新的图片资产"),
            enabled = !state.isGenerating
        ) {
            if (state.isGenerating) CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            else {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("立即生成资源")
            }
        }
    }
}

/**
 * AI 模型选择组件。
 * 提供支持生图的 Gemini/Imagen 模型列表供用户快速切换。
 */
@Composable
private fun ModelSelector(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = AppShapes.small,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(state.selectedModel.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(220.dp)) {
            GeminiModel.entries.filter { it.modelName.contains("image") || it.modelName.contains("imagen") }
                .forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { viewModel.updateState { it.copy(selectedModel = model) }; expanded = false },
                        leadingIcon = {
                            if (state.selectedModel == model) Icon(
                                Icons.Default.Check,
                                null,
                                Modifier.size(18.dp)
                            )
                        })
                }
        }
    }
}

/**
 * 全局高级设置对话框。
 * 负责管理风格参考图（图生图）以及全项目通用的风格提示词。
 */
@Composable
private fun AdvancedSettingsDialog(
    state: ProjectWorkspaceState,
    viewModel: ProjectWorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val projectName = state.projectName.replace(" ", "_")
    val projectAssetsBase = TemplateFile("templates/$projectName/assets")
    val imagePicker = projectAssetsBase.rememberImagePicker { uris ->
        uris.firstOrNull()?.let { viewModel.assetManager.setReferenceImageExternal(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局风格与参考设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "风格参考图 (图生图引导)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 参考图预览与选择
                    Box(
                        Modifier.size(80.dp).clip(AppShapes.small).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePicker() }.tip("点击选择本地图片作为 AI 生图的风格参考")
                    ) {
                        if (state.referenceImageUri != null) {
                            AsyncImage(
                                model = state.referenceImageUri.getAbsolutePath(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                null,
                                Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column {
                        Button(
                            onClick = { imagePicker() },
                            shape = AppShapes.small,
                            modifier = Modifier.height(32.dp).tip("更换当前的全局参考图"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("更改参考", style = MaterialTheme.typography.labelSmall) }
                        if (state.referenceImageUri != null) {
                            TextButton(
                                onClick = { viewModel.assetManager.setReferenceImage(null) },
                                modifier = Modifier.tip("移除参考图，AI 将不再受其风格引导")
                            ) {
                                Text(
                                    "移除参考",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Text(
                    "全局风格关键词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectAllOutlinedTextField(
                    value = state.globalStyle,
                    onValueChange = { viewModel.assetManager.setGlobalStyle(it) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = {
                        Text(
                            "例如: Cyberpunk, oil painting...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    })
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.assetManager.saveStyleSettings { onDismiss() } },
                modifier = Modifier.tip("应用当前风格并保存项目配置")
            ) { Text("保存设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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

@Composable
fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 8.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
        HorizontalDivider(modifier = Modifier.alpha(0.4f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

