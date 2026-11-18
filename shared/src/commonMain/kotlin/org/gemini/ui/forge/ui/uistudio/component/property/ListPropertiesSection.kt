package org.gemini.ui.forge.ui.uistudio.component.property

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.model.ui.BlockProperties
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 列表组件 (LIST) 专属属性配置面板。
 * 全行统一接入 [InspectorPropRow] 实现所有控件外框 0 误差左对齐与 50%/50% 对称栅格对齐。
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
        // 1. 列表布局
        val layoutOptions = listOf(
            "SingleRow" to "单行横排 (SingleRow)",
            "SingleColumn" to "单列竖排 (SingleColumn)",
            "FlowHorizontal" to "横向流动折行 (FlowHorizontal)",
            "FlowVertical" to "纵向流动折列 (FlowVertical)",
            "Pagination" to "分页网格 (Pagination)"
        )
        InspectorPropRow(label = "列表布局") {
            DropdownInputField(
                value = layoutOptions.find { it.first == properties.listLayout }?.second ?: properties.listLayout,
                onValueChange = {},
                items = layoutOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(listLayout = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.listLayout },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 行数与列数
        InspectorPropRow(label = "行数") {
            NumberOutlinedTextField(
                value = safeLineCount.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(lineCount = v))
                },
                isFloat = false,
                modifier = Modifier.weight(1f).tip("物理行数，0 为自适应，在流动或分页时有效")
            )
            NumberOutlinedTextField(
                value = safeColumnCount.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(columnCount = v))
                },
                prefixTag = "列数",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("物理列数，0 为自适应，在横向折行或分页时有效")
            )
        }

        // 3. 行距与列距
        InspectorPropRow(label = "行距") {
            NumberOutlinedTextField(
                value = safeLineGap.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(lineGap = v))
                },
                isFloat = false,
                modifier = Modifier.weight(1f).tip("行与行之间的物理间距 (px)")
            )
            NumberOutlinedTextField(
                value = safeColumnGap.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(columnGap = v))
                },
                prefixTag = "列距",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("列与列之间的物理间距 (px)")
            )
        }

        // 4. 项宽与项高
        InspectorPropRow(label = "项宽") {
            NumberOutlinedTextField(
                value = safeItemWidth.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(itemWidth = v))
                },
                isFloat = false,
                modifier = Modifier.weight(1f).tip("卡片子项默认宽度 (px)，0 为自适应")
            )
            NumberOutlinedTextField(
                value = safeItemHeight.toString(),
                onValueChange = {
                    val v = (it.toIntOrNull() ?: 0).coerceAtLeast(0)
                    onPropertiesChanged(properties.copy(itemHeight = v))
                },
                prefixTag = "项高",
                isFloat = false,
                modifier = Modifier.weight(1f).tip("卡片子项默认高度 (px)，0 为自适应")
            )
        }

        // 5. 对齐方式 (设计工具标准布局对齐图标 + 同心悬浮胶囊分段器)
        val alignOptions = listOf(
            InspectorSegmentedIconOption("left", Icons.AutoMirrored.Filled.AlignHorizontalLeft, "水平靠左 (left)"),
            InspectorSegmentedIconOption("center", Icons.Default.AlignHorizontalCenter, "水平居中 (center)"),
            InspectorSegmentedIconOption("right", Icons.AutoMirrored.Filled.AlignHorizontalRight, "水平靠右 (right)")
        )
        val valignOptions = listOf(
            InspectorSegmentedIconOption("top", Icons.Default.AlignVerticalTop, "垂直靠顶 (top)"),
            InspectorSegmentedIconOption("middle", Icons.Default.AlignVerticalCenter, "垂直居中 (middle)"),
            InspectorSegmentedIconOption("bottom", Icons.Default.AlignVerticalBottom, "垂直靠底 (bottom)")
        )
        InspectorPropRow(label = "对齐方式") {
            InspectorSegmentedIconGroup(
                options = alignOptions,
                selectedKey = properties.align,
                onSelected = { onPropertiesChanged(properties.copy(align = it)) },
                modifier = Modifier.weight(1f)
            )
            InspectorSegmentedIconGroup(
                options = valignOptions,
                selectedKey = properties.verticalAlign,
                onSelected = { onPropertiesChanged(properties.copy(verticalAlign = it)) },
                modifier = Modifier.weight(1f)
            )
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
        InspectorPropRow(label = "溢出处理") {
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 7. 选择模式
        val selOptions = listOf(
            "None" to "无 (None)",
            "Single" to "单选 (Single)",
            "Multiple" to "多选 (Multiple)"
        )
        InspectorPropRow(label = "选择模式") {
            DropdownInputField(
                value = selOptions.find { it.first == properties.selectionMode }?.second ?: "无 (None)",
                onValueChange = {},
                items = selOptions,
                onItemSelected = { (key, _) -> onPropertiesChanged(properties.copy(selectionMode = key)) },
                itemLabel = { it.second },
                isSelected = { it.first == properties.selectionMode },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 8. 子项单页 (只读下拉 + 拖入赋值)
        InspectorPropRow(label = "子项单页") {
            val dropShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
