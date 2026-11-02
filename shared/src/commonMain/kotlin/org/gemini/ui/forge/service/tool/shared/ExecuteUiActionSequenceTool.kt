package org.gemini.ui.forge.service.tool.shared

import org.gemini.ui.forge.service.tool.ToolAnnotations
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolDefinition
import org.gemini.ui.forge.service.tool.ToolResult

import kotlinx.serialization.json.*
import org.gemini.ui.forge.service.mcp.McpUiAction
import org.gemini.ui.forge.service.mcp.McpUiActionPipeline

/**
 * MCP 工具：在前端模拟真实人工操作指令序列
 * 真实驱动 UI 线程执行选中、隔离模块隐藏其他、切换参考图模式与透明度、视口聚焦以及触发校准
 */
class ExecuteUiActionSequenceTool : ToolDefinition {
    override val name: String = "execute_ui_action_sequence"
    override val description: String = "在当前前端 Compose 界面上按序模拟人工真实操作指令流（支持选中模块、隔离显示单个模块并隐藏其余、开启参考底图半透明叠加、调整透明度、画布视口居中聚焦、触发微观吸附校对算法等），每一步均在真实 UI 线程上分发并捕获真实的执行效果与报错。"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("projectName") {
                put("type", "string")
                put("description", "可选：目标工程模板名称，缺省时作用于当前已打开的活跃工程")
            }
            putJsonObject("captureScreenshot") {
                put("type", "boolean")
                put("description", "可选：执行完全部指令后是否截取当前窗口实机屏幕，默认 true")
            }
            putJsonObject("screenshotTargetBlockId") {
                put("type", "string")
                put("description", "可选：若指定，则仅截取该图元及其周围的局部区域画面，缺省为整窗截图")
            }
            putJsonObject("actions") {
                put("type", "array")
                put("description", "要执行的有序 UI 操作指令列表。支持类型：SELECT_BLOCK (选中图元), ISOLATE_BLOCK (隐藏其他图元仅保留本图元), RESTORE_VISIBILITY (恢复显隐快照), SET_REFERENCE_MODE (SPLIT/OVERLAY/HIDDEN), SET_REFERENCE_OPACITY (0.1~1.0), FOCUS_BLOCK_ON_CANVAS (重置并居中视口), TRIGGER_CALIBRATE (触发校对), TOGGLE_OUTLINES (切换外边框), WAIT_MS (等待微秒)")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("type") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("SELECT_BLOCK")
                                add("ISOLATE_BLOCK")
                                add("RESTORE_VISIBILITY")
                                add("SET_REFERENCE_MODE")
                                add("SET_REFERENCE_OPACITY")
                                add("FOCUS_BLOCK_ON_CANVAS")
                                add("TRIGGER_CALIBRATE")
                                add("TOGGLE_OUTLINES")
                                add("WAIT_MS")
                            }
                            put("description", "指令类型：SELECT_BLOCK (选中图元), ISOLATE_BLOCK (隐藏其他仅保留该图元), RESTORE_VISIBILITY (恢复全显), SET_REFERENCE_MODE (参考图模式), SET_REFERENCE_OPACITY (参考图透明度), FOCUS_BLOCK_ON_CANVAS (画布视口聚焦), TRIGGER_CALIBRATE (微观吸附校对), TOGGLE_OUTLINES (线框显隐), WAIT_MS (等待延时)")
                        }
                        putJsonObject("blockId") {
                            put("type", "string")
                            put("description", "目标图元 ID（在 SELECT_BLOCK, ISOLATE_BLOCK, FOCUS_BLOCK_ON_CANVAS, TRIGGER_CALIBRATE 等指令下必填）")
                        }
                        putJsonObject("mode") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("SPLIT")
                                add("OVERLAY")
                                add("HIDDEN")
                            }
                            put("description", "在 SET_REFERENCE_MODE 下生效：'SPLIT' (分栏对比), 'OVERLAY' (半透明重叠), 'HIDDEN' (隐藏)")
                        }
                        putJsonObject("opacity") {
                            put("type", "number")
                            put("description", "在 SET_REFERENCE_OPACITY 下生效：参考图半透明叠加不透明度 (0.1 ~ 1.0)")
                        }
                        putJsonObject("delayMs") {
                            put("type", "integer")
                            put("description", "在 WAIT_MS 下生效：等待毫秒数")
                        }
                        putJsonObject("alsoCropAndBind") {
                            put("type", "boolean")
                            put("description", "在 TRIGGER_CALIBRATE 下生效：校准后是否自动从新坐标物理裁切参考图切片")
                        }
                        putJsonObject("engineMode") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add("UI")
                                add("HEADLESS")
                            }
                            put("description", "可选：执行模式，'UI' (前台拟人化交互) 或 'HEADLESS' (离线无感处理)")
                        }
                    }
                    putJsonArray("required") {
                        add("type")
                    }
                }
            }
        }
        putJsonArray("required") {
            add("actions")
        }
    }

    override suspend fun execute(
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)?
    ): ToolResult {
        val projectName = arguments["projectName"]?.jsonPrimitive?.contentOrNull
        val captureScreenshot = arguments["captureScreenshot"]?.jsonPrimitive?.booleanOrNull ?: true
        val screenshotTargetBlockId = arguments["screenshotTargetBlockId"]?.jsonPrimitive?.contentOrNull
        val actionsArray = arguments["actions"]?.jsonArray ?: return ToolResult.error("缺少必填参数 'actions'")

        val actionList = mutableListOf<McpUiAction>()
        for (item in actionsArray) {
            val obj = item.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: continue
            val blockId = obj["blockId"]?.jsonPrimitive?.contentOrNull
            val mode = obj["mode"]?.jsonPrimitive?.contentOrNull
            val opacity = obj["opacity"]?.jsonPrimitive?.floatOrNull
            val delayMs = obj["delayMs"]?.jsonPrimitive?.longOrNull
            val alsoCrop = obj["alsoCropAndBind"]?.jsonPrimitive?.booleanOrNull
            val engineMode = obj["engineMode"]?.jsonPrimitive?.contentOrNull

            actionList.add(
                McpUiAction(
                    type = type,
                    blockId = blockId,
                    mode = mode,
                    opacity = opacity,
                    delayMs = delayMs,
                    alsoCropAndBind = alsoCrop,
                    engineMode = engineMode
                )
            )
        }

        onProgress?.invoke(0.2f, "正在向活跃 UI 工作区分发执行操作序列...")
        val result = McpUiActionPipeline.executeSequence(
            projectName = projectName,
            actions = actionList,
            captureScreenshot = captureScreenshot,
            screenshotTargetBlockId = screenshotTargetBlockId
        )

        val responseJson = buildJsonObject {
            put("allSuccess", result.allSuccess)
            put("totalSteps", result.totalSteps)
            put("successfulSteps", result.successfulSteps)
            if (result.errorMessage != null) {
                put("error", result.errorMessage)
            }
            putJsonArray("reports") {
                for (r in result.reports) {
                    addJsonObject {
                        put("step", r.stepIndex)
                        put("action", r.actionType)
                        put("success", r.isSuccess)
                        put("durationMs", r.durationMs)
                        put("message", r.message)
                    }
                }
            }
            if (result.screenshotBase64 != null) {
                put("screenshotLength", result.screenshotBase64.length)
            }
        }

        return if (result.screenshotBase64 != null) {
            ToolResult.image(
                base64Data = result.screenshotBase64,
                mimeType = "image/png",
                message = responseJson.toString()
            )
        } else {
            ToolResult.text(responseJson.toString())
        }
    }
}

