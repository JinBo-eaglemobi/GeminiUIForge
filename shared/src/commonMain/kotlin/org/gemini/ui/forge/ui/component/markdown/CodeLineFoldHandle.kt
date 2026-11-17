package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.gemini.ui.forge.ui.component.AppIconButton
import org.gemini.ui.forge.ui.component.AppIconButtonTier
import org.gemini.ui.forge.ui.component.AppIconButtonVariant

/**
 * 代码行号旁微型折叠/展开触发手柄（一文件一 Composable）。
 *
 * 严格遵循 `AppIconButtonTier.MICRO` 四阶尺寸守恒规范（20dp 容器 / 12dp 图标 / 4dp 圆角），
 * 并由外层视口裁剪器（Viewport Culling）按需挂载于当前可视窗口内的可折叠行。
 */
@Composable
fun CodeLineFoldHandle(
    isFolded: Boolean,
    tooltip: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppIconButton(
        onClick = onToggle,
        imageVector = if (isFolded) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
        contentDescription = tooltip,
        tooltip = tooltip,
        tier = AppIconButtonTier.MICRO,
        variant = AppIconButtonVariant.GHOST,
        tint = if (isFolded) Color(0xFF61AFEF) else Color(0xFF5C6370),
        modifier = modifier
    )
}
