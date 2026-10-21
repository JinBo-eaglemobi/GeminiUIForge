package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * 全局统一 Markdown 原生门面组件 (一文件一 Composable)
 *
 * 核心特性：
 * 1. 深度适配 Material 3 设计系统调色板与 Typography 规范；
 * 2. 代码块（CodeBlock / CodeFence）深度定制为 AppMarkdownCodeBlock（深色容器、语言 Tag、一键复制）；
 * 3. 自动支持 GFM 扩展规范（表格、删除线、超链接、多级标题等）。
 */
@Composable
fun AppMarkdown(
    content: String,
    modifier: Modifier = Modifier
) {
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        inlineCodeBackground = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        tableBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineSmall,
        h2 = MaterialTheme.typography.titleLarge,
        h3 = MaterialTheme.typography.titleMedium,
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.titleSmall,
        text = MaterialTheme.typography.bodyMedium,
        code = MaterialTheme.typography.bodySmall,
        quote = MaterialTheme.typography.bodyMedium,
        paragraph = MaterialTheme.typography.bodyMedium
    )

    val components = markdownComponents(
        codeFence = { model ->
            MarkdownCodeFence(content = model.content, node = model.node) { code, lang, _ ->
                AppMarkdownCodeBlock(
                    code = code,
                    language = lang,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        codeBlock = { model ->
            MarkdownCodeBlock(content = model.content, node = model.node) { code, lang, _ ->
                AppMarkdownCodeBlock(
                    code = code,
                    language = lang,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )

    Markdown(
        content = content,
        colors = colors,
        typography = typography,
        components = components,
        modifier = modifier
    )
}
