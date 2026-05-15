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
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.component.ImageAreaSelector
import org.gemini.ui.forge.ui.theme.AppShapes
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/**
 * 视觉框选重塑对话框
 *
 * 提供一个界面让用户在模板图片上框选特定区域，并输入重塑指令。
 * 支持设置是否携带会话历史上下文。
 *
 * @param blockId 当前编辑的模块 ID，如果为空则表示全局重塑
 * @param imageUri 待重塑的模板文件信息
 * @param pageWidth 页面原始宽度
 * @param pageHeight 页面原始高度
 * @param initialInstruction 初始重塑指令文案
 * @param onDismiss 对话框关闭回调
 * @param onConfirm 确认重塑回调，返回框选区域、指令、是否携带上下文及状态更新回调
 */
@Composable
fun VisualRefineDialog(
    blockId: String?,
    imageUri: TemplateFile?,
    pageWidth: Float,
    pageHeight: Float,
    initialInstruction: String,
    onDismiss: () -> Unit,
    onConfirm: (SerialRect, String, Boolean, (String) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit
) {
    var instruction by remember { mutableStateOf(initialInstruction) }
    var selectedRect by remember { mutableStateOf<SerialRect?>(null) }
    var useChatContext by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { selectedRect?.let { onConfirm(it, instruction, useChatContext, {}, {}, {}) } },
                            enabled = selectedRect != null,
                            shape = AppShapes.medium
                        ) { Text("确认重塑") }
                    }
                }
            }
        }
    }
}
