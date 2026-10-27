package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.model.ui.UIStudioBlock
import org.gemini.ui.forge.ui.component.NumberOutlinedTextField
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
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
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = when {
                        selectedAsset != null && selectedId == null -> "素材详情"
                        selectedId != null -> "组件属性"
                        state.currentView != null -> "视图页面属性"
                        else -> "工作台属性"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // 属性内容展示区域
            if (selectedAsset != null && selectedId == null) {
                // 选中素材库素材卡片时的详情面板
                val isMatting = state.isMattingAsset && state.mattingAssetId == selectedAsset.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Text(
                                text = selectedAsset.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("素材 ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text(selectedAsset.id, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("原始尺寸", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text("${selectedAsset.width} × ${selectedAsset.height}", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text(selectedAsset.category.ifBlank { "默认" }, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // 核心功能：一键去除背景生成透明图片
                    Button(
                        onClick = { viewModel.removeAssetBackground(selectedAsset.id) },
                        enabled = !isMatting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .tip("调用本地 Python rembg 高清抠图引擎，彻底去除背景并生成透明 PNG 图片")
                    ) {
                        if (isMatting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在抠图去背...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("🪄 一键去除背景 (生成透明图)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "未打开任何视图界面",
                                style = MaterialTheme.typography.bodySmall,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        // 1. 基础信息卡片 (ID & 名称重命名)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.small),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Text(
                                    text = "组件 ID: ${selectedBlock.id}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontFamily = FontFamily.Monospace
                                )

                                // 直接重命名组件名称 (ID)
                                var nameText by remember(selectedBlock.id) {
                                    mutableStateOf(selectedBlock.id)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectAllOutlinedTextField(
                                        value = nameText,
                                        onValueChange = { nameText = it },
                                        label = { Text("重命名组件", fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val trimmed = nameText.trim()
                                            if (trimmed.isNotBlank() && trimmed != selectedBlock.id) {
                                                viewModel.renameBlock(selectedBlock.id, trimmed, templateRepo)
                                            }
                                        },
                                        enabled = nameText.isNotBlank() && nameText != selectedBlock.id,
                                        modifier = Modifier.size(36.dp).tip("保存新名称")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "确认修改",
                                            tint = if (nameText.isNotBlank() && nameText != selectedBlock.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 几何坐标与尺寸 (X, Y, W, H 与 双轴独立缩放，支持鼠标水平滑动与滚轮微调)
                        // 组件基准切片尺寸与双轴缩放状态计算 (范围 0.0 ~ 1.0)（哈希引用模型：按素材哈希唯一联动）
                        val matchingAsset = remember(selectedBlock.id, selectedBlock.assetHash, viewModel.state.assetLibrary.items) {
                            viewModel.state.assetLibrary.items.find {
                                it.id == selectedBlock.id ||
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

                        // 几何变换独立卡片 (规整卡片承载与标准行列间距)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.small),
                                verticalArrangement = Arrangement.spacedBy(spacing.formRowSpacing)
                            ) {
                                // 卡片 Header：标题与右侧「还原 1.0」胶囊按钮
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "几何变换",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        softWrap = false
                                    )

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                scaleXText = "1"
                                                scaleYText = "1"
                                                opacityText = "1"
                                                viewModel.resetBlockTransform(
                                                    selectedBlock.id,
                                                    templateRepo
                                                )
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                            .tip("一键重置双轴缩放因数与透明度为 1.0 原寸 (不影响组件宽高 bounds)")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "还原 1.0",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // 第 1 行：X (px) 与 Y (px) (严格 2 列等宽，舒适列间距)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.formColumnSpacing),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    var posXText by remember(selectedBlock.id, selectedBlock.bounds.left) {
                                        mutableStateOf(selectedBlock.bounds.left.toInt().toString())
                                    }
                                    NumberOutlinedTextField(
                                        value = posXText,
                                        onValueChange = {
                                            posXText = it
                                            val newLeft = it.toFloatOrNull()
                                            if (newLeft != null) {
                                                viewModel.updateBlockBounds(
                                                    selectedBlock.id,
                                                    newLeft,
                                                    selectedBlock.bounds.top,
                                                    selectedBlock.bounds.width,
                                                    selectedBlock.bounds.height,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("X (px)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        modifier = Modifier.weight(1f).tip("水平坐标 X，按住左右滑动或滚动滚轮改值")
                                    )

                                    var posYText by remember(selectedBlock.id, selectedBlock.bounds.top) {
                                        mutableStateOf(selectedBlock.bounds.top.toInt().toString())
                                    }
                                    NumberOutlinedTextField(
                                        value = posYText,
                                        onValueChange = {
                                            posYText = it
                                            val newTop = it.toFloatOrNull()
                                            if (newTop != null) {
                                                viewModel.updateBlockBounds(
                                                    selectedBlock.id,
                                                    selectedBlock.bounds.left,
                                                    newTop,
                                                    selectedBlock.bounds.width,
                                                    selectedBlock.bounds.height,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("Y (px)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        modifier = Modifier.weight(1f).tip("垂直坐标 Y，按住左右滑动或滚动滚轮改值")
                                    )
                                }

                                // 第 2 行：宽 W (px) 与 高 H (px) (严格 2 列等宽，舒适列间距)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.formColumnSpacing),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    var widthText by remember(selectedBlock.id, selectedBlock.bounds.width) {
                                        mutableStateOf(selectedBlock.bounds.width.toInt().toString())
                                    }
                                    NumberOutlinedTextField(
                                        value = widthText,
                                        onValueChange = {
                                            widthText = it
                                            val newW = it.toFloatOrNull()
                                            if (newW != null && newW > 0) {
                                                viewModel.updateBlockBounds(
                                                    selectedBlock.id,
                                                    selectedBlock.bounds.left,
                                                    selectedBlock.bounds.top,
                                                    newW,
                                                    selectedBlock.bounds.height,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("宽 W (px)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        modifier = Modifier.weight(1f).tip("宽度 W，按住左右滑动或滚动滚轮改值")
                                    )

                                    var heightText by remember(selectedBlock.id, selectedBlock.bounds.height) {
                                        mutableStateOf(selectedBlock.bounds.height.toInt().toString())
                                    }
                                    NumberOutlinedTextField(
                                        value = heightText,
                                        onValueChange = {
                                            heightText = it
                                            val newH = it.toFloatOrNull()
                                            if (newH != null && newH > 0) {
                                                viewModel.updateBlockBounds(
                                                    selectedBlock.id,
                                                    selectedBlock.bounds.left,
                                                    selectedBlock.bounds.top,
                                                    selectedBlock.bounds.width,
                                                    newH,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("高 H (px)", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        modifier = Modifier.weight(1f).tip("高度 H，按住左右滑动或滚动滚轮改值")
                                    )
                                }

                                // 第 3 行：缩放 X 与 缩放 Y (0.0~1.0 浮点范围，严格 2 列等宽，舒适列间距)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.formColumnSpacing),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    NumberOutlinedTextField(
                                        value = scaleXText,
                                        onValueChange = { input ->
                                            scaleXText = input
                                            val scaleVal = input.toFloatOrNull()
                                            if (scaleVal != null) {
                                                val clamped = scaleVal.coerceIn(0.0f, 1.0f)
                                                viewModel.updateBlockScale(
                                                    selectedBlock.id,
                                                    clamped,
                                                    selectedBlock.scaleY,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("缩放 X", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        step = 0.05f,
                                        minVal = 0.0f,
                                        maxVal = 1.0f,
                                        modifier = Modifier.weight(1f).tip("X 轴独立缩放因数 (0.0~1.0)，按住左右滑动或滚动滚轮改值")
                                    )

                                    NumberOutlinedTextField(
                                        value = scaleYText,
                                        onValueChange = { input ->
                                            scaleYText = input
                                            val scaleVal = input.toFloatOrNull()
                                            if (scaleVal != null) {
                                                val clamped = scaleVal.coerceIn(0.0f, 1.0f)
                                                viewModel.updateBlockScale(
                                                    selectedBlock.id,
                                                    selectedBlock.scaleX,
                                                    clamped,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("缩放 Y", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        step = 0.05f,
                                        minVal = 0.0f,
                                        maxVal = 1.0f,
                                        modifier = Modifier.weight(1f).tip("Y 轴独立缩放因数 (0.0~1.0)，按住左右滑动或滚动滚轮改值")
                                    )
                                }

                                // 第 4 行：不透明度 (0.0~1.0 浮点范围，支持滑动调值，与上方网格完美通栏对齐)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    NumberOutlinedTextField(
                                        value = opacityText,
                                        onValueChange = { input ->
                                            opacityText = input
                                            val opVal = input.toFloatOrNull()
                                            if (opVal != null) {
                                                val clamped = opVal.coerceIn(0.0f, 1.0f)
                                                viewModel.updateBlockOpacity(
                                                    selectedBlock.id,
                                                    clamped,
                                                    templateRepo
                                                )
                                            }
                                        },
                                        label = { Text("透明度", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                        isFloat = true,
                                        step = 0.05f,
                                        minVal = 0.0f,
                                        maxVal = 1.0f,
                                        modifier = Modifier.weight(1f).tip("不透明度 (0.0~1.0)，0 为完全透明，1 为完全不透明，按住左右滑动或滚动滚轮改值")
                                    )
                                }
                            }
                        }

                        // 3. 组件类型动态切换 (UIBlockType)
                        Text("组件类型", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectAllOutlinedTextField(
                                value = selectedBlock.type.name,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                UIBlockType.entries.forEach { blockType ->
                                    DropdownMenuItem(
                                        text = { Text(blockType.name) },
                                        onClick = {
                                            viewModel.updateBlockType(selectedBlock.id, blockType, templateRepo)
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.small))

                        // 4. 危险操作：删除当前组件
                        OutlinedButton(
                            onClick = { viewModel.requestDeleteBlock(selectedBlock.id, templateRepo) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Block", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text("删除此组件")
                        }
                    }
                }
            }
        }
    }
}
