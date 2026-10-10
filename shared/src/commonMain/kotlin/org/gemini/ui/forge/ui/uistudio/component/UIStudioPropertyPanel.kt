package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel

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
                    imageVector = if (selectedAsset != null && selectedId == null) Icons.Default.Collections else Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (selectedAsset != null && selectedId == null) "素材详情 (Asset Inspector)" else "图元属性 (Inspector)",
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
                // 未选中图元或素材时的空状态占位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(spacing.small)
                        )
                        .padding(spacing.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "未选中任何图元，点击画布中的图元以动态编辑坐标、尺寸与类型",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // 已选中图元：动态编辑属性（参考模块编辑模式）
                val currentView = state.project?.pages?.find { it.id == state.currentViewId }
                    ?: state.project?.pages?.firstOrNull()
                val selectedBlock = currentView?.blocks?.find { it.id == selectedId }

                if (selectedBlock != null) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        // 1. 基础信息卡片 (ID & 纯容器开关)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(spacing.small)) {
                                Text(
                                    text = "图元 ID: ${selectedBlock.id}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(spacing.extraSmall))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("纯容器 (不参与生图)", style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = selectedBlock.isPureContainer,
                                        onCheckedChange = { isPure ->
                                            viewModel.updateBlockPureContainer(selectedBlock.id, isPure, templateRepo)
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            }
                        }

                        // 2. 几何坐标与尺寸 (X, Y, W, H 动态修改)
                        Text("几何变换 (Geometry)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var posXText by remember(selectedBlock.id, selectedBlock.bounds.left) {
                                mutableStateOf(selectedBlock.bounds.left.toInt().toString())
                            }
                            OutlinedTextField(
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
                                label = { Text("X", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            var posYText by remember(selectedBlock.id, selectedBlock.bounds.top) {
                                mutableStateOf(selectedBlock.bounds.top.toInt().toString())
                            }
                            OutlinedTextField(
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
                                label = { Text("Y", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var widthText by remember(selectedBlock.id, selectedBlock.bounds.width) {
                                mutableStateOf(selectedBlock.bounds.width.toInt().toString())
                            }
                            OutlinedTextField(
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
                                label = { Text("W (宽)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            var heightText by remember(selectedBlock.id, selectedBlock.bounds.height) {
                                mutableStateOf(selectedBlock.bounds.height.toInt().toString())
                            }
                            OutlinedTextField(
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
                                label = { Text("H (高)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 3. 组件类型动态切换 (UIBlockType)
                        Text("图元类型 (Block Type)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedBlock.type.name,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
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

                        // 4. 提示词与名称
                        Text("生图提示词与描述", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
                        var promptZhText by remember(selectedBlock.id, selectedBlock.userPromptZh) {
                            mutableStateOf(selectedBlock.userPromptZh)
                        }
                        OutlinedTextField(
                            value = promptZhText,
                            onValueChange = {
                                promptZhText = it
                                viewModel.updateBlockPrompts(selectedBlock.id, it, selectedBlock.userPromptEn, templateRepo)
                            },
                            label = { Text("中文描述/提示词") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        var promptEnText by remember(selectedBlock.id, selectedBlock.userPromptEn) {
                            mutableStateOf(selectedBlock.userPromptEn)
                        }
                        OutlinedTextField(
                            value = promptEnText,
                            onValueChange = {
                                promptEnText = it
                                viewModel.updateBlockPrompts(selectedBlock.id, selectedBlock.userPromptZh, it, templateRepo)
                            },
                            label = { Text("英文描述 (Prompt EN)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(spacing.small))

                        // 5. 危险操作：删除当前图元
                        OutlinedButton(
                            onClick = { viewModel.deleteSelectedBlock(selectedBlock.id, templateRepo) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Block", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text("删除此图元 (Delete)")
                        }
                    }
                }
            }
        }
    }
}
