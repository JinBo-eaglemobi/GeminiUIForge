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

        /**
         * 从原始响应 JSON 或正文文本中提取所有由大模型触发的结构化函数调用记录（带 callId / step.id 支持）
         *
         * 严格对齐 Google Gemini Interactions 官方流式 (SSE) 与非流式协议规范：
         * 1. 流式事件流 (Streaming Event Array): 自动将同 step 索引 (index) 或 id 的 step.start 初始骨架
         *    与随后的一个或多个 step.delta (type: arguments_delta) 增量分块片段进行内存累加组装；
         * 2. 多工具并发调用 (Parallel Function Calling): 按 step index / callId 隔离组装，彻底根除参数截断与串包；
         * 3. 双模容错反序列化 (Dual-Mode Deserialization): 兼容 JsonObject、JSON 字符串、内嵌转义文本三级管道。
         */
        fun extractFunctionCallRecords(rawJson: String, responseText: String): List<GeminiFunctionCallRecord> {
            val calls = mutableListOf<GeminiFunctionCallRecord>()

            if (rawJson.isNotBlank()) {
                try {
                    val element = looseJson.parseToJsonElement(rawJson)

                    // 1. Interactions 流式分块事件流集合 (JsonArray 结构)
                    if (element is JsonArray) {
                        val streamCalls = extractStreamingFunctionCalls(element)
                        if (streamCalls.isNotEmpty()) {
                            calls.addAll(streamCalls)
                        }
                    }

                    // 2. 优先精准提取 Google 官方 Interactions API: interactions.steps 结构 (JsonObject 根实体)
                    if (calls.isEmpty() && element is JsonObject) {
                        val steps = element["steps"]?.jsonArray
                            ?: element["interaction"]?.jsonObject?.get("steps")?.jsonArray
                        if (steps != null) {
                            for (stepItem in steps) {
                                if (stepItem is JsonObject) {
                                    val stepId = stepItem["id"]?.jsonPrimitive?.contentOrNull
                                        ?: stepItem["call_id"]?.jsonPrimitive?.contentOrNull
                                    val callObj = stepItem["call"]?.jsonObject
                                        ?: stepItem["tool_call"]?.jsonObject
                                        ?: stepItem["function_call"]?.jsonObject
                                        ?: (if (stepItem["type"]?.jsonPrimitive?.contentOrNull in setOf("function_call", "tool_call")) stepItem else null)
                                    if (callObj != null) {
                                        val name = callObj["name"]?.jsonPrimitive?.contentOrNull
                                        val argsRaw = callObj["arguments"] ?: callObj["args"] ?: callObj["parameters"]
                                        val argsJsonStr = serializeArgsElement(argsRaw)
                                        if (!name.isNullOrBlank()) {
                                            calls.add(GeminiFunctionCallRecord(name, argsJsonStr, stepId))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. 递归提取其余标准/兼容结构 (generateContent candidates.functionCall 等)
                    if (calls.isEmpty()) {
                        findAllFunctionCallRecordsRecursive(element, calls)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "解析原始流 JSON 工具调用异常: ${e.message}")
                }
            }

            // 若原始 JSON 未匹配到工具调用，则尝试从响应正文 Markdown 代码块或伪 JSON 中提取
            if (calls.isEmpty() && responseText.isNotBlank()) {
                val toolPatterns = listOf(
                    """```json\s*(\{[\s\S]*?"(?:tool|name|function)"[\s\S]*?\})\s*```""".toRegex(RegexOption.IGNORE_CASE),
                    """(\{\s*"(?:tool|name|function)"\s*:\s*"[^"]+"\s*,\s*"(?:parameters|arguments|args)"\s*:\s*\{[\s\S]*?\}\s*\})""".toRegex(RegexOption.IGNORE_CASE)
                )

                for (pattern in toolPatterns) {
                    val matches = pattern.findAll(responseText)
                    for (match in matches) {
                        try {
                            val snippet = match.groupValues[1]
                            val parsed = looseJson.parseToJsonElement(snippet).jsonObject
                            val name = parsed["name"]?.jsonPrimitive?.contentOrNull
                                ?: parsed["tool"]?.jsonPrimitive?.contentOrNull
                                ?: parsed["function"]?.jsonPrimitive?.contentOrNull
                            val args = parsed["parameters"]
                                ?: parsed["arguments"]
                                ?: parsed["args"]
                            val argsJsonStr = serializeArgsElement(args)
                            val callId = parsed["id"]?.jsonPrimitive?.contentOrNull
                                ?: parsed["call_id"]?.jsonPrimitive?.contentOrNull

                            if (!name.isNullOrBlank() && calls.none { it.name == name && it.argsJson == argsJsonStr }) {
                                calls.add(GeminiFunctionCallRecord(name, argsJsonStr, callId))
                            }
                        } catch (_: Exception) {}
                    }
                    if (calls.isNotEmpty()) break
                }

                // 兜底正则提取 name 与 arguments
                if (calls.isEmpty()) {
                    val fallbackRegex = """"(?:name|tool|function)"\s*:\s*"([^"]+)"""".toRegex()
                    val match = fallbackRegex.find(responseText)
                    if (match != null) {
                        val name = match.groupValues[1]
                        calls.add(GeminiFunctionCallRecord(name, "{}", null))
                    }
                }
            }

            return calls
        }

        /**
         * 从 Interactions API 流式事件流数组中精确装配函数调用与增量参数 (处理 step.start + 多分片 step.delta arguments_delta)
         */
        private fun extractStreamingFunctionCalls(events: JsonArray): List<GeminiFunctionCallRecord> {
            val stepBuilders = mutableMapOf<Int, StepCallAssembler>()

            for (eventItem in events) {
                if (eventItem !is JsonObject) continue

                val index = eventItem["index"]?.jsonPrimitive?.intOrNull
                val eventType = eventItem["event_type"]?.jsonPrimitive?.contentOrNull

                // 1. 匹配 step.start 或 step 对象
                val stepObj = eventItem["step"]?.jsonObject
                if (stepObj != null) {
                    val type = stepObj["type"]?.jsonPrimitive?.contentOrNull
                    if (type == "function_call" || type == "tool_call") {
                        val effectiveIndex = index ?: (stepBuilders.size + 1)
                        val assembler = stepBuilders.getOrPut(effectiveIndex) { StepCallAssembler(effectiveIndex) }
                        assembler.id = stepObj["id"]?.jsonPrimitive?.contentOrNull
                            ?: stepObj["call_id"]?.jsonPrimitive?.contentOrNull
                            ?: assembler.id
                        assembler.name = stepObj["name"]?.jsonPrimitive?.contentOrNull ?: assembler.name
                        val initialArgs = stepObj["arguments"] ?: stepObj["args"] ?: stepObj["parameters"]
                        if (initialArgs != null) {
                            assembler.initialArgsElement = initialArgs
                        }
                    }
                }

                // 2. 匹配 step.delta 中的增量 arguments_delta
                val deltaObj = eventItem["delta"]?.jsonObject
                if (deltaObj != null) {
                    val deltaType = deltaObj["type"]?.jsonPrimitive?.contentOrNull
                    val argsChunk = deltaObj["arguments"]?.jsonPrimitive?.contentOrNull
                    if (argsChunk != null && (deltaType == null || deltaType == "arguments_delta")) {
                        val effectiveIndex = index ?: stepBuilders.keys.lastOrNull() ?: 1
                        val assembler = stepBuilders.getOrPut(effectiveIndex) { StepCallAssembler(effectiveIndex) }
                        assembler.argumentsDeltaBuffer.append(argsChunk)
                    }
                }
            }

            if (stepBuilders.isEmpty()) return emptyList()

            val results = mutableListOf<GeminiFunctionCallRecord>()
            // 按 index 排序确保多工具调用的有序性
            stepBuilders.entries.sortedBy { it.key }.forEach { (_, assembler) ->
                val toolName = assembler.name
                if (!toolName.isNullOrBlank()) {
                    val deltaStr = assembler.argumentsDeltaBuffer.toString().trim()
                    val finalArgsJson = if (deltaStr.isNotEmpty()) {
                        normalizeJsonString(deltaStr)
                    } else {
                        serializeArgsElement(assembler.initialArgsElement)
                    }
                    results.add(GeminiFunctionCallRecord(toolName, finalArgsJson, assembler.id))
                }
            }
            return results
        }

        private class StepCallAssembler(
            val index: Int,
            var id: String? = null,
            var name: String? = null,
            val argumentsDeltaBuffer: StringBuilder = StringBuilder(),
            var initialArgsElement: JsonElement? = null
        )

        /**
         * 将 JsonElement 宽容序列化为规整的 JSON 字符串 (如 {"query": "asset"})
         */
        private fun serializeArgsElement(element: JsonElement?): String {
            if (element == null) return "{}"
            return when (element) {
                is JsonObject -> element.toString()
                is JsonPrimitive -> {
                    if (element.isString) {
                        normalizeJsonString(element.content)
                    } else {
                        element.content
                    }
                }
                else -> "{}"
            }
        }

        /**
         * 规整化 JSON 文本，自动处理转义、嵌套反序列化与边界情况
         */
        private fun normalizeJsonString(rawStr: String): String {
            val trimmed = rawStr.trim()
            if (trimmed.isEmpty()) return "{}"
            // 通道 1: 尝试直接解析为 JsonObject
            try {
                val parsed = looseJson.parseToJsonElement(trimmed)
                if (parsed is JsonObject) return parsed.toString()
            } catch (_: Exception) {}

            // 通道 2: 尝试处理包含反斜杠转义的情况（如 \"query\": \"asset\"）
            try {
                if (trimmed.contains("\\\"")) {
                    val unescaped = trimmed.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
                    val parsed = looseJson.parseToJsonElement(unescaped)
                    if (parsed is JsonObject) return parsed.toString()
                }
            } catch (_: Exception) {}

            return if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed else "{}"
        }

        /**
         * 从原始响应 JSON 或正文文本中提取所有由大模型触发的工具调用
         */
        fun extractFunctionCalls(rawJson: String, responseText: String): List<Pair<String, JsonObject>> {
            return extractFunctionCallRecords(rawJson, responseText).map { rec ->
                val parsedArgs = try {
                    if (rec.argsJson.isNotBlank()) looseJson.parseToJsonElement(rec.argsJson).jsonObject else buildJsonObject {}
                } catch (_: Exception) {
                    buildJsonObject {}
                }
                rec.name to parsedArgs
            }
        }

        private fun findAllFunctionCallRecordsRecursive(element: JsonElement, outList: MutableList<GeminiFunctionCallRecord>) {
            when (element) {
                is JsonObject -> {
                    val callId = element["id"]?.jsonPrimitive?.contentOrNull
                        ?: element["call_id"]?.jsonPrimitive?.contentOrNull

                    // 1. Google 官方 Interactions API: step/item 本身即为 function_call 或 tool_call
                    val typeStr = element["type"]?.jsonPrimitive?.contentOrNull
                    if (typeStr == "function_call" || typeStr == "tool_call") {
                        val name = element["name"]?.jsonPrimitive?.contentOrNull
                        val argsRaw = element["arguments"] ?: element["args"] ?: element["parameters"]
                        val argsJsonStr = serializeArgsElement(argsRaw)
                        if (!name.isNullOrBlank()) {
                            outList.add(GeminiFunctionCallRecord(name, argsJsonStr, callId))
                        }
                    }

                    // 2. 匹配常规 generateContent 中的 functionCall: { name, args }
                    val fc = element["functionCall"]?.jsonObject ?: element["function_call"]?.jsonObject
                    if (fc != null) {
                        val name = fc["name"]?.jsonPrimitive?.contentOrNull
                        val argsRaw = fc["args"] ?: fc["arguments"] ?: fc["parameters"]
                        val argsJsonStr = serializeArgsElement(argsRaw)
                        val fcCallId = fc["id"]?.jsonPrimitive?.contentOrNull ?: fc["call_id"]?.jsonPrimitive?.contentOrNull ?: callId
                        if (!name.isNullOrBlank()) {
                            outList.add(GeminiFunctionCallRecord(name, argsJsonStr, fcCallId))
                        }
                    }

                    // 3. 匹配常规 tool_call: { name, arguments } 或 step 中的 call
                    val toolCall = element["tool_call"]?.jsonObject ?: element["call"]?.jsonObject
                    if (toolCall != null) {
                        val name = toolCall["name"]?.jsonPrimitive?.contentOrNull
                        val argsRaw = toolCall["arguments"] ?: toolCall["args"] ?: toolCall["parameters"]
                        val argsJsonStr = serializeArgsElement(argsRaw)
                        val tcCallId = toolCall["id"]?.jsonPrimitive?.contentOrNull ?: toolCall["call_id"]?.jsonPrimitive?.contentOrNull ?: callId
                        if (!name.isNullOrBlank()) {
                            outList.add(GeminiFunctionCallRecord(name, argsJsonStr, tcCallId))
                        }
                    }

                    // 递归子节点
                    for ((_, v) in element) {
                        findAllFunctionCallRecordsRecursive(v, outList)
                    }
                }
                is JsonArray -> {
                    for (item in element) {
                        findAllFunctionCallRecordsRecursive(item, outList)
                    }
                }
                else -> {}
            }
        }
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
        durationMs: Long,
        callId: String? = null
    ): ChatToolCallRecord {
        val rawText = result.content.filterIsInstance<McpContent.Text>().joinToString("\n") { it.text }
        val summary = when {
            result.isError -> {
                rawText.ifBlank { "执行失败" }
            }
            result.content.any { it is McpContent.Image } -> {
                val textMsg = result.content.filterIsInstance<McpContent.Text>().firstOrNull()?.text
                textMsg ?: "已生成多模态视觉图像内容"
            }
            else -> {
                if (rawText.length > 300) {
                    rawText.take(300) + "..."
                } else {
                    rawText.ifBlank { "执行成功" }
                }
            }
        }

        return ChatToolCallRecord(
            id = id,
            toolName = toolName,
            argumentsJson = looseJson.encodeToString(arguments),
            resultSummary = summary,
            isSuccess = !result.isError,
            durationMs = durationMs,
            rawOutput = rawText.ifBlank { if (result.isError) "{\"error\":\"执行失败\"}" else "{}" },
            callId = callId
        )
    }

    /**
     * 实例代理转发至 companion object
     */
    fun extractFunctionCallRecords(rawJson: String, responseText: String): List<GeminiFunctionCallRecord> =
        Companion.extractFunctionCallRecords(rawJson, responseText)

    fun extractFunctionCalls(rawJson: String, responseText: String): List<Pair<String, JsonObject>> =
        Companion.extractFunctionCalls(rawJson, responseText)
}
