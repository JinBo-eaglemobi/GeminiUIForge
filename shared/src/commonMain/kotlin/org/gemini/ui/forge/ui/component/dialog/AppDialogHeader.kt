package org.gemini.ui.forge.ui.component.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 全平台通用 48dp 紧凑型弹窗标题栏。
 * 具备左侧图标与标题、右侧自定义工具栏、最右侧标准关闭按钮，并在中间可拖拽区域挂载 [draggableDialogHandle]。
 *
 * @param title 标题文字
 * @param modifier 标题栏外部修饰符
 * @param state 可选的 [DraggableDialogState]，若传入则自动将标题栏区域赋予拖拽与双击居中复位能力
 * @param icon 可选的前置图标插槽
 * @param actions 右侧自定义操作栏动作插槽
 * @param onClose 可选的关闭按钮点击回调，若为 null 则不呈现最右侧关闭图标
 */
@Composable
fun AppDialogHeader(
    title: String,
    modifier: Modifier = Modifier,
    state: DraggableDialogState? = null,
    icon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val spacing = LocalAppSpacing.current

    // 若传入了 state，则挂载拖拽与双击居中复位手势
    val dragModifier = if (state != null) {
        Modifier.draggableDialogHandle(state)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = spacing.medium)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 左侧前置图标
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = spacing.extraSmall),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(spacing.small))
        }

        // 2. 标题文案（单行截断防折行，支持紧随其后的空白拖拽抓手）
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 3. 中间空白区域：弹性撑满并兼作主要的拖拽安全抓手
        Spacer(modifier = Modifier.weight(1f).fillMaxHeight())

        // 4. 右侧自定义操作栏动作插槽
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()

            // 5. 最右侧关闭按钮（带关闭 Tooltip）
            if (onClose != null) {
                Spacer(modifier = Modifier.width(spacing.small))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .tip("关闭")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
