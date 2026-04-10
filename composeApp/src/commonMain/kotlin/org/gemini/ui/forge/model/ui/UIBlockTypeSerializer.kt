package org.gemini.ui.forge.model.ui
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 自定义序列化器：用于兼容老版本数据以及大模型返回的未知类型，统一解析为 VIEW。
 */
object UIBlockTypeSerializer : KSerializer<UIBlockType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UIBlockType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UIBlockType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): UIBlockType {
        val name = decoder.decodeString()
        return try {
            UIBlockType.valueOf(name)
        } catch (e: Exception) {
            UIBlockType.VIEW // 未知类型统一降级为 VIEW
        }
    }
}
