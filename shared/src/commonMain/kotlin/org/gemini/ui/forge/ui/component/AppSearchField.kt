package org.gemini.ui.forge.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全局紧凑型独立自定义搜索框组件。
 *
 * 专门替代 Material 3 在极小高度下因内部默认 Padding 导致文字被截断的 OutlinedTextField。
 * 严格遵循“一文件一 Composable”规范与设计系统 Tokens，内容绝对垂直居中且无高度坍塌。
 *
 * @param value 当前搜索关键词
 * @param onValueChange 关键词变更回调
 * @param modifier 外部修饰符
 * @param placeholder 占位提示文案
 * @param enabled 是否启用输入
 * @param height 搜索框固定高度，默认为系统紧凑标准 34.dp
 * @param onClear 自定义清空回调，为 null 时默认触发 onValueChange("")
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    enabled: Boolean = true,
    height: Dp = 34.dp,
    onClear: (() -> Unit)? = null
) {
    val spacing = LocalAppSpacing.current
    val shape = RoundedCornerShape(8.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            fontSize = 12.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.35f else 0.15f),
                        shape = shape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.5f else 0.25f),
                        shape = shape
                    )
                    .padding(horizontal = spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.38f),
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty() && enabled) {
                    IconButton(
                        onClick = { onClear?.invoke() ?: onValueChange("") },
                        modifier = Modifier
                            .size(18.dp)
                            .tip("清空搜索")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    )
}
