package org.gemini.ui.forge.service.tool.ai

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.gemini.ui.forge.model.chat.ChatToolCallRecord
import org.gemini.ui.forge.service.ai.GeminiFunctionCallRecord
import org.gemini.ui.forge.service.tool.ToolContent
import org.gemini.ui.forge.service.tool.ToolHub
import org.gemini.ui.forge.service.tool.ToolResult
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.looseJson

/**
 * 统一 AI 工具调用调度分发器
 *
 * 负责从大模型响应中流式解析出结构化函数调用，通过全局 [ToolHub] 动态路由执行对应的 [ToolDefinition]，
 * 并将执行产物 [ToolResult] 打包为持久化的 [ChatToolCallRecord]。
 *
 * 【架构红线】
 * 本分发器属于 AI 业务域，严禁 import [org.gemini.ui.forge.service.tool.mcp.*]，执行调度统一收拢于中立工具中枢。
 */
class AiToolExecutionDispatcher {

    companion object {
        private const val TAG = "AiToolExecutionDispatcher"

        /**
         * 从原始响应 JSON 或正文文本中提取所有由大模型触发的结构化函数调用记录（带 callId / step.id 支持）
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

        private fun extractStreamingFunctionCalls(events: JsonArray): List<GeminiFunctionCallRecord> {
            val stepBuilders = mutableMapOf<Int, StepCallAssembler>()

            for (eventItem in events) {
                if (eventItem !is JsonObject) continue

                val index = eventItem["index"]?.jsonPrimitive?.intOrNull

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

        private fun normalizeJsonString(rawStr: String): String {
            val trimmed = rawStr.trim()
            if (trimmed.isEmpty()) return "{}"
            try {
                val parsed = looseJson.parseToJsonElement(trimmed)
                if (parsed is JsonObject) return parsed.toString()
            } catch (_: Exception) {}

            try {
                if (trimmed.contains("\\\"")) {
                    val unescaped = trimmed.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
                    val parsed = looseJson.parseToJsonElement(unescaped)
                    if (parsed is JsonObject) return parsed.toString()
                }
            } catch (_: Exception) {}

            return if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed else "{}"
        }

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

                    val typeStr = element["type"]?.jsonPrimitive?.contentOrNull
                    if (typeStr == "function_call" || typeStr == "tool_call") {
                        val name = element["name"]?.jsonPrimitive?.contentOrNull
                        val argsRaw = element["arguments"] ?: element["args"] ?: element["parameters"]
                        val argsJsonStr = serializeArgsElement(argsRaw)
                        if (!name.isNullOrBlank()) {
                            outList.add(GeminiFunctionCallRecord(name, argsJsonStr, callId))
                        }
                    }

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
    ): ToolResult {
        val tool = ToolHub.findTool(toolName)
            ?: return ToolResult.error("未找到对应工具: $toolName")

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
            withTimeout(tool.timeoutMs) {
                tool.execute(effectiveArguments, onProgress)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "工具 $toolName 执行异常", e)
            ToolResult.error("工具执行发生异常: ${e.message}")
        }
    }

    /**
     * 将工具执行结果提炼为单条结构化 [ChatToolCallRecord]
     */
    fun buildToolCallRecord(
        id: String,
        toolName: String,
        arguments: JsonObject,
        result: ToolResult,
        durationMs: Long,
        callId: String? = null
    ): ChatToolCallRecord {
        val rawText = result.content.filterIsInstance<ToolContent.Text>().joinToString("\n") { it.text }
        val summary = when {
            result.isError -> {
                rawText.ifBlank { "执行失败" }
            }
            result.content.any { it is ToolContent.Image } -> {
                val textMsg = result.content.filterIsInstance<ToolContent.Text>().firstOrNull()?.text
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

        val imageContent = result.content.filterIsInstance<ToolContent.Image>().firstOrNull()
        val imageUri = imageContent?.let { img ->
            "data:${img.mimeType};base64,${img.base64Data}"
        }

        return ChatToolCallRecord(
            id = id,
            toolName = toolName,
            argumentsJson = looseJson.encodeToString(arguments),
            resultSummary = summary,
            isSuccess = !result.isError,
            durationMs = durationMs,
            rawOutput = rawText.ifBlank { if (result.isError) "{\"error\":\"执行失败\"}" else "{}" },
            callId = callId,
            imageUri = imageUri
        )
    }

    fun extractFunctionCallRecords(rawJson: String, responseText: String): List<GeminiFunctionCallRecord> =
        Companion.extractFunctionCallRecords(rawJson, responseText)

    fun extractFunctionCalls(rawJson: String, responseText: String): List<Pair<String, JsonObject>> =
        Companion.extractFunctionCalls(rawJson, responseText)
}
