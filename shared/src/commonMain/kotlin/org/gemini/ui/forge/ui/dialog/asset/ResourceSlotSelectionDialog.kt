package org.gemini.ui.forge.ui.dialog.asset

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 通用组件多资源槽位（Resource Slots）多选应用弹窗。
 *
 * 当图元具备多个状态属性槽位（如按钮的默认态/点击态/禁用态，旋转按钮的 Spin/Stop 态）时，
 * 在 AI 视觉工作室点击“应用到模块”后唤起本弹窗，支持用户单选或多选将该图片一次性批量赋予所有选中的属性中。
 */
@Composable
fun ResourceSlotSelectionDialog(
    block: UIBlock,
    imagePath: String,
    onDismiss: () -> Unit,
    onConfirm: (selectedIndices: List<Int>) -> Unit
) {
    val spacing = LocalAppSpacing.current
    val states = remember(block) { block.assetStates }

    // 默认勾选第一个槽位
    var selectedIndices by remember { mutableStateOf(setOf(0)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogConfigWidth)
                .wrapContentHeight()
                .padding(spacing.medium),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large)
            ) {
                // 1. 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(spacing.medium))
                    Column {
                        Text(
                            text = "选择要应用的资源槽位",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "当前组件支持多个状态槽位，请勾选目标槽位（支持多选）：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.medium))

                // 2. 槽位列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    itemsIndexed(states) { index, stateItem ->
                        val isChecked = index in selectedIndices
                        val currentUri = block.getCurrentImageUri(index)

                        Surface(
                            shape = AppShapes.medium,
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.medium)
                                .clickable {
                                    selectedIndices = if (isChecked) {
                                        selectedIndices - index
                                    } else {
                                        selectedIndices + index
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIndices = if (checked) {
                                            selectedIndices + index
                                        } else {
                                            selectedIndices - index
                                        }
                                    }
                                )

                                Spacer(Modifier.width(spacing.small))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stateItem.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = if (currentUri != null) "已绑定旧资源" else "当前为空",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 缩略图预览（右侧显示当前旧图）
                                Surface(
                                    shape = AppShapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    if (currentUri != null) {
                                        AsyncImage(
                                            model = currentUri.getAbsolutePath(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp)
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "无",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.large))

                // 3. 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(spacing.small))
                    Button(
                        onClick = { onConfirm(selectedIndices.toList().sorted()) },
                        enabled = selectedIndices.isNotEmpty(),
                        modifier = Modifier.tip("将当前生成的图片一次性批量应用到所有已勾选的槽位")
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(spacing.extraSmall))
                        Text("应用到选中槽位 (${selectedIndices.size})")
                    }
                }
            }
        }
    }
}
