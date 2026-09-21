package org.gemini.ui.forge.service.mcp.tools

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpToolDefinition
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.service.mcp.McpUiAction
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline

/**
 * MCP 工具：针对指定模块进行单图元隔离比对（隐藏其他图元 -> 居中放大 -> 开启参考图半透明叠加 -> 捕获画面）
 */
class InspectBlockWithReferenceTool : McpToolDefinition {
    override val name: String = "inspect_block_with_reference"
    override val description: String = "一键隔离并详查单个图元与参考图的绝对贴合度：自动隐藏其它所有干扰模块、居中视口、开启参考底图半透明叠加对比（支持指定透明度），并由真实 UI 渲染生成实机截图回传。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "可选：目标模板名称，缺省时作用于当前已打开的活跃工程")
            }
            putJsonObject("blockId") {
                put("type", "string")
                put("description", "要聚焦详查的目标模块 ID")
            }
            putJsonObject("overlayOpacity") {
                put("type", "number")
                put("description", "参考图半透明叠加透明度 (0.1 ~ 1.0)，默认 0.5")
            }
            putJsonObject("calibrate") {
                put("type", "boolean")
                put("description", "是否顺带在真实 UI 上触发一次微观吸附校对，默认 false")
            }
        }
        putJsonArray("required") {
            add("blockId")
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): McpToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
        val blockId = arguments["blockId"]?.jsonPrimitive?.content ?: return McpToolResult.error("缺少必填参数 'blockId'")
        val opacity = arguments["overlayOpacity"]?.jsonPrimitive?.floatOrNull ?: 0.5f
        val calibrate = arguments["calibrate"]?.jsonPrimitive?.booleanOrNull ?: false

        val actions = mutableListOf<McpUiAction>()

        // 1. 选中该模块
        actions.add(McpUiAction(type = "SELECT_BLOCK", blockId = blockId))
        // 2. 隔离该模块（隐藏其他模块）
        actions.add(McpUiAction(type = "ISOLATE_BLOCK", blockId = blockId))
        // 3. 居中视口
        actions.add(McpUiAction(type = "FOCUS_BLOCK_ON_CANVAS", blockId = blockId))
        // 4. 开启参考底图半透明叠加模式
        actions.add(McpUiAction(type = "SET_REFERENCE_MODE", mode = "OVERLAY"))
        // 5. 设置参考底图叠加透明度
        actions.add(McpUiAction(type = "SET_REFERENCE_OPACITY", opacity = opacity))

        // 可选：触发微观吸附校准
        if (calibrate) {
            actions.add(McpUiAction(type = "TRIGGER_CALIBRATE", blockId = blockId, engineMode = "BASELINE_SNAPPER"))
        }

        // 等待 200ms 保证渲染落定
        actions.add(McpUiAction(type = "WAIT_MS", delayMs = 200L))

        onProgress?.invoke(0.2f, "正在执行模块隔离与参考图对比管线: $blockId")
        val result = McpUiActionPipeline.executeSequence(
            projectName = projectName,
            actions = actions,
            captureScreenshot = true
        )

        val responseJson = buildJsonObject {
            put("allSuccess", result.allSuccess)
            put("inspectedBlockId", blockId)
            put("opacity", opacity)
            put("calibrated", calibrate)
            if (result.errorMessage != null) {
                put("error", result.errorMessage)
            }
            putJsonArray("reports") {
                for (r in result.reports) {
                    addJsonObject {
                        put("step", r.stepIndex)
                        put("action", r.actionType)
                        put("success", r.isSuccess)
                        put("message", r.message)
                    }
                }
            }
        }

        return if (result.screenshotBase64 != null) {
            McpToolResult.image(
                base64Data = result.screenshotBase64,
                mimeType = "image/png",
                message = responseJson.toString()
            )
        } else {
            McpToolResult.text(responseJson.toString())
        }
    }
}
