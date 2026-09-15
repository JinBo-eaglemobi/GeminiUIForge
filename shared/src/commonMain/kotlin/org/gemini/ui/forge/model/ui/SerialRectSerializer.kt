package org.gemini.ui.forge.model.ui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * 具有超级容错能力的 [SerialRect] JSON 序列化器
 *
 * 彻底消除 AI 大模型返回边界数据格式不确定时的崩溃问题：
 * 1. 原生 JSON 数组：`[0, 0, 720, 1080]`
 * 2. 原生 JSON 对象：`{"left": 0, "top": 0, "right": 720, "bottom": 1080}` 或 `{"x": 0, "y": 0, "width": 720, "height": 1080}`
 * 3. 带双引号的数组字符串（大模型常见转义）：`"[0, 0, 720, 1080]"` 或 `"[0,0,720,1080]"`
 * 4. 逗号/空格分隔的纯数值字符串：`"0, 0, 720, 1080"` 或 `"0,0,720,1080"`
 * 5. 带引号的嵌套 JSON 对象字符串：`"{\"left\":0, ...}"`
 * 6. 极端畸变兜底：自动降级为 `SerialRect(0f, 0f, 0f, 0f)`，绝不抛出崩溃中断整个工作流。
 */
object SerialRectSerializer : KSerializer<SerialRect> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SerialRect") {
        element<Float>("left")
        element<Float>("top")
        element<Float>("right")
        element<Float>("bottom")
    }

    override fun deserialize(decoder: Decoder): SerialRect {
        require(decoder is JsonDecoder) { "This serializer can be used only with JSON format" }
        val element = decoder.decodeJsonElement()
        return parseSerialRect(element)
    }

    private fun parseSerialRect(element: JsonElement): SerialRect {
        return try {
            when (element) {
                is JsonArray -> parseFromArray(element.jsonArray)
                is JsonObject -> parseFromObject(element.jsonObject)
                is JsonPrimitive -> {
                    val content = element.content.trim()
                    when {
                        content.startsWith("[") && content.endsWith("]") -> {
                            val parsed = try { Json.parseToJsonElement(content) } catch (_: Exception) { null }
                            if (parsed is JsonArray) {
                                parseFromArray(parsed)
                            } else {
                                parseFromStringList(content.removeSurrounding("[", "]"))
                            }
                        }
                        content.startsWith("{") && content.endsWith("}") -> {
                            val parsed = try { Json.parseToJsonElement(content) } catch (_: Exception) { null }
                            if (parsed is JsonObject) {
                                parseFromObject(parsed)
                            } else {
                                SerialRect(0f, 0f, 0f, 0f)
                            }
                        }
                        else -> parseFromStringList(content)
                    }
                }
            }
        } catch (_: Exception) {
            // 发生任何未预期的异常时，安全兜底，保护全局反序列化树不崩溃
            SerialRect(0f, 0f, 0f, 0f)
        }
    }

    private fun parseFromArray(array: JsonArray): SerialRect {
        return SerialRect(
            left = array.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0f,
            top = array.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0f,
            right = array.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: 0f,
            bottom = array.getOrNull(3)?.jsonPrimitive?.floatOrNull ?: 0f
        )
    }

    private fun parseFromObject(obj: JsonObject): SerialRect {
        val left = obj["left"]?.jsonPrimitive?.floatOrNull
            ?: obj["x"]?.jsonPrimitive?.floatOrNull
            ?: 0f
        val top = obj["top"]?.jsonPrimitive?.floatOrNull
            ?: obj["y"]?.jsonPrimitive?.floatOrNull
            ?: 0f
        val right = obj["right"]?.jsonPrimitive?.floatOrNull
            ?: (left + (obj["width"]?.jsonPrimitive?.floatOrNull ?: 0f))
        val bottom = obj["bottom"]?.jsonPrimitive?.floatOrNull
            ?: (top + (obj["height"]?.jsonPrimitive?.floatOrNull ?: 0f))
        return SerialRect(left, top, right, bottom)
    }

    private fun parseFromStringList(raw: String): SerialRect {
        val parts = raw.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val floats = parts.mapNotNull { it.toFloatOrNull() }
        return if (floats.size >= 4) {
            SerialRect(floats[0], floats[1], floats[2], floats[3])
        } else {
            SerialRect(0f, 0f, 0f, 0f)
        }
    }

    override fun serialize(encoder: Encoder, value: SerialRect) {
        require(encoder is JsonEncoder) { "This serializer can be used only with JSON format" }
        // 默认将坐标序列化为体积更小的数组形式 [left, top, right, bottom]
        encoder.encodeJsonElement(JsonArray(listOf(
            JsonPrimitive(value.left),
            JsonPrimitive(value.top),
            JsonPrimitive(value.right),
            JsonPrimitive(value.bottom)
        )))
    }
}
