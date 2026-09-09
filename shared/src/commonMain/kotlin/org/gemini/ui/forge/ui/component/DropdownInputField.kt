package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 下拉按钮的设计宽度：大号箭头图标 + 完整左右热区，与输入框尾部的透明占位严格等宽 */
private val DROPDOWN_BUTTON_WIDTH = 46.dp

/**
 * 通用下拉输入框组件：可编辑输入框与候选下拉列表的一体化组合。
 *
 * 特性：
 * 1. 输入框主体复用 [SelectAllOutlinedTextField]，保留键盘 (Tab) 聚焦自动全选等既有体验；
 * 2. [items] 非空时在输入框右侧显示通高下拉按钮：通过 matchParentSize 覆盖层实现，
 *    按钮高度与输入框完全一致、大号箭头图标随展开状态切换方向，鼠标悬停手型光标；
 * 3. 弹出的下拉菜单宽度与输入框严格一致（通过布局实测输入框像素宽度换算得到）；
 * 4. [showClearButton] 开启且输入框有内容时，显示一键清除按钮（点击回调 [onValueChange] 传空串）；
 * 5. 组件尺寸的紧凑/触控形态由 AppTheme 全局 Density 重映射统一接管，
 *    组件内部不包含任何布局模式判断与硬编码尺寸分支。
 *
 * @param value 输入框显示的文本
 * @param onValueChange 输入内容变化回调（含清除按钮触发的空串回传）
 * @param items 下拉候选数据源；为空时不显示下拉按钮与菜单
 * @param onItemSelected 下拉条目被选中时的回调
 * @param itemLabel 将候选项映射为展示文案
 * @param isSelected 判断候选项是否为当前选中项（选中项在菜单中显示对勾）
 * @param modifier 外部布局修饰符
 * @param label 输入框标签（悬浮标签）
 * @param readOnly 输入框是否只读（选中候选项锁定展示的场景）
 * @param enabled 输入框是否可用
 * @param visualTransformation 文本视觉变换（如密码掩码）
 * @param showClearButton 是否在有内容时显示一键清除按钮
 * @param shape 输入框外形
 * @param textStyle 文本样式
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
    readOnly: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showClearButton: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = 1
) {
    var expanded by remember { mutableStateOf(false) }
    // 输入框实测像素宽度：用于让弹出菜单与输入框严格等宽
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { fieldWidthPx = it.width }
    ) {
        SelectAllOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            readOnly = readOnly,
            enabled = enabled,
            singleLine = maxLines == 1,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            shape = shape,
            textStyle = textStyle,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 清除按钮：开启且有内容时显示，点击回传空串由业务层统一处理
                    if (showClearButton && value.isNotEmpty()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 通高下拉按钮的等宽透明占位：在输入框尾部预留按钮空间，避免文字被覆盖层遮挡
                    if (items.isNotEmpty()) {
                        Spacer(Modifier.width(DROPDOWN_BUTTON_WIDTH))
                    }
                }
            }
        )

        // 通高下拉按钮覆盖层：matchParentSize 使覆盖层与输入框物理同尺寸，
        // 内部按钮贴右缘 fillMaxHeight，从而保证按钮高度与输入框完全一致
        if (items.isNotEmpty()) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(DROPDOWN_BUTTON_WIDTH)
                        .clickable { expanded = !expanded }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 候选下拉菜单：宽度与输入框严格一致，选中项打勾标识
        if (items.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { fieldWidthPx.toDp() })
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = itemLabel(item),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        },
                        leadingIcon = if (isSelected(item)) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}
