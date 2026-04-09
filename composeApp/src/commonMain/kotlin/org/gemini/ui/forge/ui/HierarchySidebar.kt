package org.gemini.ui.forge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.domain.UIBlock
import org.gemini.ui.forge.domain.UIBlockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun HierarchySidebar(
    blocks: List<UIBlock>,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("图层层级", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 用户要求：最底层的显示在第一位。通常列表渲染顺序即 Z 轴顺序。
            // 假设 blocks 列表本身就是按照 Z 轴从小到大排序的。
            items(blocks) { block ->
                HierarchyItem(
                    block = block,
                    depth = 0,
                    selectedBlockId = selectedBlockId,
                    onBlockClicked = onBlockClicked
                )
            }
        }
    }
}

@Composable
private fun HierarchyItem(
    block: UIBlock,
    depth: Int,
    selectedBlockId: String?,
    onBlockClicked: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val isSelected = block.id == selectedBlockId
    val hasChildren = block.children.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable { onBlockClicked(block.id) }
                .padding(start = (8 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(20.dp))
            }

            Spacer(Modifier.width(4.dp))
            
            Column {
                Text(
                    text = stringResource(block.type.getDisplayNameRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = block.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (hasChildren && expanded) {
            block.children.forEach { child ->
                HierarchyItem(
                    block = child,
                    depth = depth + 1,
                    selectedBlockId = selectedBlockId,
                    onBlockClicked = onBlockClicked
                )
            }
        }
    }
}
