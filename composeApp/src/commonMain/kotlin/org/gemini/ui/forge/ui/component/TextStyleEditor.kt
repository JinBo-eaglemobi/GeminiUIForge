package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 将 Hex 字符串转换为 Compose Color
 */
fun parseHexColor(hex: String, defaultColor: Color = Color.Black): Color {
    return try {
        if (hex.startsWith("#")) {
            val colorString = hex.substring(1)
            when (colorString.length) {
                6 -> Color(colorString.toLong(16) or 0x00000000FF000000)
                8 -> Color(colorString.toLong(16))
                else -> defaultColor
            }
        } else {
            defaultColor
        }
    } catch (e: Exception) {
        defaultColor
    }
}

/**
 * 通用的带预览和弹窗选择的十六进制颜色输入框
 */
@Composable
fun ColorPickerField(
    label: String,
    hexColor: String,
    onColorChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentColor = parseHexColor(hexColor, Color.Transparent)
    
    // 预设的常用颜色板
    val presetColors = listOf(
        "#FFFFFF", "#000000", "#FF0000", "#00FF00", "#0000FF", 
        "#FFFF00", "#00FFFF", "#FF00FF", "#C0C0C0", "#808080",
        "#800000", "#808000", "#008000", "#800080", "#008080", "#000080",
        "#FFA500", "#A52A2A", "#FFC0CB", "#FFD700"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectAllOutlinedTextField(
            value = hexColor,
            onValueChange = onColorChanged,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(top = 6.dp) // 与 OutlinedTextField 对齐
                .clip(AppShapes.small)
                .background(currentColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, AppShapes.small)
                .clickable { showDialog = true }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择颜色 ($label)") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetColors) { presetHex ->
                        val color = parseHexColor(presetHex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    2.dp, 
                                    if (presetHex.equals(hexColor, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.Transparent, 
                                    CircleShape
                                )
                                .clickable {
                                    onColorChanged(presetHex)
                                    showDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}

/**
 * 文本排版工具栏（加粗、倾斜、对齐方式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStyleToolbar(
    isBold: Boolean,
    onBoldChanged: (Boolean) -> Unit,
    isItalic: Boolean,
    onItalicChanged: (Boolean) -> Unit,
    horizontalAlign: String,
    onHorizontalAlignChanged: (String) -> Unit,
    verticalAlign: String,
    onVerticalAlignChanged: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("排版与对齐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font Style
            Row {
                IconToggleButton(checked = isBold, onCheckedChange = onBoldChanged) {
                    Icon(Icons.Default.FormatBold, contentDescription = "加粗", tint = if(isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconToggleButton(checked = isItalic, onCheckedChange = onItalicChanged) {
                    Icon(Icons.Default.FormatItalic, contentDescription = "倾斜", tint = if(isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }

            // Horizontal Align
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = horizontalAlign == "LEFT",
                    onClick = { onHorizontalAlignChanged("LEFT") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, "左对齐") }
                SegmentedButton(
                    selected = horizontalAlign == "CENTER",
                    onClick = { onHorizontalAlignChanged("CENTER") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Icon(Icons.Default.FormatAlignCenter, "水平居中") }
                SegmentedButton(
                    selected = horizontalAlign == "RIGHT",
                    onClick = { onHorizontalAlignChanged("RIGHT") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, "右对齐") }
            }

            // Vertical Align
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = verticalAlign == "TOP",
                    onClick = { onVerticalAlignChanged("TOP") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Icon(Icons.Default.VerticalAlignTop, "置顶") }
                SegmentedButton(
                    selected = verticalAlign == "CENTER",
                    onClick = { onVerticalAlignChanged("CENTER") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Icon(Icons.Default.VerticalAlignCenter, "垂直居中") }
                SegmentedButton(
                    selected = verticalAlign == "BOTTOM",
                    onClick = { onVerticalAlignChanged("BOTTOM") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Icon(Icons.Default.VerticalAlignBottom, "置底") }
            }
        }
    }
}
