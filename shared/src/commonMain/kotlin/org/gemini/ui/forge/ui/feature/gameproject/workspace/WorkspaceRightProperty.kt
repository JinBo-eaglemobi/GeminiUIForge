package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_debug_logs_section
import geminiuiforge.composeapp.generated.resources.gp_debug_no_selection
import geminiuiforge.composeapp.generated.resources.gp_debug_no_tree
import geminiuiforge.composeapp.generated.resources.gp_debug_props_section
import geminiuiforge.composeapp.generated.resources.gp_debug_toggle_hint
import geminiuiforge.composeapp.generated.resources.gp_debug_tree_section
import geminiuiforge.composeapp.generated.resources.gp_ws_right
import org.gemini.ui.forge.model.gameproject.DebugPropertyRow
import org.gemini.ui.forge.model.gameproject.DebugTreeNode
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 右侧属性 / 调试面板。
 */
@Composable
fun PropertiesPanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    // 已展开的调试树节点 ID 集合
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    // 下半区页签：true = 属性，false = 日志
    var showProps by remember { mutableStateOf(true) }
    val visibleDebugNodes = remember(viewModel.debugTree, expandedIds) {
        flattenDebugTree(viewModel.debugTree, expandedIds)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelHeader(
                title = stringResource(Res.string.gp_ws_right),
                onCollapse = onCollapse,
                collapseIcon = Icons.Default.ChevronRight
            )
            if (!viewModel.debugMode) {
                EmptyPanel(text = stringResource(Res.string.gp_debug_toggle_hint))
            } else {
                // 节点树区（层级展示）
                SectionLabel(stringResource(Res.string.gp_debug_tree_section))
                if (viewModel.debugTree == null) {
                    SectionHint(stringResource(Res.string.gp_debug_no_tree))
                } else {
                    LazyColumn(modifier = Modifier.weight(0.42f)) {
                        items(
                            visibleDebugNodes,
                            key = { it.depth.toString() + "|" + it.node.id + "|" + it.node.text }
                        ) { flat ->
                            DebugTreeRow(
                                node = flat.node,
                                depth = flat.depth,
                                selected = flat.node.id == viewModel.debugSelectedId,
                                onToggle = {
                                    expandedIds = if (flat.node.id in expandedIds) {
                                        expandedIds - flat.node.id
                                    } else {
                                        expandedIds + flat.node.id
                                    }
                                },
                                onSelect = { viewModel.selectDebugNode(flat.node.id.ifBlank { null }) }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                // 属性 / 日志页签
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = showProps,
                        onClick = { showProps = true },
                        label = { Text(stringResource(Res.string.gp_debug_props_section), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = !showProps,
                        onClick = { showProps = false },
                        label = { Text(stringResource(Res.string.gp_debug_logs_section), style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (showProps) {
                    if (viewModel.debugProps.isEmpty()) {
                        SectionHint(stringResource(Res.string.gp_debug_no_selection))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(viewModel.debugProps, key = { it.key }) { row ->
                                PropRow(row = row)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(viewModel.debugLogs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分区小标题。
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * 分区空态提示。
 */
@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)
    )
}

/**
 * 调试树节点行。
 */
@Composable
private fun DebugTreeRow(
    node: DebugTreeNode,
    depth: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable { onSelect(); if (node.item.isNotEmpty()) onToggle() }
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .padding(start = (6 + depth * 12).dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = node.text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 属性行。
 */
@Composable
private fun PropRow(row: DebugPropertyRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 调试树扁平化结果。
 */
private data class FlatDebugNode(
    val node: DebugTreeNode,
    val depth: Int
)

/**
 * 将调试节点树按展开状态扁平化为可见行列表。
 */
private fun flattenDebugTree(root: DebugTreeNode?, expanded: Set<String>): List<FlatDebugNode> {
    if (root == null) return emptyList()
    val out = mutableListOf<FlatDebugNode>()
    fun walk(node: DebugTreeNode, depth: Int) {
        node.item.forEach { child ->
            out.add(FlatDebugNode(child, depth))
            if (child.item.isNotEmpty() && child.id in expanded) {
                walk(child, depth + 1)
            }
        }
    }
    walk(root, 0)
    return out
}
