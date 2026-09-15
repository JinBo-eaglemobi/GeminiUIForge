package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.LocalMattingService
import org.gemini.ui.forge.service.mcp.McpToolAnnotations
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.readLocalFileBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 图像本地离线去背/抠图工具
 */
class RemoveBackgroundTool(
    private val storage: LocalFileStorage = LocalFileStorage()
) : McpToolDefinition {

    override val name: String = "remove_background"

    override val description: String =
        "调用本地高效的 Python 离线去背引擎 (rembg/pillow)，精准剔除图像背景并输出带透明 Alpha 通道的 PNG 图像。"

    override val annotations: McpToolAnnotations = McpToolAnnotations(
        readOnlyHint = false,
        idempotentHint = true
    )

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("imagePath", buildJsonObject {
                put("type", "string")
                put("description", "待抠图的本地图片文件绝对路径")
            })
        })
        put("required", buildJsonArray { add("imagePath") })
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val imagePath = arguments["imagePath"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.error("参数 'imagePath' 不能为空")

        onProgress?.invoke(0.2f, "正在读取源图片...")
        val rawBytes = readLocalFileBytes(imagePath)
            ?: return McpToolResult.error("无法读取源图片文件: $imagePath")

        onProgress?.invoke(0.4f, "正在调用本地 Python 抠图引擎 (rembg)...")
        val mattingService = LocalMattingService(storage)
        val noBgBytes = mattingService.removeBackground(rawBytes) { logMsg ->
            onProgress?.invoke(0.7f, logMsg)
        } ?: return McpToolResult.error("本地抠图处理失败，请检查 Python 及 rembg 环境")

        onProgress?.invoke(0.9f, "正在编码透明通道图像...")
        @OptIn(ExperimentalEncodingApi::class)
        val base64 = Base64.encode(noBgBytes)

        // 另存一份到源图片同目录下 (带 _nobg 后缀)
        val outPath = imagePath.substringBeforeLast(".") + "_nobg.png"
        try {
            storage.saveBytesToFile(outPath, noBgBytes)
        } catch (_: Throwable) {}

        onProgress?.invoke(1.0f, "背景移除完成")
        return McpToolResult.image(
            base64Data = base64,
            mimeType = "image/png",
            message = "✅ 背景移除完成，透明图像已生成并另存至: $outPath"
        )
    }
}
