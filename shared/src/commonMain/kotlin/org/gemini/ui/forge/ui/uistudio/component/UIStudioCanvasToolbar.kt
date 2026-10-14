package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.uistudio.UIStudioViewModel
import kotlin.math.roundToInt

/**
 * 画布右上角悬浮视口缩放与自适应居中工具栏。
 * 包含缩小、当前缩放比（点击重置 100%）、放大、自适应居中。
 */
@Composable
fun UIStudioCanvasToolbar(
    viewModel: UIStudioViewModel,
    modifier: Modifier = Modifier,
    containerSize: IntSize = IntSize.Zero
) {
    val state = viewModel.state
    val zoomPercent = (state.scale * 100f).roundToInt()

    Surface(
        color = Color(0xDD1E293B),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // 1. 缩小
            IconButton(
                onClick = { viewModel.zoomOut() },
                modifier = Modifier
                    .size(28.dp)
                    .tip("缩小画布 (滚轮向下)")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "缩小",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 2. 缩放百分比胶囊（点击还原 100% 原寸，不影响画布坐标）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        viewModel.resetZoomTo100()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .tip("还原为 100% 原始比例 (保持当前坐标)")
            ) {
                Text(
                    text = "$zoomPercent%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8) // 高亮青蓝
                )
            }

            // 3. 放大
            IconButton(
                onClick = { viewModel.zoomIn() },
                modifier = Modifier
                    .size(28.dp)
                    .tip("放大画布 (滚轮向上)")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "放大",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .padding(horizontal = 2.dp)
            ) {
                VerticalDivider(color = Color.White.copy(alpha = 0.2f))
            }

            // 4. 一键还原 100% 缩放值 (仅还原缩放比例，不改变画布坐标)
            IconButton(
                onClick = {
                    viewModel.resetZoomTo100()
                },
                modifier = Modifier
                    .size(28.dp)
                    .tip("一键还原 100% 缩放值 (保持当前坐标)")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "还原 100%",
                    tint = Color(0xFF38BDF8), // 青蓝指示原寸
                    modifier = Modifier.size(16.dp)
                )
            }

            // 5. 一键自适应居中
            IconButton(
                onClick = { viewModel.triggerFitToViewport() },
                modifier = Modifier
                    .size(28.dp)
                    .tip("一键自适应视口居中")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "居中",
                    tint = Color(0xFFA78BFA), // 紫色强调自适应
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
