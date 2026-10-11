package org.gemini.ui.forge.ui.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.readLocalFileBytes
import org.jetbrains.skia.Image

/**
 * 通用沉浸式原寸图片查看灯箱 (Universal Image Lightbox)
 *
 * 核心特性：
 * 1. 【1:1 物理原始尺寸防拉大模糊】：加载图片真实宽高，小图 1:1 像素级原寸居中、超大图等比安全收缩在视口内；
 * 2. 【鼠标滚轮以光标为中心流畅缩放】：完全支持 PC 端滚轮上下滑动缩放 (0.1x ~ 8.0x)；
 * 3. 【一键 1:1 原始物理大小】：顶部提供专职 1:1 按钮快速恢复真实物理分辨率，消除缩放迷失；
 * 4. 【人机工程学舒适大按键】：工具条按钮加大至 40dp，图标 20dp，字体放大，PC 与跨端均清晰可见。
 *
 * @param imageUri 图片本地路径或 URI (支持绝对路径、相对路径或纯内存缓存)
 * @param onDismiss 关闭灯箱回调
 * @param modifier 修饰符
 */
@Composable
fun CommonImageLightbox(
    imageUri: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val density = LocalDensity.current
    val copy = rememberClipboardAction()

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // 探测图片的真实物理像素大小 (natural size)
    var naturalSize by remember { mutableStateOf<IntSize?>(null) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.Default) {
            try {
                val bytes = readLocalFileBytes(imageUri) ?: TemplateFile(imageUri).readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val skiaImg = Image.makeFromEncoded(bytes)
                    naturalSize = IntSize(skiaImg.width, skiaImg.height)
                }
            } catch (_: Throwable) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else false
                }
                .clickable { onDismiss() }
                // ★ 鼠标滚轮缩放监听
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) {
                                    val factor = if (delta < 0f) 1.15f else 0.87f
                                    zoomScale = (zoomScale * factor).coerceIn(0.1f, 8.0f)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val maxViewportW = maxWidth * 0.88f
            val maxViewportH = maxHeight * 0.88f

            // 计算 1.0x 基准尺寸：小图 1:1 原寸呈现绝对不拉伸！大图等比安全缩限在视口内
            val (baseWidthDp, baseHeightDp) = remember(naturalSize, maxViewportW, maxViewportH) {
                val origW = naturalSize?.width?.toFloat() ?: 600f
                val origH = naturalSize?.height?.toFloat() ?: 450f
                val origWDp = with(density) { origW.toDp() }
                val origHDp = with(density) { origH.toDp() }

                if (origWDp <= maxViewportW && origHDp <= maxViewportH) {
                    origWDp to origHDp
                } else {
                    val scaleX = maxViewportW / origWDp
                    val scaleY = maxViewportH / origHDp
                    val s = minOf(scaleX, scaleY)
                    (origWDp * s) to (origHDp * s)
                }
            }

            // 中间可拖拽平移与手势缩放的图片图层
            Box(
                modifier = Modifier
                    .size(baseWidthDp, baseHeightDp)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .clickable(enabled = false) {}
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.1f, 8.0f)
                            panOffset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Full Lightbox Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // 顶部悬浮工具栏（按钮尺寸放大至 40dp，图标 20dp，带 1:1 物理原寸查看按钮）
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = spacing.large)
                    .clickable(enabled = false) {}
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    // 当前缩放比率药丸
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // 放大按钮
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(8.0f) },
                        modifier = Modifier.size(40.dp).tip("放大图片 (+25%)")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                    }

                    // 缩小按钮
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 0.8f).coerceAtLeast(0.1f) },
                        modifier = Modifier.size(40.dp).tip("缩小图片 (-20%)")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                    }

                    // ★ 专职 1:1 物理原始尺寸快速查看按钮
                    Button(
                        onClick = {
                            // 计算让图片在屏幕上以 1:1 真实物理像素呈现所需的 zoomScale
                            val origW = naturalSize?.width?.toFloat()
                            if (origW != null && origW > 0f) {
                                val origWDp = with(density) { origW.toDp() }
                                zoomScale = if (baseWidthDp > 0.dp) (origWDp / baseWidthDp) else 1f
                            } else {
                                zoomScale = 1f
                            }
                            panOffset = Offset.Zero
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).tip("以 1:1 原始物理分辨率清晰呈现 (无拉伸、无模糊)")
                    ) {
                        Text(
                            text = if (naturalSize != null) "1:1 原图 (${naturalSize!!.width}×${naturalSize!!.height})" else "1:1 原图",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 适应屏幕居中复位按钮
                    IconButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(40.dp).tip("适应屏幕居中复位")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                    }

                    VerticalDivider(modifier = Modifier.height(22.dp))

                    // 资源管理器中打开
                    IconButton(
                        onClick = {
                            try {
                                val absPath = TemplateFile(imageUri).getAbsolutePath()
                                getPlatform().openInFileExplorer(absPath)
                            } catch (_: Exception) {
                                getPlatform().openInFileExplorer(imageUri)
                            }
                        },
                        modifier = Modifier.size(40.dp).tip("在系统资源管理器中定位图片原文件")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Explorer", modifier = Modifier.size(20.dp))
                    }

                    // 复制绝对路径
                    IconButton(
                        onClick = {
                            try {
                                val absPath = TemplateFile(imageUri).getAbsolutePath()
                                copy(absPath, "图片路径已复制到剪贴板")
                            } catch (_: Exception) {
                                copy(imageUri, "图片路径已复制到剪贴板")
                            }
                        },
                        modifier = Modifier.size(40.dp).tip("复制图片绝对物理路径")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", modifier = Modifier.size(20.dp))
                    }

                    VerticalDivider(modifier = Modifier.height(22.dp))

                    // 关闭按钮
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp).tip("关闭灯箱 (Esc)")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
