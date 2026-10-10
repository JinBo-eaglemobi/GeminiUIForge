package org.gemini.ui.forge.ui.uistudio.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.math.roundToInt

/**
 * 校对弹窗右侧图元导航与属性编辑侧栏
 *
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun AssetSliceSidebar(
    items: List<EditableSliceItem>,
    selectedItemId: String?,
    onSelectItem: (String?) -> Unit,
    onUpdateItem: (EditableSliceItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val selectedItem = items.find { it.id == selectedItemId }

    Column(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(spacing.medium)
    ) {
        if (selectedItem != null) {
            // A 模式：选中图元的属性编辑器
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
                        text = "图元属性",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { onSelectItem(null) },
                    modifier = Modifier.size(28.dp).tip("返回列表")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

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
                CoordinateBox(label = "X", value = bounds.left.roundToInt()) { newVal ->
                    val w = bounds.width
                    onUpdateItem(selectedItem.copy(bounds = SerialRect(newVal.toFloat(), bounds.top, newVal + w, bounds.bottom)))
                }
                CoordinateBox(label = "Y", value = bounds.top.roundToInt()) { newVal ->
                    val h = bounds.height
                    onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, newVal.toFloat(), bounds.right, newVal + h)))
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                CoordinateBox(label = "宽", value = bounds.width.roundToInt()) { newVal ->
                    val safeW = newVal.coerceAtLeast(8)
                    onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, bounds.top, bounds.left + safeW, bounds.bottom)))
                }
                CoordinateBox(label = "高", value = bounds.height.roundToInt()) { newVal ->
                    val safeH = newVal.coerceAtLeast(8)
                    onUpdateItem(selectedItem.copy(bounds = SerialRect(bounds.left, bounds.top, bounds.right, bounds.top + safeH)))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
        } else {
            // B 模式：图元总览列表
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "图元清单 (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "点击聚焦",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(spacing.small))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = item.id == selectedItemId
                    val b = item.bounds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                            .clickable { onSelectItem(item.id) }
                            .padding(horizontal = spacing.small, vertical = spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.category.isNotBlank()) {
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${b.width.roundToInt()}×${b.height.roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteItem(item.id) },
                            modifier = Modifier.size(24.dp).tip("删除")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinateBox(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.width(110.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
}
