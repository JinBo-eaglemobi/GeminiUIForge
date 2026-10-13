package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.ui.uistudio.model.UIAssetItem
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.gemini.ui.forge.utils.toImageBitmap
import kotlin.math.roundToInt

/**
 * 素材拖拽过程中跟随鼠标指针实时移动的浮动镜像徽标 (Drag Ghost Badge)。
 *
 * 核心规范：
 * 1. 鼠标点击/抓取位置恒定锚定在素材图标几何正中心；
 * 2. 徽标根据素材原始分辨率宽高比等比自适应缩放（最大限定 52dp），无多余变形；
 * 3. 移除冗余文字栏，纯粹呈现微透明质感与高亮描边的切片画面。
 */
@Composable
fun UIStudioDragGhostBadge(
    asset: UIAssetItem?,
    cursorPos: Offset?,
    modifier: Modifier = Modifier
) {
    if (asset == null || cursorPos == null) return

    val density = LocalDensity.current
    var bitmap by remember(asset.relativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(asset.relativePath) {
        withContext(Dispatchers.Default) {
            val bytes = readLocalFileBytes(asset.relativePath)
                ?: try {
                    TemplateFile(asset.relativePath).readBytes()
                } catch (_: Exception) {
                    null
                }
            if (bytes != null) {
                bitmap = bytes.toImageBitmap()
            }
        }
    }

    // 计算等比缩放尺寸（最大限定为 52dp）
    val maxBadgeSizeDp = 52.dp
    val aspect = if (asset.height > 0) asset.width.toFloat() / asset.height.toFloat() else 1f
    val (badgeWidthDp, badgeHeightDp) = if (aspect >= 1f) {
        val w = maxBadgeSizeDp
        val h = (maxBadgeSizeDp.value / aspect).coerceIn(24f, maxBadgeSizeDp.value).dp
        w to h
    } else {
        val h = maxBadgeSizeDp
        val w = (maxBadgeSizeDp.value * aspect).coerceIn(24f, maxBadgeSizeDp.value).dp
        w to h
    }

    val badgeWidthPx = with(density) { badgeWidthDp.toPx() }
    val badgeHeightPx = with(density) { badgeHeightDp.toPx() }

    var ghostContainerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { ghostContainerCoords = it }
    ) {
        Surface(
            color = Color(0xD90F172A), // 半透明深色质感底色
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(badgeWidthDp, badgeHeightDp)
                .offset {
                    val origin = ghostContainerCoords?.takeIf { it.isAttached }?.boundsInWindow()?.let { Offset(it.left, it.top) } ?: Offset.Zero
                    val localCursorX = cursorPos.x - origin.x
                    val localCursorY = cursorPos.y - origin.y
                    IntOffset(
                        (localCursorX - badgeWidthPx / 2f).roundToInt(),
                        (localCursorY - badgeHeightPx / 2f).roundToInt()
                    )
                }
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
