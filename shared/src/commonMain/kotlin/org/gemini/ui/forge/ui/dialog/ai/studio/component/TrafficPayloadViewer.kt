package org.gemini.ui.forge.ui.dialog.ai.studio.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.common.ScrollEdgeNavConfig
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 报文中折叠 Base64 数据的深度查看与图片预览弹层
 */
@Composable
fun TrafficPayloadViewer(
    payloadText: String,
    isSignature: Boolean = false,
    onDismiss: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val copyAction = rememberClipboardAction()

    // 尝试探测并解码为图片字节流（排除思维签名且校验图片魔数）
    val imageBytes = remember(payloadText, isSignature) {
        if (isSignature) return@remember null
        try {
            val rawB64 = when {
                payloadText.startsWith("data:image") && payloadText.contains(",") ->
                    payloadText.substringAfter(",")
                else -> payloadText.trim()
            }
            @OptIn(ExperimentalEncodingApi::class)
            val bytes = Base64.decode(rawB64)
            if (isImageBytes(bytes)) bytes else null
        } catch (_: Throwable) {
            null
        }
    }

    val sizeKb = remember(payloadText) {
        (payloadText.length * 3 / 4) / 1024
    }

    var activeTab by remember(imageBytes) {
        mutableStateOf(if (imageBytes != null) 0 else 1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ★ 依据 UI 规范响应式设计：占屏幕视口宽度的 72%，范围收拢在 720dp ~ 1100dp，杜绝两侧空白浪费与狭窄拥挤
            val dynamicWidth = (maxWidth * 0.72f).coerceIn(spacing.dialogMediumWidth, 1100.dp)

            Surface(
                modifier = Modifier
                    .width(dynamicWidth)
                    .fillMaxHeight(0.88f)
                    .padding(spacing.medium),
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.large)
                ) {
                    // 顶部标题行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val titleIcon = when {
                                imageBytes != null -> Icons.Default.Image
                                isSignature -> Icons.Default.Psychology
                                else -> Icons.Default.Psychology
                            }
                            val titleText = when {
                                imageBytes != null -> "Base64 图片查看器 (~${sizeKb} KB)"
                                isSignature -> "思维签名查看器 (~${sizeKb} KB)"
                                else -> "数据载荷查看器 (~${sizeKb} KB)"
                            }
                            Icon(
                                imageVector = titleIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(spacing.small))
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.tip("关闭")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(Modifier.height(spacing.small))

                    // 若检测到图片数据，提供 [图片预览 / 原文数据] 切换 Tab；若非图片数据，直接提供复制原文按钮
                    if (imageBytes != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 修复 SegmentedButton 高度导致的文字截断 Bug：统一遵循 36dp 标准控件高度
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.height(36.dp)) {
                                SegmentedButton(
                                    selected = activeTab == 0,
                                    onClick = { activeTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    label = {
                                        Text(
                                            text = "图片直接预览",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                )
                                SegmentedButton(
                                    selected = activeTab == 1,
                                    onClick = { activeTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    label = {
                                        Text(
                                            text = "Base64 纯文本",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = { copyAction(payloadText, "已复制完整 Base64 原文") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).tip("一键复制完整未截断的 Base64 字符串")
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text("复制原文", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSignature) "思维链数字签名数据（非图像载荷）" else "非图像二进制载荷",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            OutlinedButton(
                                onClick = { copyAction(payloadText, if (isSignature) "已复制完整思维签名" else "已复制完整数据载荷") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).tip(if (isSignature) "一键复制完整思维签名密文" else "一键复制完整未截断字符串")
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(spacing.extraSmall))
                                Text(if (isSignature) "复制签名" else "复制原文", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                // 内容展示区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(AppShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), AppShapes.medium)
                        .padding(spacing.small)
                ) {
                    if (imageBytes != null && activeTab == 0) {
                        // 图片直接预览
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = imageBytes,
                                contentDescription = "Decoded Base64 Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(spacing.small)
                            )
                        }
                    } else {
                        // 纯文本展示区
                        val textScrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = spacing.small)
                                    .verticalScroll(textScrollState)
                                    .padding(spacing.small)
                            ) {
                                Text(
                                    text = payloadText,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            VerticalScrollbarAdapter(
                                scrollState = textScrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                edgeNav = ScrollEdgeNavConfig(
                                    alignment = Alignment.BottomEnd,
                                    modifier = Modifier.padding(end = 12.dp, bottom = 12.dp)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
}


/**
 * 严格校验二进制流是否符合常见图像格式魔数（PNG, JPEG, WEBP, GIF），
 * 避免误将思维签名或常规二进制 Base64 数据识别为图片
 */
private fun isImageBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    // PNG: 89 50 4E 47
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
    ) return true
    // JPEG: FF D8 FF
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    ) return true
    // GIF: 47 49 46
    if (bytes.size >= 6 &&
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
    ) return true
    // WEBP: RIFF....WEBP (52 49 46 46 ... 57 45 42 50)
    if (bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
    ) return true
    return false
}


