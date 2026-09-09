package org.gemini.ui.forge.ui.dialog.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.action_refine_area
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.state.ProjectWorkspaceState
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.component.selector.RegionImageSource
import org.gemini.ui.forge.ui.component.selector.UniversalImageRegionSelector
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 视觉框选重塑对话框（基于 UniversalImageRegionSelector 通用选区组件重塑版）。
 *
 * 提供一个界面让用户在模板图片上框选特定区域，并输入重塑指令。
 * 支持设置是否携带会话历史上下文，并接入 8 方向控制点、Alt 中心对称缩放与三分法构图辅助线。
 *
 * @param viewModel 统一工作区的视图模型控制中心
 * @param state 当前全量工作区运行时状态
 * @param apiKey 视觉 AI 解析接口调用鉴权密钥
 */
@Composable
fun VisualRefineDialog(
    viewModel: ProjectWorkspaceViewModel,
    state: ProjectWorkspaceState,
    apiKey: String
) {
    val blockId = state.refineTargetId
    val imageUri = state.currentPage?.sourceImageUri
    val pageWidth = state.currentPage?.width ?: 1080f
    val pageHeight = state.currentPage?.height ?: 1920f
    val initialInstruction = if (blockId != null) state.defaultRefineInstructionUpdate else state.defaultRefineInstructionNew

    var instruction by remember { mutableStateOf(initialInstruction) }
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    var useChatContext by remember { mutableStateOf(false) }
    val spacing = LocalAppSpacing.current

    Dialog(
        onDismissRequest = { viewModel.hideVisualRefine() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
                // 1. 顶部标题与说明
                val titleSuffix = if (blockId != null) " - 模块: $blockId" else " - 全局区域"
                Text(
                    text = stringResource(Res.string.action_refine_area) + titleSuffix,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(spacing.small))

                // 2. 底图尺寸与选区信息条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.small)
                        .padding(spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Text("底图尺寸: ${pageWidth.toInt()} x ${pageHeight.toInt()}", style = MaterialTheme.typography.labelMedium)

                    val rect = selectedRect
                    if (rect != null) {
                        val selW = abs(rect.width).toInt()
                        val selH = abs(rect.height).toInt()
                        Text(
                            text = "当前重塑选区: $selW x $selH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "当前选区: 未选择（在底图上任意拖拽即可拉出重塑选区）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(spacing.medium))

                // 3. 通用图片选区组件 (UniversalImageRegionSelector)
                UniversalImageRegionSelector(
                    imageSource = imageUri?.let { RegionImageSource.FromFile(it) },
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    initialRect = selectedRect,
                    showThirdsGrid = true,
                    showDimensionBadge = true,
                    selectionBorderColor = Color(0xFF00E5FF),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(AppShapes.medium),
                    onSelectionChange = { selectedRect = it },
                    onSelectionConfirmed = { selectedRect = it }
                )

                Spacer(Modifier.height(spacing.medium))

                // 4. 重塑指令多行输入框
                SelectAllOutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("重塑指令 (Prompt)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = AppShapes.small
                )

                Spacer(Modifier.height(spacing.medium))

                // 5. 底部选项与操作按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useChatContext,
                            onCheckedChange = { useChatContext = it }
                        )
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        TextButton(
                            onClick = { viewModel.hideVisualRefine() },
                            shape = AppShapes.medium,
                            modifier = Modifier.tip("取消重塑操作")
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                selectedRect?.let { rect ->
                                    viewModel.hideVisualRefine()
                                    viewModel.layoutEditor.onRefineArea(
                                        blockId,
                                        rect,
                                        instruction,
                                        apiKey,
                                        useChatContext
                                    ) { }
                                }
                            },
                            enabled = selectedRect != null,
                            shape = AppShapes.medium,
                            modifier = Modifier.tip("确认并开始执行 AI 局部视觉重塑")
                        ) {
                            Text("确认重塑")
                        }
                    }
                }
            }
        }
    }
}
