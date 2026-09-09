package org.gemini.ui.forge.ui.dialog.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.ui.ResourceItem
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.*



/**
 * 层级资源绑定设置弹窗。
 * 按层级级联选择资源配置表中的 key，并支持添加 (+)、更改（重选）、删除（逐级清除）以及展示说明。
 * 支持任意深度级联，智能解析 value 为 String 数组或 ResourceItem 数组。
 */
@Composable
fun ResourceBindingDialog(
    block: UIBlock,
    parentBlock: UIBlock?,
    configData: List<ResourceItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // 1. 父级已绑定路径的前缀
    val parentBindingPath = parentBlock?.resourceBindingPath ?: emptyList()
    val parentHasBinding = parentBindingPath.isNotEmpty()

    // 2. 当前模块已绑定的相对路径（除去父级绑定前缀）
    val initialSelections = remember(block.resourceBindingPath, parentBindingPath) {
        if (parentHasBinding) {
            // 如果父级已经绑定，且当前已绑定路径含有父级前缀，则截取剩余部分作为初始选择
            if (block.resourceBindingPath.size > parentBindingPath.size &&
                block.resourceBindingPath.take(parentBindingPath.size) == parentBindingPath
            ) {
                block.resourceBindingPath.drop(parentBindingPath.size)
            } else {
                emptyList()
            }
        } else {
            block.resourceBindingPath
        }
    }

    // 用户在弹窗中的临时级联选择状态（相对路径部分）
    val selections = remember { mutableStateListOf<String>().apply { addAll(initialSelections) } }

    // 每一级下拉菜单展开状态
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    // 核心寻找算法：给定一条相对或绝对路径，获取其下一级包含的所有子 ResourceItem 列表
    fun getChildrenForPath(path: List<String>): List<ResourceItem> {
        if (path.isEmpty()) {
            return configData
        }

        var currentItems = configData
        for (key in path) {
            val matchItem = currentItems.find { it.key == key } ?: return emptyList()
            currentItems = matchItem.child ?: emptyList()
        }

        return currentItems
    }

    // 用于计算并获取特定层级的候选资源列表
    fun getOptionsForLevel(level: Int): List<ResourceItem> {
        val path = parentBindingPath + selections.take(level)
        return getChildrenForPath(path)
    }

    // 判断特定层级选中的元素，在其后面是否还有下一级数组存在（能否显示 + 号）
    fun hasNextLevelFor(level: Int, selectedValue: String): Boolean {
        if (selectedValue.isBlank()) return false
        val fullPath = parentBindingPath + selections.take(level) + selectedValue
        return getChildrenForPath(fullPath).isNotEmpty()
    }

    // 根据当前的完整层级，获取最终叶子节点的描述信息
    val leafDescription = remember(selections.toList()) {
        val lastIndex = selections.indexOfLast { it.isNotBlank() }
        if (lastIndex == -1) null
        else {
            val selectedValue = selections[lastIndex]
            val parentPath = parentBindingPath + selections.take(lastIndex)
            getChildrenForPath(parentPath).find { it.key == selectedValue }?.description?.takeIf { it.isNotBlank() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // 解除平台默认宽度限制：级联层级较深时内容可自然展宽，紧凑模式下不再被压成窄条
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = stringResource(Res.string.res_binding_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 显示当前父级的上下文（如果父级已绑定）
                if (parentHasBinding) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = AppShapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(Res.string.res_binding_parent_limit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(Res.string.res_binding_parent_bound, parentBindingPath.joinToString(" > ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(Res.string.res_binding_parent_bound_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 级联选择区域
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.res_binding_path_select),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    val levelCount = if (selections.isEmpty()) 1 else selections.size

                    for (i in 0 until levelCount) {
                        val rawSelectedValue = selections.getOrNull(i)
                        // 若选项是占位空串或 null，视为未选择
                        val selectedValue = rawSelectedValue?.takeIf { it.isNotBlank() }
                        val options = getOptionsForLevel(i)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 层级小标签
                            Text(
                                text = stringResource(Res.string.res_binding_level_label, i + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(36.dp)
                            )

                            // 下拉选择框
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { expandedStates[i] = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.medium,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = selectedValue ?: stringResource(Res.string.res_binding_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedValue != null) MaterialTheme.colorScheme.onSurface 
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = expandedStates[i] ?: false,
                                    onDismissRequest = { expandedStates[i] = false },
                                    modifier = Modifier.width(280.dp)
                                ) {
                                    if (options.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.res_binding_empty), style = MaterialTheme.typography.bodySmall) },
                                            onClick = { expandedStates[i] = false },
                                            enabled = false
                                        )
                                    } else {
                                        options.forEach { optionItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        val displayName = if (!optionItem.type.isNullOrBlank()) {
                                                            "${optionItem.type}（${optionItem.key}）"
                                                        } else {
                                                            optionItem.key
                                                        }
                                                        Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        if (optionItem.description.isNotBlank()) {
                                                            Text(
                                                                text = optionItem.description, 
                                                                style = MaterialTheme.typography.labelSmall, 
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    // 更改值：清除后面所有级别的选择，防止数据不一致
                                                    while (selections.size > i) {
                                                        selections.removeAt(selections.size - 1)
                                                    }
                                                    selections.add(optionItem.key)
                                                    expandedStates[i] = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 后面是否显示 + 号（当选中的值下面有数组子集，且当前是 selections 的最后一级）
                            if (selectedValue != null && selections.size == i + 1 && hasNextLevelFor(i, selectedValue)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        // 自动在 selections 中添加空串，触发下一级渲染并展开
                                        val nextLevel = i + 1
                                        selections.add("")
                                        expandedStates[nextLevel] = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, stringResource(Res.string.res_binding_add_level), modifier = Modifier.size(18.dp))
                                }
                            }

                            // 每一级的删除按钮
                            if (rawSelectedValue != null) {
                                IconButton(
                                    onClick = {
                                        // 删除当前及后面所有的选择
                                        while (selections.size > i) {
                                            selections.removeAt(selections.size - 1)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.res_binding_delete_level),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // 显示单独选中项的描述 (如果有的话)
                        val levelDesc = remember(selectedValue, selections.toList()) {
                            if (selectedValue != null) {
                                val parentPath = parentBindingPath + selections.take(i)
                                getChildrenForPath(parentPath).find { it.key == selectedValue }?.description?.takeIf { it.isNotBlank() }
                            } else null
                        }

                        if (levelDesc != null) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 44.dp, top = 2.dp, bottom = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.small)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                // 层级描述支持选择复制
                                SelectionContainer {
                                    Text(
                                        text = levelDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // 如果后面还有，显示层级箭头指示
                        if (i < selections.size - 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // 终极整体描述展示
                if (leafDescription != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.res_binding_explanation),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        // 资源说明支持选择复制（绑定决策时可直接复制描述文本）
                        SelectionContainer {
                            Text(
                                text = leafDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 过滤掉 selections 中的空占位符，返回完整路径
                    val validSelections = selections.filter { it.isNotBlank() }
                    onConfirm(parentBindingPath + validSelections)
                },
                enabled = selections.any { it.isNotBlank() },
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.res_binding_action_bind))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.dialog_action_cancel))
            }
        }
    )
}
