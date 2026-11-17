package org.gemini.ui.forge.model.code

/**
 * 代码块内可折叠的作用域区间数据模型（单一实体单一物理文件）。
 *
 * @property startLine 作用域起始行索引（0-based）
 * @property endLine 作用域结束行索引（0-based，必须大于 startLine）
 * @property placeholder 折叠后行尾显示的语法省略符号（如 "{...}"、"[...]"、"</tag>"）
 */
data class CodeFoldScope(
    val startLine: Int,
    val endLine: Int,
    val placeholder: String = "{...}"
) {
    /** 被折叠隐藏的内部行数 */
    val hiddenLineCount: Int
        get() = (endLine - startLine).coerceAtLeast(0)
}
