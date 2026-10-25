package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.chat.ChatToolCallRecord
import org.gemini.ui.forge.service.mcp.McpContent
import org.gemini.ui.forge.service.mcp.McpToolResult
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson

/**
 * 统一 AI 工具调用调度分发器
 */
class AiToolExecutionDispatcher {

    companion object {
        private const val TAG = "AiToolExecutionDispatcher"
    }

    /**
     * 执行指定工具并获取统一结果
     */
    suspend fun dispatchToolCall(
        toolName: String,
        arguments: JsonObject,
        onProgress: ((progress: Float, message: String?) -> Unit)? = null
    ): McpToolResult {
        val tool = UniversalAiToolRegistry.findTool(toolName)
            ?: return McpToolResult.error("未找到对应工具: $toolName")

        return try {
            val effectiveArguments = if (toolName in setOf("generate_block_asset", "generate_all_assets", "generate_image")) {
                val presetModel = org.gemini.ui.forge.manager.ConfigManager().loadKey("DEFAULT_IMAGE_GEN_MODEL")?.ifBlank { null }
                if (!presetModel.isNullOrBlank()) {
                    val currentModel = arguments["modelName"]?.jsonPrimitive?.contentOrNull
                    if (currentModel != presetModel) {
                        AppLogger.i(TAG, "🛡️ 生图模型预设锁定生效: 强制将 modelName 从 [$currentModel] 替换为预设 [$presetModel]")
                        buildJsonObject {
                            arguments.forEach { (k, v) -> put(k, v) }
                            put("modelName", presetModel)
                        }
                    } else arguments
                } else arguments
            } else {
                arguments
            }

            AppLogger.i(TAG, "🚀 开始执行 AI 工具: $toolName, 参数: $effectiveArguments")
            tool.execute(effectiveArguments, onProgress)
        } catch (e: Exception) {
            AppLogger.e(TAG, "工具 $toolName 执行异常", e)
            McpToolResult.error("工具执行发生异常: ${e.message}")
        }
    }

    /**
     * 将工具执行结果提炼为单条结构化 ChatToolCallRecord
     */
    fun buildToolCallRecord(
        id: String,
        toolName: String,
        arguments: JsonObject,
        result: McpToolResult,
        durationMs: Long
    ): ChatToolCallRecord {
        val summary = when {
            result.isError -> {
                result.content.filterIsInstance<McpContent.Text>().firstOrNull()?.text
                    ?: "执行失败"
            }
            result.content.any { it is McpContent.Image } -> {
                val textMsg = result.content.filterIsInstance<McpContent.Text>().firstOrNull()?.text
                textMsg ?: "已生成多模态视觉图像内容"
            }
            else -> {
                val fullText = result.content.filterIsInstance<McpContent.Text>().joinToString("\n") { it.text }
                if (fullText.length > 300) {
                    fullText.take(300) + "..."
                } else {
                    fullText.ifBlank { "执行成功" }
                }
            }
        }

        return ChatToolCallRecord(
            id = id,
            toolName = toolName,
            argumentsJson = looseJson.encodeToString(arguments),
            resultSummary = summary,
            isSuccess = !result.isError,
            durationMs = durationMs
        )
    }

    /**
     * 从原始响应 JSON 或正文文本中提取所有由大模型触发的工具调用
     */
    fun extractFunctionCalls(rawJson: String, responseText: String): List<Pair<String, JsonObject>> {
        val calls = mutableListOf<Pair<String, JsonObject>>()

        // 1. 优先从官方原始响应 JSON 查找
        try {
            if (rawJson.isNotBlank()) {
                val element = looseJson.parseToJsonElement(rawJson)

                // 针对 Google Interactions API 流式事件数组做高保真 step/delta 参数聚合
                if (element is JsonArray) {
                    val stepNamesByIndex = mutableMapOf<Int, String>()
                    val stepArgsByIndex = mutableMapOf<Int, StringBuilder>()

                    for (item in element) {
                        val obj = item as? JsonObject ?: continue
                        val index = obj["index"]?.jsonPrimitive?.intOrNull
                        val step = obj["step"]?.let { it as? JsonObject }
                        if (index != null && step != null) {
                            val typeStr = step["type"]?.jsonPrimitive?.contentOrNull
                            if (typeStr == "function_call" || typeStr == "tool_call") {
                                val name = step["name"]?.jsonPrimitive?.contentOrNull
                                if (!name.isNullOrBlank()) {
                                    stepNamesByIndex[index] = name
                                    val initialArgs = step["arguments"]?.let { it as? JsonObject }
                                    if (initialArgs != null && initialArgs.isNotEmpty()) {
                                        stepArgsByIndex[index] = StringBuilder(initialArgs.toString())
                                    }
                                }
                            }
                        }
                        val delta = obj["delta"]?.let { it as? JsonObject }
                        if (index != null && delta != null) {
                            val deltaArgs = delta["arguments"]?.jsonPrimitive?.contentOrNull
                            if (!deltaArgs.isNullOrBlank()) {
                                stepArgsByIndex.getOrPut(index) { StringBuilder() }.append(deltaArgs)
                            }
                        }
                    }

                    if (stepNamesByIndex.isNotEmpty()) {
                        for ((idx, name) in stepNamesByIndex) {
                            val argsStr = stepArgsByIndex[idx]?.toString() ?: ""
                            val parsedArgs = try {
                                if (argsStr.isNotBlank()) {
                                    looseJson.parseToJsonElement(argsStr).jsonObject
                                } else {
                                    buildJsonObject {}
                                }
                            } catch (_: Exception) {
                                buildJsonObject {}
                            }
                            calls.add(name to parsedArgs)
                        }
                    }
                }

                // 递归兜底遍历其它格式（如常规 generateContent 中的 functionCall）
                if (calls.isEmpty()) {
                    findAllFunctionCallsRecursive(element, calls)
                }
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "从 rawJson 提取函数调用忽略异常: ${e.message}")
        }

        // 2. 若未从 JSON 中解析出工具调用，尝试从正文 Markdown 代码块中捕获
        if (calls.isEmpty() && responseText.isNotBlank()) {
            val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            for (match in codeBlockRegex.findAll(responseText)) {
                val snippet = match.groupValues[1].trim()
                if (snippet.startsWith("{")) {
                    try {
                        val parsed = looseJson.parseToJsonElement(snippet).jsonObject
                        val name = parsed["tool_name"]?.jsonPrimitive?.contentOrNull
                            ?: parsed["name"]?.jsonPrimitive?.contentOrNull
                            ?: parsed["function"]?.jsonPrimitive?.contentOrNull
                        val args = parsed["arguments"]?.jsonObject
                            ?: parsed["parameters"]?.jsonObject
                            ?: parsed["args"]?.jsonObject
                            ?: parsed

                        if (!name.isNullOrBlank() && UniversalAiToolRegistry.findTool(name) != null) {
                            calls.add(name to args)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        return calls
    }

    private fun findAllFunctionCallsRecursive(element: JsonElement, outList: MutableList<Pair<String, JsonObject>>) {
        when (element) {
            is JsonObject -> {
                // 1. Google 官方 Interactions API: step/item 本身即为 function_call 或 tool_call
                val typeStr = element["type"]?.jsonPrimitive?.contentOrNull
                if (typeStr == "function_call" || typeStr == "tool_call") {
                    val name = element["name"]?.jsonPrimitive?.contentOrNull
                    val args = element["arguments"]?.jsonObject
                        ?: element["args"]?.jsonObject
                        ?: element["parameters"]?.jsonObject
                        ?: buildJsonObject {}
                    if (!name.isNullOrBlank()) {
                        outList.add(name to args)
                    }
                }

                // 2. 匹配常规 generateContent 中的 functionCall: { name, args }
                val fc = element["functionCall"]?.jsonObject ?: element["function_call"]?.jsonObject
                if (fc != null) {
                    val name = fc["name"]?.jsonPrimitive?.contentOrNull
                    val args = fc["args"]?.jsonObject
                        ?: fc["arguments"]?.jsonObject
                        ?: buildJsonObject {}
                    if (!name.isNullOrBlank()) {
                        outList.add(name to args)
                    }
                }

                // 3. 匹配常规 tool_call: { name, arguments } 或 step 中的 call
                val toolCall = element["tool_call"]?.jsonObject ?: element["call"]?.jsonObject
                if (toolCall != null) {
                    val name = toolCall["name"]?.jsonPrimitive?.contentOrNull
                    val args = toolCall["arguments"]?.jsonObject
                        ?: toolCall["args"]?.jsonObject
                        ?: buildJsonObject {}
                    if (!name.isNullOrBlank()) {
                        outList.add(name to args)
                    }
                }

                // 递归子节点
                for ((_, v) in element) {
                    findAllFunctionCallsRecursive(v, outList)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findAllFunctionCallsRecursive(item, outList)
                }
            }
            else -> {}
        }
    }
}
