package org.gemini.ui.forge.service.tool

/**
 * 工具结构化执行结果中立契约。
 *
 * 统一承载 AI Function Calling 与 MCP tools/call 的多模态响应。
 */
data class ToolResult(
    val content: List<ToolContent>,
    val isError: Boolean = false
) {
    companion object {
        fun text(text: String, isError: Boolean = false): ToolResult =
            ToolResult(listOf(ToolContent.Text(text)), isError)

        fun success(text: String): ToolResult =
            text(text, isError = false)

        fun image(base64Data: String, mimeType: String = "image/png", message: String? = null): ToolResult =
            ToolResult(
                buildList {
                    if (!message.isNullOrBlank()) add(ToolContent.Text(message))
                    add(ToolContent.Image(base64Data, mimeType))
                }
            )

        fun error(errorMessage: String): ToolResult =
            ToolResult(listOf(ToolContent.Text(errorMessage)), isError = true)
    }
}
