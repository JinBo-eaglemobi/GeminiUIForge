package org.gemini.ui.forge.model.ui
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

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
        
        return if (element is JsonArray) {
            val array = element.jsonArray
            SerialRect(
                left = array.getOrNull(0)?.jsonPrimitive?.float ?: 0f,
                top = array.getOrNull(1)?.jsonPrimitive?.float ?: 0f,
                right = array.getOrNull(2)?.jsonPrimitive?.float ?: 0f,
                bottom = array.getOrNull(3)?.jsonPrimitive?.float ?: 0f
            )
        } else if (element is JsonObject) {
            val obj = element.jsonObject
            SerialRect(
                left = obj["left"]?.jsonPrimitive?.float ?: 0f,
                top = obj["top"]?.jsonPrimitive?.float ?: 0f,
                right = obj["right"]?.jsonPrimitive?.float ?: 0f,
                bottom = obj["bottom"]?.jsonPrimitive?.float ?: 0f
            )
        } else {
            throw IllegalArgumentException("Unexpected JSON element for SerialRect: $element")
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
