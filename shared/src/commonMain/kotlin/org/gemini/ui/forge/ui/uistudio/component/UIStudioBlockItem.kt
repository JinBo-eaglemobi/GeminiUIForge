package org.gemini.ui.forge.ui.uistudio.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.ui.SerialRect
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIBlockType
import org.gemini.ui.forge.utils.toImageBitmap

/**
 * UI Studio 独立图元项渲染组件
 *
 * 负责在画布逻辑坐标系下渲染单个已切片/已入库的图元实体，展示真实的切片图像与工程级边界药丸。
 * 支持鼠标左键点击选中与左键拖拽平移。
 * 遵循一文件一 Composable 铁律。
 */
@Composable
fun UIStudioBlockItem(
    block: UIBlock,
    isSelected: Boolean,
    absBounds: SerialRect = block.toAbsoluteBounds(),
    inheritedOpacity: Float = 1.0f,
    scale: Float = 1.0f,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {},
    onDragDelta: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember(block.id, block.currentImageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(block.id, block.currentImageUri) {
        withContext(Dispatchers.Default) {
            val file = block.currentImageUri ?: block.referenceImage
            var bytes = file?.readBytes()
            // 历史路径失效自愈探测：若读不出，尝试从规范 assets/image/{id}.png 读取
            if (bytes == null || bytes.isEmpty()) {
                val rel = file?.relativePath ?: ""
                val project = rel.substringAfter("templates/").substringBefore("/assets").ifBlank { "New_UI_Game" }
                val fallbackRel = "templates/$project/assets/image/${block.id}.png"
                bytes = try {
                    org.gemini.ui.forge.data.TemplateFile(fallbackRel).readBytes()
                } catch (_: Exception) {
                    null
                }
            }
            if (bytes != null && bytes.isNotEmpty()) {
                try {
                    imageBitmap = bytes.toImageBitmap()
                } catch (_: Exception) {
                    imageBitmap = null
                }
            }
        }
    }

    val b = absBounds
    val width = (b.right - b.left).coerceAtLeast(1f)
    val height = (b.bottom - b.top).coerceAtLeast(1f)
    val displayName = block.userPromptZh.ifBlank { block.userPromptEn.ifBlank { block.id } }
    val isPureContainer = block.isPureContainer || block.type == UIBlockType.CONTAINER

    val typeColor = when (block.type) {
        UIBlockType.BUTTON, UIBlockType.SPIN_BUTTON -> Color(0xFFF59E0B) // 琥珀金橙
        UIBlockType.BACKGROUND -> Color(0xFF3B82F6) // 沉稳星空蓝
        UIBlockType.CONTAINER -> Color(0xFF8B5CF6) // 优雅紫罗兰
        UIBlockType.TEXT -> Color(0xFF10B981) // 清爽薄荷绿
        else -> Color(0xFF06B6D4) // 科技青蓝
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF9100) // 鲜明工程金橙色
        block.isLocked -> Color.Gray.copy(alpha = 0.45f)
        isPureContainer -> Color(0xFF8B5CF6).copy(alpha = 0.55f) // 柔和紫罗兰纯容器
        imageBitmap != null -> typeColor.copy(alpha = 0.35f) // 已绑定素材时细微优雅边框，不遮挡素材
        else -> typeColor.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .offset(x = b.left.dp, y = b.top.dp)
            .size(width = width.dp, height = height.dp)
            .graphicsLayer {
                scaleX = block.effectiveScaleX
                scaleY = block.effectiveScaleY
                alpha = (block.effectiveOpacity * inheritedOpacity).coerceIn(0.0f, 1.0f)
            }
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(2.dp)
            )
            .pointerInput(block.id, block.isLocked) {
                detectTapGestures(
                    onTap = {
                        if (!block.isLocked) {
                            onClick()
                        }
                    },
                    onDoubleTap = {
                        if (!block.isLocked) {
                            onDoubleTap()
                        }
                    }
                )
            }
            .then(
                if (!block.isLocked) {
                    Modifier.pointerInput(block.id, scale) {
                        detectDragGestures(
                            onDragStart = { onClick() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val safeScale = if (scale <= 0.01f) 1.0f else scale
                                onDragDelta(dragAmount.x / safeScale, dragAmount.y / safeScale)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    }
                } else Modifier
            )
    ) {
        // 渲染切片图片本体（纯容器不渲染底图，保持透明；使用 Fit 保持原始纵横比，绝不拉伸变形）
        val bmp = imageBitmap
        if (bmp != null && !isPureContainer) {
            Image(
                bitmap = bmp,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (!isPureContainer) {
            // 无图普通组件占位（遵循多维语义化配色）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(typeColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }

        // 选中高亮状态下的工程角标与名称微标签
        if (isSelected) {
            // 四角 6x6dp 金橙色控制手柄指示
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(6.dp)
                    .background(Color(0xFFFF9100))
                    .border(0.5.dp, Color.White)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .background(Color(0xFFFF9100))
                    .border(0.5.dp, Color.White)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(6.dp)
                    .background(Color(0xFFFF9100))
                    .border(0.5.dp, Color.White)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(6.dp)
                    .background(Color(0xFFFF9100))
                    .border(0.5.dp, Color.White)
            )

            // 左上角悬浮高对比名称胶囊
            Surface(
                color = Color(0xFFFF9100),
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = block.id,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // 锁定态右上角挂锁标记
        if (block.isLocked) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(bottomStart = 4.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "已锁定",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(2.dp).size(10.dp)
                )
            }
        }
    }
}
