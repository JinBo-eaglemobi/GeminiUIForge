package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.captureActiveScreenShot
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 主窗口/桌面现场取证截图工具（供 AI 调试与视觉状态确认）
 */
class ScreenshotWindowTool : McpToolDefinition {

    override val name: String = "screenshot_window"

    override val description: String =
        "获取应用程序当前主窗口/桌面的实时物理屏幕截图 (ImageContent)。供 AI 客户端现场感知窗口状态、排查界面异常或确认用户交互。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = true,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        onProgress?.invoke(0.3f, "正在捕获屏幕画面...")
        val shotBytes = captureActiveScreenShot()
            ?: return McpToolResult.error("当前平台不支持或未能捕获桌面屏幕画面")

        @OptIn(ExperimentalEncodingApi::class)
        val b64 = Base64.encode(shotBytes)
        onProgress?.invoke(1.0f, "屏幕画面捕获就绪")

        return McpToolResult.image(
            base64Data = b64,
            mimeType = "image/png",
            message = "📸 已成功捕获当前桌面屏幕截图画面"
        )
    }
}
