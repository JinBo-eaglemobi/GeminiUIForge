package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全项目统一封装下拉选择器/下拉输入框组件 (DropdownInputField)。
 *
 * 特性：
 * 1. 外观：无浮动标签时呈现工业流线胶囊，有浮动标签时自动适配表单；
 * 2. 动态尺度：高度、间距、图标尺寸与字号 100% 动态取自 [LocalAppSpacing] 与 [MaterialTheme.typography]，
 *    切换桌面紧凑模式与移动端触控模式时自动响应放大/缩小，绝不写死固定值；
 * 3. 弹出的下拉菜单 100% 复用项目统一组件 [AppMenu] 与 [AppMenuItemRow]；
 * 4. 通过 `Modifier.matchParentSize()` 精准锚定宿主高度并配合正向偏移，确保菜单严格在按钮正下方展开且不遮挡按钮。
 */
@Composable
fun <T> DropdownInputField(
    value: String,
    onValueChange: (String) -> Unit,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    isSelected: (T) -> Boolean = { false },
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    itemIcon: ((T) -> ImageVector?)? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showClearButton: Boolean = !readOnly,
    emptyText: String? = null,
    streamlined: Boolean = (label == null),
    shape: Shape = MaterialTheme.shapes.small,
    textStyle: TextStyle? = null,
    maxLines: Int = 1
) {
    val spacing = LocalAppSpacing.current
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val hasMenu = items.isNotEmpty() || emptyText != null
    val dropdownButtonWidth = spacing.iconButtonCompactSize
    val dynamicBodyStyle = if (spacing.isCompact) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { fieldWidthPx = it.width }
    ) {
        if (streamlined && readOnly) {
            val capsuleShape = RoundedCornerShape(spacing.inspectorFieldCornerRadius)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.inspectorFieldHeight)
                    .clip(capsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                    .border(
                        width = if (expanded) 1.dp else 0.8.dp,
                        color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = capsuleShape
                    )
                    .clickable(enabled = enabled && hasMenu) { expanded = !expanded }
                    .pointerHoverIcon(if (enabled && hasMenu) PointerIcon.Hand else PointerIcon.Default)
                    .padding(horizontal = spacing.inspectorFieldPaddingHorizontal)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.inspectorControlGap),
                    modifier = Modifier.weight(1f)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(spacing.inspectorInlineIconSize)
                        )
                    }
                    Text(
                        text = value.ifBlank { placeholder },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = (textStyle ?: dynamicBodyStyle).copy(
                            color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showClearButton && value.isNotEmpty() && enabled) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(spacing.inspectorInlineIconSize)
                                .clickable { onValueChange("") }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .tip("清除已选内容")
                        )
                        Spacer(Modifier.width(spacing.inspectorControlGap))
                    }
                    if (hasMenu) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(spacing.iconCompactSize)
                        )
                    }
                }
            }
        } else {
            SelectAllOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                placeholder = if (placeholder.isNotEmpty()) {
                    {
                        Text(
                            text = placeholder,
                            maxLines = 1,
                            style = (textStyle ?: dynamicBodyStyle).copy(
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                } else null,
                leadingIcon = if (leadingIcon != null) {
                    {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.inspectorInlineIconSize)
                        )
                    }
                } else null,
                readOnly = readOnly,
                enabled = enabled,
                singleLine = maxLines == 1,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                streamlined = streamlined,
                shape = shape,
                textStyle = textStyle,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showClearButton && value.isNotEmpty()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier.size(spacing.iconButtonMicroSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(spacing.inspectorInlineIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (hasMenu) {
                            Spacer(Modifier.width(dropdownButtonWidth))
                        }
                    }
                }
            )

            if (hasMenu) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(dropdownButtonWidth)
                            .clickable(enabled = enabled) { expanded = !expanded }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(spacing.iconStandardSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 全项目统一浮层菜单 AppMenu：通过 matchParentSize() 锚定触发控件全高，正向偏移展开绝不遮挡按钮
        if (hasMenu) {
            val fieldWidthDp = with(density) { fieldWidthPx.toDp() }
            val safeAnchorWidth = if (fieldWidthDp > 0.dp) fieldWidthDp else spacing.dialogAlertWidth / 2
            AppMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.matchParentSize(),
                tier = AppMenuTier.COMPACT,
                minMenuWidth = safeAnchorWidth,
                maxMenuWidth = maxOf(safeAnchorWidth, spacing.dialogAlertWidth * 0.7f),
                anchorWidth = safeAnchorWidth,
                offset = DpOffset(0.dp, spacing.extraSmall),
                isUpwardExplicit = false
            ) {
                if (items.isEmpty()) {
                    AppMenuItemRow(
                        title = emptyText ?: "",
                        onClick = { expanded = false }
                    )
                } else {
                    items.forEach { item ->
                        AppMenuItemRow(
                            title = itemLabel(item),
                            icon = itemIcon?.invoke(item),
                            isSelected = isSelected(item),
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
