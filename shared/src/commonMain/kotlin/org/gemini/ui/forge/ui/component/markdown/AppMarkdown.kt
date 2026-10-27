package org.gemini.ui.forge.ui.component.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    val sanitizedContent = remember(content) {
        sanitizeMarkdownWindowsPaths(content)
    }

    Markdown(
        content = sanitizedContent,
        colors = colors,
        typography = typography,
        components = components,
        modifier = modifier
    )
}

/**
 * 保护 Markdown 中的 Windows 物理绝对路径（如 C:\Users\...），
 * 避免因 CommonMark 规范（以及 multiplatform-markdown-renderer 库的 replaceEntities 机制）
 * 将反斜杠（特别是后跟标点如 \. 或 \_）当做转义符号剥离。
 *
 * 核心原理：
 * multiplatform-markdown-renderer 的底层 Annotator 对普通文本及行内 CodeSpan 均会调用
 * EntityConverter.replaceEntities，无条件将 "\ + ASCII标点" 剥离为标点本身。
 * 因此对所有非多行代码块（```）内的 Windows 绝对路径，将其中的反斜杠安全转义为双反斜杠 "\\"，
 * 经解析器 unescape 后精准还原为单反斜杠 "\"。对于正文中的裸露路径同时补齐行内代码反引号包裹。
 */
internal fun sanitizeMarkdownWindowsPaths(raw: String): String {
    if (!raw.contains(":\\")) return raw

    // 先按三反引号代码块（```）分段，代码块内部不予触碰
    val parts = raw.split("```")
    return parts.mapIndexed { index, part ->
        if (index % 2 == 1) {
            // 代码块内部，100% 原样保留
            part
        } else {
            // 非代码块区域（包含普通正文与行内代码 `...`）
            val inlineParts = part.split("`")
            inlineParts.mapIndexed { inlineIndex, inlineText ->
                if (inlineIndex % 2 == 1) {
                    // 行内代码内部：如果包含 Windows 路径，必须将反斜杠替换为双反斜杠防止被 replaceEntities 吞掉
                    if (inlineText.contains(":\\")) {
                        escapeWindowsPathBackslashes(inlineText)
                    } else {
                        inlineText
                    }
                } else {
                    // 普通正文：匹配裸露的 Windows 路径（如 C:\... 或 D:\...）
                    inlineText.replace(Regex("""(?<![A-Za-z0-9_])([A-Za-z]:\\[^\s`\r\n，。！？；、“”‘’（）《》]+)""")) { match ->
                        var path = match.value
                        var suffix = ""
                        while (path.endsWith(",") || path.endsWith(";") || path.endsWith(":") || path.endsWith(")")) {
                            suffix = path.takeLast(1) + suffix
                            path = path.dropLast(1)
                        }
                        val escapedPath = escapeWindowsPathBackslashes(path)
                        "`$escapedPath`$suffix"
                    }
                }
            }.joinToString("`")
        }
    }.joinToString("```")
}

private fun escapeWindowsPathBackslashes(path: String): String {
    // 匹配 Windows 路径中的单个或连续反斜杠，安全统一规范为双反斜杠 "\\"，
    // 供 CommonMark 引擎解码还原为单反斜杠 "\"，彻底防止 \. 或 \_ 等被当做标点转义吞噬。
    return path.replace(Regex("""\\+""")) { "\\\\" }
}
