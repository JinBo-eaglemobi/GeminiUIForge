package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.extend.rememberClipboardAction
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * Markdown 高质感深色代码块容器组件 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 顶部 Header 展示代码块语言 Tag（如 KOTLIN, JSON, SHELL）；
 * 2. 具备深色专用背景（#1E1E24）与细微边框高光；
 * 3. 右上角配备内置成功状态反馈（1500ms 动态绿色对勾）的一键复制代码按钮；
 * 4. 严格遵循嵌套圆角守恒与人机工程学紧凑热区。
 */
@Composable
fun AppMarkdownCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val copy = rememberClipboardAction()
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    // 代码块专用深色背景与细微边框 (§3.1 与 §3.2 规范)
    val codeBackgroundColor = Color(0xFF1E1E24)
    val codeBorderColor = Color(0xFF2E323D)
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        shape = shape,
        color = codeBackgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, codeBorderColor),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部代码块操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181A1F))
                    .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.uppercase()?.ifBlank { "TEXT" } ?: "TEXT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF9DA5B4)
                    )
                )

                IconButton(
                    onClick = {
                        copy(code, "代码已复制到剪贴板")
                        isCopied = true
                        scope.launch {
                            delay(1500L)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(28.dp).tip(if (isCopied) "已复制" else "复制代码")
                ) {
                    Crossfade(targetState = isCopied) { copied ->
                        if (copied) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已复制",
                                tint = Color(0xFF98C379),
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制代码",
                                tint = Color(0xFFABB2BF),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 代码本体展示区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium)
            ) {
                Text(
                    text = code.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFABB2BF)
                    )
                )
            }
        }
    }
}
