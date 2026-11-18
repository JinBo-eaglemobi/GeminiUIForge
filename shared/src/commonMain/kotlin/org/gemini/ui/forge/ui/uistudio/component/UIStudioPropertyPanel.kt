package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.extend.copyOnClick
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.DropdownInputField
import org.gemini.ui.forge.ui.component.InspectorDualPropRow
import org.gemini.ui.forge.ui.component.InspectorSectionHeader
import org.gemini.ui.forge.ui.component.InspectorSinglePropRow
import org.gemini.ui.forge.ui.component.InspectorTextFieldRow
import org.gemini.ui.forge.ui.component.getDisplayNameRes
import org.gemini.ui.forge.ui.component.getIcon
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import org.gemini.ui.forge.ui.uistudio.component.property.UIStudioSpecificProperties
import org.gemini.ui.forge.utils.findBlockById

/**
 * UI Studio 右侧属性检查器面板组件。
 *
 * 宽度由外部动态控制，支持用户拖拽分割线自由调整宽度。
 * 提供当前选中图元或选中素材的尺寸坐标动态编辑、组件类型切换、提示词修改、一键透明去背等功能。
 *
 * @param viewModel UI Studio 核心 ViewModel
 * @param templateRepo 模板工程仓储
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIStudioPropertyPanel(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier,
    templateRepo: TemplateRepository = remember { TemplateRepository() }
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val dynamicTitleStyle = if (spacing.isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val dynamicChipStyle = if (spacing.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val state = viewModel.state
    val selectedId = state.selectedBlockId
    val selectedAsset = state.selectedAsset

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(spacing.medium)
        ) {
            // 顶部面板 Header：标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when {
                        selectedAsset != null && selectedId == null -> Icons.Default.Collections
                        selectedId != null -> Icons.Default.Tune
                        else -> Icons.AutoMirrored.Filled.ViewQuilt
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.iconLargeSize)
                )
                Text(
                    text = when {
                        selectedAsset != null && selectedId == null -> "素材详情"
                        selectedId != null -> "组件属性"
                        state.currentView != null -> "视图页面属性"
                        else -> "工作台属性"
                    },
                    style = dynamicTitleStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // 属性内容展示区域
            if (selectedAsset != null && selectedId == null) {
                // 选中素材库素材卡片时的详情面板
                val isMatting = state.isMattingAsset && state.mattingAssetHash == selectedAsset.hash
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = AppShapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Text(
                                text = selectedAsset.name,
                                style = dynamicTitleStyle.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("素材 Hash", style = dynamicBodyStyle, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    selectedAsset.hash,
                                    style = dynamicBodyStyle,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.copyOnClick(selectedAsset.hash, "已复制素材 Hash")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("原始尺寸", style = dynamicBodyStyle, color = MaterialTheme.colorScheme.outline)
                                Text("${selectedAsset.width} × ${selectedAsset.height}", style = dynamicBodyStyle, fontFamily = FontFamily.Monospace)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("分类", style = dynamicBodyStyle, color = MaterialTheme.colorScheme.outline)
                                Text(selectedAsset.category.ifBlank { "默认" }, style = dynamicBodyStyle)
                            }
                        }
                    }

                    // 核心功能：一键去除背景生成透明图片
                    Button(
                        onClick = { viewModel.assetLibrary.removeAssetBackground(selectedAsset.hash) },
                        enabled = !isMatting,
                        shape = AppShapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.inspectorFieldHeight)
                            .tip("调用本地 Python rembg 高清抠图引擎，彻底去除背景并生成透明 PNG 图片")
                    ) {
                        if (isMatting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(spacing.iconCompactSize),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text("正在抠图去背...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(spacing.iconCompactSize))
                            Spacer(Modifier.width(spacing.small))
                            Text("🪄 一键去除背景 (生成透明图)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            } else if (selectedId == null) {
                // 默认展示主视图（页面）信息卡片；当未打开任何页面时显示空状态
                val currentView = state.currentView
                if (currentView != null) {
                    UIStudioMainViewInspector(
                        view = currentView,
                        viewModel = viewModel,
                        templateRepo = templateRepo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewQuilt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(spacing.iconStandardSize)
                            )
                            Text(
                                text = "未打开任何视图界面",
                                style = dynamicBodyStyle,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                // 已选中组件：动态编辑属性（尺寸、坐标、类型、名称）
                val currentView = state.currentView
                val selectedBlock = currentView?.blocks?.findBlockById(selectedId) as? UIStudioBlock

                if (selectedBlock != null) {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = spacing.small)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                        // 1. 基础信息段 (InspectorSectionHeader + 扁平两列无框流式行)
                        var isBasicExpanded by remember { mutableStateOf(true) }
                        InspectorSectionHeader(
                            title = "基础信息",
                            isExpanded = isBasicExpanded,
                            onToggle = { isBasicExpanded = !isBasicExpanded }
                        )

                        if (isBasicExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing),
                                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.inspectorRowSpacing)
                            ) {
                                // 行 1：组件名称 (直接复用 SelectAllOutlinedTextField，回车或失焦直接保存)
                                var nameText by remember(selectedBlock.id) { mutableStateOf(selectedBlock.id) }
                                InspectorTextFieldRow(
                                    label = "组件名称",
                                    value = nameText,
                                    onValueChange = { nameText = it },
                                    onCommit = { trimmed ->
                                        if (trimmed.isNotBlank() && trimmed != selectedBlock.id) {
                                            viewModel.canvasEditor.renameBlock(selectedBlock.id, trimmed, templateRepo)
                                        }
                                    },
                                    tooltip = "修改组件唯一标识符 ID (回车或失焦自动保存)"
                                )

                                // 行 2：组件类型 (复用统一组件 DropdownInputField)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "组件类型",
                                        maxLines = 1,
                                        softWrap = false,
                                        style = dynamicBodyStyle.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.width(spacing.inspectorLabelWidth)
                                    )
                                    val typeItems = UIBlockType.entries.map {
                                        it to stringResource(it.getDisplayNameRes())
                                    }
                                    val currentTypeLabel = typeItems.find { it.first == selectedBlock.type }?.second
                                        ?: stringResource(selectedBlock.type.getDisplayNameRes())
                                    DropdownInputField(
                                        value = currentTypeLabel,
                                        onValueChange = {},
                                        items = typeItems,
                                        onItemSelected = { (newType, _) ->
                                            viewModel.canvasEditor.updateBlockType(selectedBlock.id, newType, templateRepo)
                                        },
                                        itemLabel = { it.second },
                                        isSelected = { it.first == selectedBlock.type },
                                        itemIcon = { it.first.getIcon() },
                                        leadingIcon = selectedBlock.type.getIcon(),
                                        readOnly = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 2. 几何坐标与尺寸 (X, Y, W, H 与 双轴独立缩放，支持鼠标水平滑动与滚轮微调)
                        // 组件基准切片尺寸与双轴缩放状态计算 (范围 0.0 ~ 1.0)（哈希引用模型：按素材哈希唯一联动）
                        val matchingAsset = remember(selectedBlock.id, selectedBlock.assetHash, viewModel.state.assetLibrary.items) {
                            viewModel.state.assetLibrary.items.find {
                                it.name == selectedBlock.id ||
                                (selectedBlock.assetHash != null && it.hash == selectedBlock.assetHash)
                            }
                        }
                        // 独立缩放 Transform 状态 (独立于 bounds 宽高)
                        val currentScaleX = (kotlin.math.round(selectedBlock.effectiveScaleX * 100f) / 100f)
                        val currentScaleY = (kotlin.math.round(selectedBlock.effectiveScaleY * 100f) / 100f)
                        val currentOpacity = (kotlin.math.round(selectedBlock.effectiveOpacity * 100f) / 100f)

                        var scaleXText by remember(selectedBlock.id, selectedBlock.effectiveScaleX) {
                            val i = currentScaleX.toInt()
                            mutableStateOf(if (currentScaleX == i.toFloat()) i.toString() else currentScaleX.toString())
                        }
                        var scaleYText by remember(selectedBlock.id, selectedBlock.effectiveScaleY) {
                            val i = currentScaleY.toInt()
                            mutableStateOf(if (currentScaleY == i.toFloat()) i.toString() else currentScaleY.toString())
                        }
                        var opacityText by remember(selectedBlock.id, selectedBlock.effectiveOpacity) {
                            val i = currentOpacity.toInt()
                            mutableStateOf(if (currentOpacity == i.toFloat()) i.toString() else currentOpacity.toString())
                        }

                        // 2. 几何坐标与尺寸 (扁平流式排版，统一无框设计)
                        var isTransformExpanded by remember { mutableStateOf(true) }
                        InspectorSectionHeader(
                            title = "几何变换",
                            isExpanded = isTransformExpanded,
                            onToggle = { isTransformExpanded = !isTransformExpanded },
                            trailingContent = {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(spacing.inspectorFieldCornerRadius),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(spacing.inspectorFieldCornerRadius))
                                        .clickable {
                                            viewModel.canvasEditor.resetBlockBoundsToOriginalSize(
                                                selectedBlock.id,
                                                templateRepo
                                            )
                                        }
                                        .padding(horizontal = spacing.small, vertical = 2.dp)
                                        .tip("一键将宽 W 和高 H 复位为图片原本的真实物理像素尺寸")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            modifier = Modifier.size(spacing.iconMicroSize),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = "原图",
                                            style = dynamicChipStyle.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(spacing.inspectorFieldCornerRadius),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(spacing.inspectorFieldCornerRadius))
                                        .clickable {
                                            scaleXText = "1"
                                            scaleYText = "1"
                                            opacityText = "1"
                                            viewModel.viewport.resetBlockTransform(
                                                selectedBlock.id,
                                                templateRepo
                                            )
                                        }
                                        .padding(horizontal = spacing.small, vertical = 2.dp)
                                        .tip("一键重置双轴缩放因数与透明度为 1.0 原寸")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(spacing.iconMicroSize),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "还原 1.0",
                                            style = dynamicChipStyle.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )

                        if (isTransformExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing),
                                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.inspectorRowSpacing)
                            ) {

                                // 第 1 行：位置
                                var posXText by remember(selectedBlock.id, selectedBlock.bounds.left) {
                                    mutableStateOf(selectedBlock.bounds.left.toInt().toString())
                                }
                                var posYText by remember(selectedBlock.id, selectedBlock.bounds.top) {
                                    mutableStateOf(selectedBlock.bounds.top.toInt().toString())
                                }
                                InspectorDualPropRow(
                                    categoryLabel = "位置",
                                    tag1 = "X",
                                    value1 = posXText,
                                    onValueChange1 = {
                                        posXText = it
                                        val newLeft = it.toFloatOrNull()
                                        if (newLeft != null) {
                                            viewModel.canvasEditor.updateBlockBounds(
                                                selectedBlock.id,
                                                newLeft,
                                                selectedBlock.bounds.top,
                                                selectedBlock.bounds.width,
                                                selectedBlock.bounds.height,
                                                templateRepo
                                            )
                                        }
                                    },
                                    tag2 = "Y",
                                    value2 = posYText,
                                    onValueChange2 = {
                                        posYText = it
                                        val newTop = it.toFloatOrNull()
                                        if (newTop != null) {
                                            viewModel.canvasEditor.updateBlockBounds(
                                                selectedBlock.id,
                                                selectedBlock.bounds.left,
                                                newTop,
                                                selectedBlock.bounds.width,
                                                selectedBlock.bounds.height,
                                                templateRepo
                                            )
                                        }
                                    },
                                    isFloat = true,
                                    tooltip1 = "水平坐标 X (px)",
                                    tooltip2 = "垂直坐标 Y (px)"
                                )

                                // 第 2 行：尺寸
                                var widthText by remember(selectedBlock.id, selectedBlock.bounds.width) {
                                    mutableStateOf(selectedBlock.bounds.width.toInt().toString())
                                }
                                var heightText by remember(selectedBlock.id, selectedBlock.bounds.height) {
                                    mutableStateOf(selectedBlock.bounds.height.toInt().toString())
                                }
                                InspectorDualPropRow(
                                    categoryLabel = "尺寸",
                                    tag1 = "W",
                                    value1 = widthText,
                                    onValueChange1 = {
                                        widthText = it
                                        val newW = it.toFloatOrNull()
                                        if (newW != null && newW > 0) {
                                            viewModel.canvasEditor.updateBlockBounds(
                                                selectedBlock.id,
                                                selectedBlock.bounds.left,
                                                selectedBlock.bounds.top,
                                                newW,
                                                selectedBlock.bounds.height,
                                                templateRepo
                                            )
                                        }
                                    },
                                    tag2 = "H",
                                    value2 = heightText,
                                    onValueChange2 = {
                                        heightText = it
                                        val newH = it.toFloatOrNull()
                                        if (newH != null && newH > 0) {
                                            viewModel.canvasEditor.updateBlockBounds(
                                                selectedBlock.id,
                                                selectedBlock.bounds.left,
                                                selectedBlock.bounds.top,
                                                selectedBlock.bounds.width,
                                                newH,
                                                templateRepo
                                            )
                                        }
                                    },
                                    isFloat = true,
                                    tooltip1 = "宽度 W (px)",
                                    tooltip2 = "高度 H (px)"
                                )

                                // 第 3 行：缩放
                                InspectorDualPropRow(
                                    categoryLabel = "缩放",
                                    tag1 = "X",
                                    value1 = scaleXText,
                                    onValueChange1 = { input ->
                                        scaleXText = input
                                        val scaleVal = input.toFloatOrNull()
                                        if (scaleVal != null) {
                                            val clamped = scaleVal.coerceIn(0.0f, 1.0f)
                                            viewModel.viewport.updateBlockScale(
                                                selectedBlock.id,
                                                clamped,
                                                selectedBlock.scaleY,
                                                templateRepo
                                            )
                                        }
                                    },
                                    tag2 = "Y",
                                    value2 = scaleYText,
                                    onValueChange2 = { input ->
                                        scaleYText = input
                                        val scaleVal = input.toFloatOrNull()
                                        if (scaleVal != null) {
                                            val clamped = scaleVal.coerceIn(0.0f, 1.0f)
                                            viewModel.viewport.updateBlockScale(
                                                selectedBlock.id,
                                                selectedBlock.scaleX,
                                                clamped,
                                                templateRepo
                                            )
                                        }
                                    },
                                    isFloat = true,
                                    step = 0.05f,
                                    minVal = 0.0f,
                                    maxVal = 1.0f,
                                    tooltip1 = "X 轴独立缩放因数 (0.0~1.0)",
                                    tooltip2 = "Y 轴独立缩放因数 (0.0~1.0)"
                                )

                                // 第 4 行：不透明度
                                InspectorSinglePropRow(
                                    categoryLabel = "不透明度",
                                    value = opacityText,
                                    onValueChange = { input ->
                                        opacityText = input
                                        val opVal = input.toFloatOrNull()
                                        if (opVal != null) {
                                            val clamped = opVal.coerceIn(0.0f, 1.0f)
                                            viewModel.viewport.updateBlockOpacity(
                                                selectedBlock.id,
                                                clamped,
                                                templateRepo
                                            )
                                        }
                                    },
                                    isFloat = true,
                                    step = 0.05f,
                                    minVal = 0.0f,
                                    maxVal = 1.0f,
                                    tooltip = "不透明度 (0.0~1.0)"
                                )
                            }
                        }

                        // 3. 组件专属属性配置 (Component Specific Properties)
                        var isSpecificExpanded by remember { mutableStateOf(true) }
                        InspectorSectionHeader(
                            title = "${stringResource(selectedBlock.type.getDisplayNameRes())} 专属配置",
                            isExpanded = isSpecificExpanded,
                            onToggle = { isSpecificExpanded = !isSpecificExpanded }
                        )

                        if (isSpecificExpanded) {
                            val allPageIds = remember(state.project?.studioPages) {
                                state.project?.studioPages?.map { it.id } ?: emptyList()
                            }
                            UIStudioSpecificProperties(
                                block = selectedBlock,
                                onPropertiesChanged = { updatedProps ->
                                    viewModel.canvasEditor.updateBlockProperties(selectedBlock.id, updatedProps, templateRepo)
                                },
                                availablePages = allPageIds,
                                draggedPageId = state.draggedPageId,
                                dragCursorPosition = state.dragCursorPosition
                            )
                        }

                        // 若该组件引用了平级视图页面，提供一键直达编辑入口
                        val boundPage = remember(selectedBlock.id, selectedBlock.targetPageId, state.project?.studioPages) {
                            state.project?.studioPages?.find { it.id == selectedBlock.targetPageId }
                        }
                        if (boundPage != null) {
                            Spacer(modifier = Modifier.height(spacing.extraSmall))
                            OutlinedButton(
                                onClick = { viewModel.pageManager.openViewTab(boundPage.id) },
                                shape = AppShapes.medium,
                                modifier = Modifier.fillMaxWidth().tip("打开该组件引用的独立视图进行精细排版")
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = "Open Bound Page", modifier = Modifier.size(spacing.iconCompactSize))
                                Spacer(modifier = Modifier.width(spacing.extraSmall))
                                Text("进入引用视图 (${boundPage.id})", style = dynamicBodyStyle)
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.small))

                        // 4. 危险操作：删除当前组件
                        OutlinedButton(
                            onClick = { viewModel.canvasEditor.requestDeleteBlock(selectedBlock.id, templateRepo) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = AppShapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Block", modifier = Modifier.size(spacing.iconCompactSize))
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text("删除此组件", style = dynamicBodyStyle)
                        }
                    }

                    VerticalScrollbarAdapter(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
            }
        }
    }
}
