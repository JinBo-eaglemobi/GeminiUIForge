package org.gemini.ui.forge.service.tool

/**
 * 工具多模态执行结果内容项中立契约。
 */
sealed class ToolContent {
    /** 纯文本内容项 */
    data class Text(val text: String) : ToolContent()

    /**
     * 图像内容项（支持原生回传给大模型做视觉审查与比对，或作为 MCP 图像产物）
     * @param base64Data 未经 URI 头包裹的纯 Base64 字符串
     * @param mimeType 图像 MIME 类型（如 image/png、image/webp、image/jpeg）
     */
    data class Image(val base64Data: String, val mimeType: String = "image/png") : ToolContent()
}
