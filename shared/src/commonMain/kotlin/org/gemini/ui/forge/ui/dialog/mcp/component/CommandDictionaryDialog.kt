package org.gemini.ui.forge.ui.dialog.mcp.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.gemini.ui.forge.extend.copyOnClick

import org.gemini.ui.forge.service.mcp.McpCommandCategory
import org.gemini.ui.forge.service.mcp.McpCommandDictionary
import org.gemini.ui.forge.service.mcp.McpCommandEntry
import org.gemini.ui.forge.service.mcp.McpToolRegistry
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * MCP 交互指令与工具字典说明独立弹窗组件 (工整单列流式排版架构)
 *
 * 遵循红线与人机工程学规范：
 * 1. 一文件一 Composable
 * 2. 采用大型标准宽度 Token (spacing.dialogLargeWidth: 960.dp)，高度 88% 自适应
 * 3. 动态响应式联动 McpToolRegistry，实时呈现全部最新注册工具
 * 4. 采用工整统一的单列流式排版，每项横向贯穿通栏，高度自然包裹无参差，扫描动线流畅
 * 5. 父子同心圆角几何守恒 (R_outer 20dp, R_inner 12dp)
 * 6. PC 交互按钮与胶囊挂载 Tooltip (Modifier.tip)
 */
@Composable
fun CommandDictionaryDialog(
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    // 动态感知注册中心的所有工具变动
    val toolsState by McpToolRegistry.tools.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(McpCommandCategory.ALL) }

    // 动态派生所有条目
    val allEntries = remember(toolsState) {
        McpCommandDictionary.getAllDynamicEntries()
    }

    // 根据分类和搜索过滤
    val filteredEntries = remember(allEntries, selectedCategory, searchQuery) {
        allEntries.filter { entry ->
            val matchCategory = selectedCategory == McpCommandCategory.ALL || entry.category == selectedCategory
            val matchSearch = if (searchQuery.isBlank()) {
                true
            } else {
                entry.name.contains(searchQuery.trim(), ignoreCase = true) ||
                        entry.description.contains(searchQuery.trim(), ignoreCase = true)
            }
            matchCategory && matchSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(spacing.dialogLargeWidth) // 960.dp 大型视口标准
                .fillMaxHeight(0.88f)
                .padding(spacing.medium),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.large)
            ) {
                // 1. 顶部标题栏与动态统计
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Help,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = "MCP 交互指令与工具字典速查",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(spacing.small))
                        // 动态数量徽章
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "共 ${allEntries.size} 项 / 筛选中 ${filteredEntries.size} 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).tip("关闭窗口")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                // 2. 搜索输入框与快速过滤
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.extraSmall),
                    placeholder = {
                        Text("按指令标识名或中文描述搜索（如 analyze、template、UI、确认 等）...", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp).tip("清空搜索条件")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )

                Spacer(Modifier.height(spacing.extraSmall))

                // 3. 分类切换胶囊栏
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(McpCommandCategory.entries) { category ->
                        val count = remember(allEntries, category) {
                            if (category == McpCommandCategory.ALL) allEntries.size
                            else allEntries.count { it.category == category }
                        }
                        val isSelected = selectedCategory == category

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedCategory = category }
                                .tip("切换至 ${category.displayName} 分组")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.extraSmall))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 4. 指令列表容器（双列并排 Bento 视效）
                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到与「$searchQuery」相符的指令或工具",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = spacing.small),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        items(filteredEntries, key = { it.name }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.medium, vertical = spacing.small)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                // 点击即复制指令名（复用项目剪贴板规范扩展）
                                                modifier = Modifier
                                                    .copyOnClick(entry.name, "已复制指令名: ${entry.name}")
                                                    .tip("点击复制指令名")
                                            )

                                            Spacer(Modifier.width(spacing.small))

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (entry.isTool) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = if (entry.isTool) entry.category.displayName else "系统协议",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = if (entry.isTool) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (entry.isTool) "MCP 工具" else "原生 JSON-RPC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(Modifier.height(spacing.extraSmall))

                                    Text(
                                        text = entry.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
