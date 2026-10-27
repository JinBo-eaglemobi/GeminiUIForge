package org.gemini.ui.forge.ui.component.chat.mention

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.gemini.ui.forge.model.chat.MentionItem
import kotlin.math.roundToInt

/**
 * @ 联想菜单 PC 端悬停预览 Popup 宿主 (一文件一 Composable)
 *
 * 负责 350ms 悬停防抖、视口边缘预测与自适应左右翻转、垂直高度安全钳制。
 */
@Composable
fun MentionHoverPreviewPopup(
    hovered: Pair<MentionItem, IntOffset>?,
    panelBoundsInWindow: IntRect,
    config: MentionHoverPreviewConfig?,
    modifier: Modifier = Modifier
) {
    if (config == null) return

    var visibleItem by remember { mutableStateOf<Pair<MentionItem, IntOffset>?>(null) }

    // ★ 350ms 智能防抖：快速滑过不触发弹窗；切换行项时瞬时跟随
    LaunchedEffect(hovered?.first?.id) {
        if (hovered == null) {
            visibleItem = null
            return@LaunchedEffect
        }
        delay(350)
        visibleItem = hovered
    }

    val active = visibleItem ?: return
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowWidth = windowInfo.containerSize.width
    val windowHeight = windowInfo.containerSize.height

    val previewWidthPx = with(density) { MENTION_HOVER_PREVIEW_WIDTH.roundToPx() }
    val previewMaxHeightPx = with(density) { MENTION_HOVER_PREVIEW_MAX_HEIGHT.roundToPx() }
    val spacingPx = with(density) { 8.dp.roundToPx() }

    // 智能左右翻转与防越界位置计算器
    val positionProvider = remember(active, panelBoundsInWindow, windowWidth, windowHeight) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // 1. 水平方向：默认在面板右侧 (anchorBounds.right + spacing)
                val preferredRightX = anchorBounds.right + spacingPx
                val fitsOnRight = preferredRightX + popupContentSize.width <= windowSize.width

                val finalX = if (fitsOnRight) {
                    preferredRightX
                } else {
                    // 右侧超界，自动翻转至面板左侧
                    (anchorBounds.left - popupContentSize.width - spacingPx).coerceAtLeast(8)
                }

                // 2. 垂直方向：对齐当前悬停行项的顶部 Y
                val rowTopInWindow = anchorBounds.top + active.second.y
                val maxY = (windowSize.height - popupContentSize.height - 8).coerceAtLeast(8)
                val finalY = rowTopInWindow.coerceIn(8, maxY)

                return IntOffset(finalX, finalY)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        MentionHoverPreviewCard(
            item = active.first,
            config = config,
            modifier = modifier
        )
    }
}
