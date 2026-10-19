package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gemini.ui.forge.ui.component.tip
import org.gemini.ui.forge.ui.theme.LocalAppSpacing

/**
 * Markdown 代码块专用顶部控制操作栏组件 (一文件一 Composable 规范)。
 *
 * 核心特性：
 * 1. 左侧展示语言 Tag（大写 monospace 等宽字体）及折叠预览行数元信息；
 * 2. 右侧展示一键复制代码按钮与折叠/展开状态切换图标；
 * 3. 支持点击头部区域快速折叠/展开；
 * 4. 严格遵循嵌套圆角与人机工程学紧凑热区。
 */
@Composable
fun AppMarkdownCodeHeader(
    language: String?,
    lineCount: Int,
    isCollapsed: Boolean,
    isCopied: Boolean,
    onToggleCollapse: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF181A1F))
            .clickable(onClick = onToggleCollapse)
            .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                text = language?.uppercase()?.ifBlank { "TEXT" } ?: "TEXT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF9DA5B4)
                )
            )

            if (isCollapsed && lineCount > 0) {
                Text(
                    text = "($lineCount 行代码已折叠)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = Color(0xFF5C6370)
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            // 一键复制代码按钮
            IconButton(
                onClick = onCopyClick,
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

            // 折叠 / 展开状态切换按钮
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier.size(28.dp).tip(if (isCollapsed) "展开代码块" else "折叠代码块")
            ) {
                Crossfade(targetState = isCollapsed) { collapsed ->
                    if (collapsed) {
                        Icon(
                            Icons.Default.UnfoldMore,
                            contentDescription = "展开代码块",
                            tint = Color(0xFFABB2BF),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.UnfoldLess,
                            contentDescription = "折叠代码块",
                            tint = Color(0xFFABB2BF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
