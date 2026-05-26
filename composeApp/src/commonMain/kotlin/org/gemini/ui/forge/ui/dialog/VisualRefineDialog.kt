package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.gemini.ui.forge.ui.component.ImageAreaSelector
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.ProjectWorkspaceViewModel
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉框选重塑对话框
 *
 * 提供一个界面让用户在模板图片上框选特定区域，并输入重塑指令。
 * 支持设置是否携带会话历史上下文。
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

    Dialog(onDismissRequest = { viewModel.hideVisualRefine() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val titleSuffix = if (blockId != null) " - 模块: $blockId" else " - 全局"
                Text(stringResource(Res.string.action_refine_area) + titleSuffix, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("底图尺寸: ${pageWidth.toInt()} x ${pageHeight.toInt()}", style = MaterialTheme.typography.labelMedium)
                    
                    val rect = selectedRect
                    if (rect != null) {
                        val selW = abs(rect.width).toInt()
                        val selH = abs(rect.height).toInt()
                        Text("当前选区大小: $selW x $selH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("当前选区大小: 未选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(16.dp))

                ImageAreaSelector(
                    imageUri = imageUri,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.05f))
                        .clip(RoundedCornerShape(8.dp)),
                    selectionColor = Color.Cyan,
                    onSelectionChange = { selectedRect = it }
                )

                Spacer(Modifier.height(16.dp))
                SelectAllOutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("重塑指令") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = AppShapes.medium
                )
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useChatContext,
                            onCheckedChange = { useChatContext = it }
                        )
                        Text("携带历史上下文 (会话模式)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        TextButton(onClick = { viewModel.hideVisualRefine() }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
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
                            shape = AppShapes.medium
                        ) { Text("确认重塑") }
                    }
                }
            }
        }
    }
}
