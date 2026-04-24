package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAssetGenDialog(
    blocks: List<UIBlock>,
    onCancel: () -> Unit,
    onStartGen: (List<UIBlock>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(blocks.map { it.id }.toSet()) }
    val groupedBlocks = remember(blocks) { blocks.groupBy { it.type } }
    
    // 折叠状态：记录哪些类型是展开的
    var expandedTypes by remember { mutableStateOf(groupedBlocks.keys.toSet()) }
    
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.widthIn(max = 650.dp).fillMaxHeight(0.85f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(Res.string.batch_gen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = stringResource(Res.string.batch_gen_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 快捷操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { selectedIds = blocks.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.batch_gen_select_all))
                        }
                        TextButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Deselect, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.batch_gen_deselect_all))
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { expandedTypes = groupedBlocks.keys.toSet() }) {
                            Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部展开")
                        }
                        TextButton(onClick = { expandedTypes = emptySet() }) {
                            Icon(Icons.Default.UnfoldLess, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部折叠")
                        }
                    }
                }

                if (blocks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.batch_gen_empty))
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            groupedBlocks.forEach { (type, typeBlocks) ->
                                val isExpanded = type in expandedTypes
                                
                                item(key = type.name) {
                                    val allOfTypeSelected = typeBlocks.all { it.id in selectedIds }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.small)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                            .clickable {
                                                expandedTypes = if (isExpanded) expandedTypes - type else expandedTypes + type
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 展开/折叠图标
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Spacer(Modifier.width(4.dp))
                                        
                                        // 复选框
                                        IconButton(
                                            onClick = {
                                                val ids = typeBlocks.map { it.id }
                                                selectedIds = if (allOfTypeSelected) selectedIds - ids.toSet() else selectedIds + ids
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (allOfTypeSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                tint = if (allOfTypeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Text(
                                            text = type.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${typeBlocks.count { it.id in selectedIds }}/${typeBlocks.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    items(typeBlocks, key = { it.id }) { block ->
                                        val isSelected = block.id in selectedIds
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 36.dp)
                                                .clip(AppShapes.small)
                                                .clickable {
                                                    selectedIds = if (isSelected) selectedIds - block.id else selectedIds + block.id
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(block.id, style = MaterialTheme.typography.bodyMedium)
                                                    Spacer(Modifier.width(8.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                        shape = AppShapes.small
                                                    ) {
                                                        Text(
                                                            text = block.type.name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (block.userPrompt.isNotBlank()) {
                                                    Text(
                                                        block.userPrompt,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 滚动条
                        VerticalScrollbarAdapter(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            scrollState = listState
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(Res.string.dialog_action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onStartGen(blocks.filter { it.id in selectedIds }) },
                        enabled = selectedIds.isNotEmpty(),
                        shape = AppShapes.medium
                    ) {
                        Text(stringResource(Res.string.batch_gen_start))
                    }
                }
            }
        }
    }
}
