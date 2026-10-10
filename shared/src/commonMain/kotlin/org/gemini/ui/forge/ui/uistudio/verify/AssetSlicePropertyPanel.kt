package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.math.roundToInt

/**
 * 切片预审校对弹窗右侧固定属性面板 (Inspector)
 *
 * 遵循一文件一 Composable 铁律。固定展示在最右侧，
 * 未选中时显示占位引导，选中时支持实时编辑图元名称、分类及几何坐标。
 *
 * @param selectedItem 当前选中的图元实体（为空时展示占位引导）
 * @param onDeselect 取消选中回调
 * @param onUpdateItem 更新图元属性回调
 * @param onDeleteItem 删除当前图元回调
 * @param modifier 外部修饰符
 */
@Composable
fun AssetSlicePropertyPanel(
    selectedItem: EditableSliceItem?,
    onDeselect: () -> Unit,
    onUpdateItem: (EditableSliceItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.medium)
    ) {
        // 顶部 Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text = "图元属性 (Inspector)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (selectedItem != null) {
                IconButton(
                    onClick = onDeselect,
                    modifier = Modifier.size(28.dp).tip("取消选中")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        if (selectedItem == null) {
            // 未选中图元时的空状态占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = "未选中任何图元",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        text = "请在画布或左侧元件列表中\n点击选择图元以编辑其属性",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 选中图元的属性表单
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 图元 ID 徽章
                AssistChip(
                    onClick = {},
                    label = { Text("ID: ${selectedItem.id}", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(spacing.small))

                // 名称编辑
                OutlinedTextField(
                    value = selectedItem.name,
                    onValueChange = { onUpdateItem(selectedItem.copy(name = it)) },
                    label = { Text("图元名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(spacing.small))

                // 分类编辑
                OutlinedTextField(
                    value = selectedItem.category,
                    onValueChange = { onUpdateItem(selectedItem.copy(category = it)) },
                    label = { Text("业务分类") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                // 几何坐标调整 (X, Y, W, H)
                Text(
                    text = "几何坐标与尺寸 (px)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(spacing.small))

                val bounds = selectedItem.bounds

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    var xText by remember(bounds.left) { mutableStateOf(bounds.left.roundToInt().toString()) }
                    OutlinedTextField(
                        value = xText,
                        onValueChange = { input ->
                            xText = input
                            input.toIntOrNull()?.let { newVal ->
                                val w = bounds.width
                                onUpdateItem(selectedItem.copy(bounds = SerialRect(newVal.toFloat(), bounds.top, newVal + w, bounds.bottom)))
                            }
                        },
                        label = { Text("X", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    var yText by remember(bounds.top) { mutableStateOf(bounds.top.roundToInt().toString()) }
                    OutlinedTextField(
                        value = yText,
                        onValueChange = { input ->
                            yText = input
                            input.toIntOrNull()?.let { newVal ->
                                val h = bounds.height
                                onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, newVal.toFloat(), bounds.right, newVal + h)))
                            }
                        },
                        label = { Text("Y", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    var wText by remember(bounds.width) { mutableStateOf(bounds.width.roundToInt().toString()) }
                    OutlinedTextField(
                        value = wText,
                        onValueChange = { input ->
                            wText = input
                            input.toIntOrNull()?.let { newVal ->
                                val safeW = newVal.coerceAtLeast(8)
                                onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, bounds.top, bounds.left + safeW, bounds.bottom)))
                            }
                        },
                        label = { Text("宽", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    var hText by remember(bounds.height) { mutableStateOf(bounds.height.roundToInt().toString()) }
                    OutlinedTextField(
                        value = hText,
                        onValueChange = { input ->
                            hText = input
                            input.toIntOrNull()?.let { newVal ->
                                val safeH = newVal.coerceAtLeast(8)
                                onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, bounds.top, bounds.right, bounds.top + safeH)))
                            }
                        },
                        label = { Text("高", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // 删除当前图元按钮
            OutlinedButton(
                onClick = { onDeleteItem(selectedItem.id) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().tip("删除此图元")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text("删除图元")
            }
        }
    }
}
