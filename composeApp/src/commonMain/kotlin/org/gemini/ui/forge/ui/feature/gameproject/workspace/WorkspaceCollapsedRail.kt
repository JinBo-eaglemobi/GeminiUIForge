package org.gemini.ui.forge.ui.feature.gameproject.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * 折叠后的窄轨道组件（通用组件）。
 * 整条轨道均可点击展开（兼容紧凑模式下 IconButton 最小尺寸被 36dp 轨道裁切的问题）。
 */
@Composable
fun CollapsedRail(onExpand: () -> Unit, expandIcon: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight()
            .clickable { onExpand() }
            .pointerHoverIcon(PointerIcon.Hand),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            expandIcon()
        }
    }
}
