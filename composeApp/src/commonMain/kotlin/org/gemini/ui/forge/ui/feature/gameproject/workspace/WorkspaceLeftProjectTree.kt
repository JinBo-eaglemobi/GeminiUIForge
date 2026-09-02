package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.gp_tree_empty
import geminiuiforge.composeapp.generated.resources.gp_ws_left
import geminiuiforge.composeapp.generated.resources.gp_ws_refresh
import org.gemini.ui.forge.model.gameproject.ResourceTreeNode
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 左侧项目资源树面板。
 */
@Composable
fun ResourceTreePanel(
    viewModel: GameProjectViewModel,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    // 已展开的目录路径集合（会话级状态）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val visibleNodes = remember(viewModel.resourceTree, expandedPaths) {
        flattenTree(viewModel.resourceTree, expandedPaths)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 面板头：标题 + 刷新 + 折叠
            PanelHeader(
                title = stringResource(Res.string.gp_ws_left),
                onCollapse = onCollapse,
                collapseIcon = Icons.Default.ChevronLeft,
                actions = {
                    IconButton(onClick = { viewModel.refreshTree() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.gp_ws_refresh),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
            val tree = viewModel.resourceTree
            if (tree == null) {
                EmptyPanel(text = stringResource(Res.string.gp_tree_empty))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleNodes, key = { it.node.path }) { flat ->
                        TreeRow(
                            node = flat.node,
                            depth = flat.depth,
                            expanded = flat.node.path in expandedPaths,
                            onToggle = {
                                expandedPaths = if (flat.node.path in expandedPaths) {
                                    expandedPaths - flat.node.path
                                } else {
                                    expandedPaths + flat.node.path
                                }
                            },
                            onSelectHtml = { viewModel.selectHtml(flat.node.path) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 树节点行：缩进 + 展开箭头 + 文件夹/文件图标 + 名称；文件夹点击展开/收起，HTML 文件点击切换预览。
 */
@Composable
private fun TreeRow(
    node: ResourceTreeNode,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectHtml: () -> Unit
) {
    val isHtml = node.path.endsWith(".html", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clickable { if (node.isDirectory) onToggle() else if (isHtml) onSelectHtml() }
            .padding(start = (4 + depth * 12).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/收起指示器：仅目录项显示箭头，文件项以 Spacer 占位保持物理对齐（坚决不硬编码字符）
        if (node.isDirectory) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = if (node.isDirectory) {
                if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder
            } else {
                Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (node.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isHtml) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 资源树扁平化结果（文件内部私有数据类）。
 */
private data class FlatNode(
    val node: ResourceTreeNode,
    val depth: Int
)

/**
 * 将资源树按展开状态扁平化为可见行列表（文件内部私有函数）。
 */
private fun flattenTree(root: ResourceTreeNode?, expanded: Set<String>): List<FlatNode> {
    if (root == null) return emptyList()
    val out = mutableListOf<FlatNode>()
    fun walk(node: ResourceTreeNode, depth: Int) {
        node.children.forEach { child ->
            out.add(FlatNode(child, depth))
            if (child.isDirectory && child.path in expanded) {
                walk(child, depth + 1)
            }
        }
    }
    walk(root, 0)
    return out
}
