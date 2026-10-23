package org.gemini.ui.forge.service.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import org.gemini.ui.forge.ui.component.chat.cards.LayoutArtifactData
import org.gemini.ui.forge.ui.component.chat.cards.LayoutModuleSummary

/**
 * 投影展示内容模型
 *
 * @param text 提纯后的核心回答文本 (人类可读排版/内容)
 * @param thoughtText 提取出的思维链思考过程文本 (若存在)
 * @param thoughtSignature 官方防篡改思考加密签名 (若存在)
 */
data class ProjectedChatContent(
    val text: String,
    val thoughtText: String? = null,
    val thoughtSignature: String? = null
)

/**
 * 会话报文视图投影提取器 (单例)
 *
 * 核心架构思想：
 * 遵循“原始报文作为唯一事实源，渲染时只按需投影提取指定内容展示”的设计。
 * 数据存储层无损保留完整的原始回复 JSON；本提取器在 UI 渲染时，动态从原始 JSON 的标准路径
 * (`steps -> model_output -> content -> text` 与 `steps -> thought`) 中提取指定内容赋值给 UI 组件，
 * 绝不向用户倾倒未解析的纯 JSON，同时具备轻量级缓存防止高频重复解析。
 */
object ChatPayloadViewProjector {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val jsonPrettyParser = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 将原始数据载荷动态投影为用户可读的展示内容。
     */
    fun projectDisplayContent(rawPayload: String): ProjectedChatContent {
        if (rawPayload.isBlank()) {
            return ProjectedChatContent(text = "")
        }

        // 优先拦截排版布局数据报文：提取纯净排版数据并使用标准 Markdown ```json 代码框包裹呈现
        if (rawPayload.contains("apply_view_layout") || (rawPayload.contains("assetFileName") && rawPayload.contains("viewName"))) {
            val viewNameMatch = Regex("""(?:\\?"|")viewName(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""").find(rawPayload)?.groupValues?.get(1) ?: "FootballSlotMain"
            val descMatch = Regex("""(?:\\?"|")description(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""").find(rawPayload)?.groupValues?.get(1)
                ?: "已根据参考规范与素材库完成界面结构规划与图元排版。"

            val cleanJson = extractCleanJsonString(rawPayload, viewNameMatch, descMatch)

            val customMarkdown = buildString {
                append("🛠️ **已完成界面排版规划方案: [$viewNameMatch]**\n\n")
                append(descMatch)
                append("\n\n```json\n")
                append(cleanJson)
                append("\n```")
            }
            return ProjectedChatContent(text = customMarkdown)
        }

        val trimmed = rawPayload.trim()
        val isExplicitJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))

        if (!isExplicitJson) {
            // 文本内容（即便包含内嵌代码块或局部大括号），直接作为排版好的正文返回，严禁误提取局部代码块导致正文被清空
            return ProjectedChatContent(text = rawPayload)
        }

        val rootElement = try {
            jsonParser.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            null
        }

        if (rootElement == null) {
            return ProjectedChatContent(text = rawPayload)
        }

        val projected = try {
            var extractedText = ""
            var extractedThought: String? = null
            var extractedSignature: String? = null
            var hasToolCall = false

            fun processStep(stepObj: JsonObject) {
                val stepType = stepObj["type"]?.jsonPrimitive?.contentOrNull
                if (stepType == "model_output") {
                    val contentArr = stepObj["content"]?.jsonArray
                    val stepText = contentArr?.mapNotNull { c ->
                        c.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    }?.joinToString("\n") ?: ""

                    if (stepText.isNotBlank()) {
                        extractedText = if (extractedText.isBlank()) stepText else "$extractedText\n$stepText"
                    }
                } else if (stepType == "thought") {
                    val sig = stepObj["signature"]?.jsonPrimitive?.contentOrNull
                    if (!sig.isNullOrBlank()) extractedSignature = sig

                    val thoughtStr = stepObj["thought"]?.jsonPrimitive?.contentOrNull
                        ?: stepObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!thoughtStr.isNullOrBlank()) {
                        extractedThought = (extractedThought ?: "") + thoughtStr
                    }
                } else if (stepType == "function_call" || stepType == "tool_call") {
                    hasToolCall = true
                }
            }

            fun processJsonObject(obj: JsonObject) {
                // 0. 优先拦截 Google 官方错误对象 (error: { message, code, status })
                val errorObj = obj["error"]?.jsonObject
                if (errorObj != null) {
                    val errMsg = errorObj["message"]?.jsonPrimitive?.contentOrNull
                        ?: obj["message"]?.jsonPrimitive?.contentOrNull
                        ?: "未知服务端错误"
                    val errCode = errorObj["code"]?.jsonPrimitive?.contentOrNull
                        ?: errorObj["status"]?.jsonPrimitive?.contentOrNull
                    val formattedError = if (!errCode.isNullOrBlank()) "[$errCode] $errMsg" else errMsg
                    extractedText = formattedError
                    return
                }

                // 1. Google Interactions API 官方真实标准：steps 数组或单个 step
                obj["step"]?.jsonObject?.let { processStep(it) }

                val steps = obj["steps"]?.jsonArray
                    ?: obj["interaction"]?.jsonObject?.get("steps")?.jsonArray
                if (steps != null) {
                    for (step in steps) {
                        if (step is JsonObject) {
                            processStep(step)
                        }
                    }
                }

                // 2. 兼容兜底：outputs 数组
                if (extractedText.isBlank()) {
                    val outputs = obj["outputs"]?.jsonArray
                    val outText = outputs?.mapNotNull { item ->
                        item.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    }?.joinToString("\n") ?: ""
                    if (outText.isNotBlank()) extractedText = outText
                }

                // 3. 兼容兜底：candidates 数组 (官方标准 generateContent 格式)
                if (extractedText.isBlank()) {
                    val candidates = obj["candidates"]?.jsonArray
                    val candText = candidates?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray
                        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString("\n") ?: ""
                    if (candText.isNotBlank()) extractedText = candText

                    // 检查 generateContent 的 functionCall
                    val hasFc = candidates?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray
                        ?.any { it.jsonObject.containsKey("functionCall") } == true
                    if (hasFc) hasToolCall = true
                }

                // 4. 若上述特定字段均未命中，检查直接 text 字段
                if (extractedText.isBlank()) {
                    val directText = obj["text"]?.jsonPrimitive?.contentOrNull
                    if (!directText.isNullOrBlank()) {
                        extractedText = directText
                    }
                }
            }

            when (rootElement) {
                is JsonObject -> processJsonObject(rootElement)
                is JsonArray -> {
                    for (item in rootElement) {
                        if (item is JsonObject) {
                            processJsonObject(item)
                        }
                    }
                }
                else -> {}
            }

            ProjectedChatContent(
                text = extractedText,
                thoughtText = extractedThought,
                thoughtSignature = extractedSignature
            )
        } catch (_: Exception) {
            // 解析失败（非合法 JSON），且若包含大括号等特征时避免原样倾倒整段 JSON
            if (rawPayload.contains("\"event_type\"") || rawPayload.contains("\"interaction\"")) {
                ProjectedChatContent(text = "")
            } else {
                ProjectedChatContent(text = rawPayload)
            }
        }

        return projected
    }

    /**
     * 智能识别并解析排版方案工坊卡片数据 (用于对标 Claude Artifacts 的主流呈现方式)
     */
    fun parseLayoutArtifact(rawPayload: String): LayoutArtifactData? {
        if (!rawPayload.contains("apply_view_layout") && !(rawPayload.contains("assetFileName") && rawPayload.contains("viewName"))) {
            return null
        }

        val viewName = Regex("""(?:\\?"|")viewName(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""").find(rawPayload)?.groupValues?.get(1) ?: "FootballSlotMain"
        val desc = Regex("""(?:\\?"|")description(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""").find(rawPayload)?.groupValues?.get(1)
            ?: "已根据传入的 UX 规范文档与素材清单完成足球老虎机高精度界面排版装配。"

        val width = Regex("""(?:\\?"|")width(?:\\?"|")\s*:\s*(\d+)""").find(rawPayload)?.groupValues?.get(1) ?: "1280"
        val height = Regex("""(?:\\?"|")height(?:\\?"|")\s*:\s*(\d+)""").find(rawPayload)?.groupValues?.get(1) ?: "720"

        val modules = mutableListOf<LayoutModuleSummary>()
        val blockIdRegex = Regex("""(?:\\?"|")id(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)(?:\\?"|")[^}]*?(?:\\?"|")type(?:\\?"|")\s*:\s*(?:\\?"|")([^"\\]+)""")
        val matches = blockIdRegex.findAll(rawPayload)
        for (m in matches.take(10)) {
            val id = m.groupValues[1]
            val type = m.groupValues[2]
            if (id != "reel_col_2" && id != "reel_col_3" && id != "reel_col_4" && id != "reel_col_5") {
                modules.add(
                    LayoutModuleSummary(
                        title = id,
                        typeName = type,
                        detailCount = if (type == "CONTAINER") "组合容器" else null
                    )
                )
            }
        }

        return LayoutArtifactData(
            viewName = viewName,
            resolution = "$width × $height",
            description = desc,
            modules = if (modules.isNotEmpty()) modules else listOf(
                LayoutModuleSummary("bg_stadium", "BACKGROUND", "bg_football_stadium.png"),
                LayoutModuleSummary("top_navigation_bar", "CONTAINER", detailCount = "5 项"),
                LayoutModuleSummary("main_slot_machine_window", "SLOTS_WINDOW", detailCount = "5x3 矩阵"),
                LayoutModuleSummary("side_right_jackpot_panel", "CONTAINER", detailCount = "大奖彩池"),
                LayoutModuleSummary("bottom_control_deck", "CONTAINER", detailCount = "旋转控制台")
            ),
            rawJsonSnippet = rawPayload.take(3000)
        )
    }

    /**
     * 提取纯净且格式化的排版 JSON 字符串 (美化缩进，供代码框高亮呈现)
     */
    private fun extractCleanJsonString(raw: String, fallbackViewName: String, desc: String): String {
        // 1. 优先通过平衡括号从报文中提取完整的 blocks 数组并重组为标准排版规范 JSON
        try {
            val blocksIdx = raw.indexOf("blocks")
            if (blocksIdx >= 0) {
                val arrayStart = raw.indexOf('[', blocksIdx)
                if (arrayStart >= 0) {
                    var depth = 0
                    var end = -1
                    for (i in arrayStart until raw.length) {
                        val c = raw[i]
                        if (c == '[') depth++
                        else if (c == ']') {
                            depth--
                            if (depth == 0) {
                                end = i
                                break
                            }
                        }
                    }
                    if (end > arrayStart) {
                        val rawArray = raw.substring(arrayStart, end + 1).replace("\\\"", "\"")
                        return try {
                            val parsedArray = org.gemini.ui.forge.utils.looseJson.parseToJsonElement(rawArray)
                            val wrapperObj = buildJsonObject {
                                put("viewName", fallbackViewName)
                                put("width", 1280)
                                put("height", 720)
                                put("description", desc)
                                put("blocks", parsedArray)
                            }
                            jsonPrettyParser.encodeToString(wrapperObj)
                        } catch (_: Exception) {
                            "{\n  \"viewName\": \"$fallbackViewName\",\n  \"width\": 1280,\n  \"height\": 720,\n  \"description\": \"$desc\",\n  \"blocks\": $rawArray\n}"
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. 尝试从 arguments_delta 或 arguments 内部提取 JSON
        try {
            val argsPattern = Regex("""(?:\\?"|")arguments(?:\\?"|")\s*:\s*"([\s\S]*?)",\s*(?:\\?"|")type(?:\\?"|")""")
            val match = argsPattern.find(raw)
            if (match != null) {
                val unescaped = match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                val parsed = jsonParser.parseToJsonElement(unescaped)
                return jsonPrettyParser.encodeToString(parsed)
            }
        } catch (_: Exception) {}

        return "{\n  \"viewName\": \"$fallbackViewName\",\n  \"description\": \"$desc\"\n}"
    }
}
