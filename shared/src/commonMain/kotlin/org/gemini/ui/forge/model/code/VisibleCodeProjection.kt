package org.gemini.ui.forge.model.code

import androidx.compose.ui.text.AnnotatedString

/**
 * 折叠过滤后的代码块可见视图投影模型（单一实体单一物理文件）。
 *
 * 将所有可见行预先合并为单一行号字符串与单一代码 [AnnotatedString]，
 * 使 Compose 仅需渲染 2 个 Text 节点即可呈现任意长度的代码块，彻底消除多行节点重组开销。
 *
 * @property visibleLineCount 当前可见总行数
 * @property lineNumbersText 合并后的行号列文本（每行以 \n 分隔）
 * @property codeAnnotatedText 合并后的代码正文（已折叠行尾内联附带高亮省略徽章 Span）
 * @property visibleScopes 当前可见行中携带折叠作用域的映射（Key = 可见行索引 0-based，Value = 作用域）
 */
data class VisibleCodeProjection(
    val visibleLineCount: Int,
    val lineNumbersText: String,
    val codeAnnotatedText: AnnotatedString,
    val visibleScopes: Map<Int, CodeFoldScope>
)
