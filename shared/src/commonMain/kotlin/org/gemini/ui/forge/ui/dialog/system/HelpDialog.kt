package org.gemini.ui.forge.ui.dialog.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.gemini.ui.forge.ui.component.dialog.AppDraggableDialog
import org.gemini.ui.forge.ui.component.dialog.draggableDialogHandle
import org.gemini.ui.forge.ui.component.dialog.rememberDraggableDialogState
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.ui.component.markdown.AppMarkdown
import org.gemini.ui.forge.ui.theme.AppShapes

/**
 * 帮助说明对话框 (一文件一 Composable)。
 *
 * 完整加载并渲染项目内建的 HELP.md 原生 Markdown 文档，
 * 支持代码块语法高亮与一键复制、超链接点击跳转，提供沉浸式原生帮助体验。
 *
 * @param onDismiss 点击关闭按钮或取消对话框时的回调。
 */
@OptIn(InternalResourceApi::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    var helpContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val bytes = readResourceBytes("HELP.md")
            helpContent = bytes.decodeToString()
        } catch (e: Exception) {
            helpContent = "### 帮助文档加载失败\n\n未能读取内建 HELP.md 资源文件，错误原因：`${e.message}`"
        } finally {
            isLoading = false
        }
    }

    val draggableState = rememberDraggableDialogState()

    AppDraggableDialog(
        onDismissRequest = onDismiss,
        state = draggableState
    ) {
        Card(
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.90f).fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableDialogHandle(draggableState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.help_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // Markdown 内容呈现区域
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 8.dp)
                        ) {
                            AppMarkdown(
                                content = helpContent ?: "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { getPlatform().openInBrowser("https://raw.githubusercontent.com/JinBo-eaglemobi/GeminiUIForge/refs/heads/master/shared/src/commonMain/resources/HELP.md") },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.help_action_open_browser))
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.medium
                    ) {
                        Text(stringResource(Res.string.close))
                    }
                }
            }
        }
    }
}
