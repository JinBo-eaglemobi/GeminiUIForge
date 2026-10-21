package org.gemini.ui.forge.ui.dialog.doc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.data.readBytesInternal
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * 外部文档、图元与素材详情沉浸式查看弹窗 (一文件一 Composable)
 *
 * 核心规范：
 * 1. 遵循 Design Tokens 与 spacing.dialogMediumWidth (720dp)；
 * 2. 嵌套同心圆角与 WCAG 2.2 无障碍对比度标准；
 * 3. 跨平台友好（基于 TemplateFile 与 getPlatform()），杜绝 Java 桌面专有 API；
 * 4. 纯文本与 Markdown 内容优雅平滑滚动阅读。
 */
@Composable
fun DocumentPreviewDialog(
    title: String,
    filePath: String? = null,
    content: String? = null,
    onDismissRequest: () -> Unit
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()

    var displayContent by remember(filePath, content) { mutableStateOf(content) }
    var resolvedAbsolutePath by remember(filePath) { mutableStateOf(filePath.orEmpty()) }
    var isLoading by remember(filePath, content) { mutableStateOf(content == null && !filePath.isNullTransBlank()) }
    var errorMessage by remember(filePath, content) { mutableStateOf<String?>(null) }
    var isMaximized by remember { mutableStateOf(false) }

    LaunchedEffect(filePath, content) {
        if (content != null) {
            displayContent = content
            isLoading = false
            return@LaunchedEffect
        }

        if (filePath.isNullTransBlank()) {
            displayContent = "（无可用文本内容）"
            isLoading = false
            return@LaunchedEffect
        }

        val pathStr = filePath ?: ""
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.Default) {
            try {
                var bytes: ByteArray? = null
                var detectedPath = pathStr

                // 1. 若已经是绝对路径形态（Windows "C:\" / "C:/" 或 Unix "/"），直接使用 readBytesInternal
                val isAbsolute = pathStr.contains(":/") || pathStr.contains(":\\") || pathStr.startsWith("/")
                if (isAbsolute) {
                    detectedPath = pathStr
                    bytes = readBytesInternal(pathStr)
                }

                // 2. 若未读取到或不是绝对路径，尝试 TemplateFile 沙盒环境解析
                if (bytes == null) {
                    val tf = try {
                        TemplateFile(pathStr)
                    } catch (_: Exception) {
                        null
                    }
                    if (tf != null) {
                        detectedPath = tf.getAbsolutePath()
                        bytes = tf.readBytes()
                    }
                }

                // 3. 兜底回退：若仍未读取到且此前未按绝对路径尝试过，直接尝试 readBytesInternal
                if (bytes == null && !isAbsolute) {
                    bytes = readBytesInternal(pathStr)
                }

                resolvedAbsolutePath = detectedPath
                if (bytes != null) {
                    displayContent = bytes.decodeToString()
                } else {
                    errorMessage = "未能读取文件内容或文件不存在：$resolvedAbsolutePath"
                }
            } catch (e: Exception) {
                errorMessage = "读取文件异常：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = if (isMaximized) {
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            } else {
                Modifier
                    .width(spacing.dialogLargeWidth)
                    .heightIn(min = 520.dp, max = 800.dp)
                    .padding(spacing.medium)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.medium)
            ) {
                // 1. 顶部 Header 操作栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(spacing.small))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (resolvedAbsolutePath.isNotBlank()) {
                            Text(
                                text = resolvedAbsolutePath,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }

                    // 定位文件夹
                    if (resolvedAbsolutePath.isNotBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    getPlatform().openInFileExplorer(resolvedAbsolutePath)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.tip("打开文件所在文件夹")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "打开所在文件夹",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 最大化 / 还原窗口
                    IconButton(
                        onClick = { isMaximized = !isMaximized },
                        modifier = Modifier.tip(if (isMaximized) "还原窗口" else "最大化窗口")
                    ) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isMaximized) "还原窗口" else "最大化窗口",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 关闭
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.tip("关闭")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = spacing.small)
                )

                // 2. 主体内容展示区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(spacing.small)
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        errorMessage != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        displayContent != null -> {
                            val scrollState = rememberScrollState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                            .padding(spacing.small)
                                    ) {
                                        Text(
                                            text = displayContent.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                lineHeight = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                VerticalScrollbarAdapter(
                                    scrollState = scrollState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(spacing.small))

                // 3. 底部信息与操作
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val charCount = displayContent?.length ?: 0
                    val lineCount = if (displayContent.isNullOrEmpty()) 0 else displayContent!!.lines().size
                    Text(
                        text = "共 $charCount 字符 · $lineCount 行",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        TextButton(
                            onClick = {
                                displayContent?.let { copy(it, "已复制文档全文到剪贴板") }
                            },
                            enabled = !displayContent.isNullOrBlank()
                        ) {
                            Text("复制全文")
                        }
                        Button(
                            onClick = onDismissRequest,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("完成")
                        }
                    }
                }
            }
        }
    }
}

private fun String?.isNullTransBlank(): Boolean = this == null || this.trim().isEmpty()
