package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 列表组件 (LIST) 专属属性配置面板。
 * 所有尺寸、间距与字号 100% 动态读取 [LocalAppSpacing] 与 [MaterialTheme.typography]，自动适配桌面与移动双模式。
 */
@Composable
fun ListPropertiesSection(
    properties: BlockProperties.ListProperties,
    onPropertiesChanged: (BlockProperties.ListProperties) -> Unit,
    availablePages: List<String> = emptyList(),
    isPageDragging: Boolean = false,
    draggedPageId: String? = null,
    dragCursorPosition: androidx.compose.ui.geometry.Offset? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    val safeLineCount = properties.lineCount.coerceAtLeast(0)
    val safeColumnCount = properties.columnCount.coerceAtLeast(0)
    val safeLineGap = properties.lineGap.coerceAtLeast(0)
    val safeColumnGap = properties.columnGap.coerceAtLeast(0)
    val safeItemWidth = properties.itemWidth.coerceAtLeast(0)
    val safeItemHeight = properties.itemHeight.coerceAtLeast(0)

    // 拖拽吸附检测
    var itemDropBounds by remember { mutableStateOf<Rect?>(null) }
    var wasHovered by remember { mutableStateOf(false) }
    var lastDraggedId by remember { mutableStateOf<String?>(null) }
    val isDropHovered = isPageDragging && itemDropBounds != null && dragCursorPosition != null && itemDropBounds!!.contains(dragCursorPosition)

    LaunchedEffect(isPageDragging, isDropHovered, draggedPageId) {
        if (isPageDragging) {
            if (draggedPageId != null) lastDraggedId = draggedPageId
            wasHovered = isDropHovered
        } else {
            if (wasHovered && lastDraggedId != null) {
                onPropertiesChanged(properties.copy(defaultItem = lastDraggedId!!))
            }
            wasHovered = false
            lastDraggedId = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.inspectorRowSpacing)
    ) {
        // 1. 列表布局 (复用统一组件 DropdownInputField)
        val layoutOptions = listOf(
            "SingleRow" to "单行横排 (SingleRow)",
            "SingleColumn" to "单列竖排 (SingleColumn)",
            "FlowHorizontal" to "横向流动折行 (FlowHorizontal)",
            "FlowVertical" to "纵向流动折列 (FlowVertical)",
            "Pagination" to "分页网格 (Pagination)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "列表布局",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            DropdownInputField(
                value = layoutOptions.find { it.first == properties.listLayout }?.second ?: properties.listLayout,
                onValueChange = {},
                items = layoutOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(listLayout = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.listLayout },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. 行数与列数
        InspectorSimpleDualPropRow(
            label1 = "行数",
            value1 = safeLineCount.toString(),
            onValueChange1 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(lineCount = v))
            },
            label2 = "列数",
            value2 = safeColumnCount.toString(),
            onValueChange2 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(columnCount = v))
            },
            isFloat = false,
            tooltip1 = "物理行数，0 为自适应，在流动或分页时有效",
            tooltip2 = "物理列数，0 为自适应，在横向折行或分页时有效"
        )

        // 3. 行距与列距
        InspectorSimpleDualPropRow(
            label1 = "行距",
            value1 = safeLineGap.toString(),
            onValueChange1 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(lineGap = v))
            },
            label2 = "列距",
            value2 = safeColumnGap.toString(),
            onValueChange2 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(columnGap = v))
            },
            isFloat = false,
            tooltip1 = "行与行之间的物理间距 (px)",
            tooltip2 = "列与列之间的物理间距 (px)"
        )

        // 4. 项宽与项高
        InspectorSimpleDualPropRow(
            label1 = "项宽",
            value1 = safeItemWidth.toString(),
            onValueChange1 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(itemWidth = v))
            },
            label2 = "项高",
            value2 = safeItemHeight.toString(),
            onValueChange2 = {
                val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                onPropertiesChanged(properties.copy(itemHeight = v))
            },
            isFloat = false,
            tooltip1 = "卡片子项默认宽度 (px)，0 为自适应",
            tooltip2 = "卡片子项默认高度 (px)，0 为自适应"
        )

        // 5. 对齐方式 (水平对齐 & 垂直对齐)
        val alignOptions = listOf("left" to "靠左", "center" to "居中", "right" to "靠右")
        val valignOptions = listOf("top" to "靠顶", "middle" to "居中", "bottom" to "靠底")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "对齐方式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.weight(1f)
            ) {
                DropdownInputField(
                    value = alignOptions.find { it.first == properties.align }?.second ?: properties.align,
                    onValueChange = {},
                    items = alignOptions,
                    onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(align = key)) },
                    itemLabel = { it.second },
                    isSelected = { it.first == properties.align },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                DropdownInputField(
                    value = valignOptions.find { it.first == properties.verticalAlign }?.second ?: properties.verticalAlign,
                    onValueChange = {},
                    items = valignOptions,
                    onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(verticalAlign = key)) },
                    itemLabel = { it.second },
                    isSelected = { it.first == properties.verticalAlign },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6. 溢出处理
        val overflowOptions = listOf(
            "Visible" to "可见 (Visible)",
            "Hidden" to "隐藏 (Hidden)",
            "ScrollVertical" to "垂直滚动 (Vertical)",
            "ScrollHorizontal" to "水平滚动 (Horizontal)",
            "ScrollBoth" to "自由滚动 (Both)"
        )
        val currentOverflowMode = when {
            properties.overflow == 0 -> "Visible"
            properties.overflow == 1 -> "Hidden"
            properties.overflow == 2 && properties.scrollType == 0 -> "ScrollHorizontal"
            properties.overflow == 2 && properties.scrollType == 2 -> "ScrollBoth"
            else -> "ScrollVertical"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "溢出处理",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            DropdownInputField(
                value = overflowOptions.find { it.first == currentOverflowMode }?.second ?: "垂直滚动 (Vertical)",
                onValueChange = {},
                items = overflowOptions,
                onItemSelected = { (key, _) ->
                    val (newOverflow, newScroll) = when (key) {
                        "Visible" -> 0 to -1
                        "Hidden" -> 1 to -1
                        "ScrollHorizontal" -> 2 to 0
                        "ScrollBoth" -> 2 to 2
                        else -> 2 to 1
                    }
                    onPropertiesChanged(properties.copy(overflow = newOverflow, scrollType = newScroll))
                },
                itemLabel = { it.second },
                isSelected = { it.first == currentOverflowMode },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 7. 选择模式
        val selOptions = listOf(
            "None" to "无 (None)",
            "Single" to "单选 (Single)",
            "Multiple" to "多选 (Multiple)"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "选择模式",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )
            DropdownInputField(
                value = selOptions.find { it.first == properties.selectionMode }?.second ?: "无 (None)",
                onValueChange = {},
                items = selOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(selectionMode = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.selectionMode },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 8. 子项单页 (项目资源：只读 DropdownInputField + 拖入赋值)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "子项单页",
                maxLines = 1,
                softWrap = false,
                style = dynamicBodyStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.width(spacing.inspectorLabelWidth)
            )

            val dropShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords -> itemDropBounds = coords.boundsInWindow() }
                    .then(
                        if (isDropHovered) {
                            Modifier
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), dropShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), dropShape)
                        } else Modifier
                    )
            ) {
                DropdownInputField(
                    value = properties.defaultItem,
                    onValueChange = { if (it.isEmpty()) onPropertiesChanged(properties.copy(defaultItem = "")) },
                    items = availablePages,
                    onItemSelected = { pageId -> onPropertiesChanged(properties.copy(defaultItem = pageId)) },
                    itemLabel = { it },
                    isSelected = { it == properties.defaultItem },
                    itemIcon = { Icons.Default.Layers },
                    leadingIcon = Icons.Default.Layers,
                    placeholder = "点击选择或拖入视图...",
                    emptyText = "暂无可用视图页面",
                    showClearButton = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tip("禁止直接打字。可点击下拉从当前所有视图中选择，也可直接从左侧「视图界面」拖拽页面至此释放赋值 (List 只能渲染单一页面)")
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        // 9. 高级布尔开关
        InspectorCheckboxRow(
            label = "自动调整子项尺寸",
            checked = properties.autoResizeItem,
            onCheckedChange = { onPropertiesChanged(properties.copy(autoResizeItem = it)) },
            tooltip = "自动按容器可用宽度/高度等比缩放或放大拉伸子项"
        )
        InspectorCheckboxRow(
            label = "折叠隐藏的项目",
            checked = properties.foldHiddenItems,
            onCheckedChange = { onPropertiesChanged(properties.copy(foldHiddenItems = it)) },
            tooltip = "当子项 isVisible 为 false 时不占位，后面项目自动前移填补"
        )
        InspectorCheckboxRow(
            label = "点击项目时平滑滚动至可见",
            checked = properties.scrollItemToViewFully,
            onCheckedChange = { onPropertiesChanged(properties.copy(scrollItemToViewFully = it)) },
            tooltip = "点击未完全露出的边缘子项时，列表自动平滑滚动使其 100% 露出"
        )
    }
}
