package org.gemini.ui.forge.model.ui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 自定义序列化器：用于兼容老版本历史数据以及大模型返回的别名类型，平滑映射到基础 UIBlockType。
 */
object UIBlockTypeSerializer : KSerializer<UIBlockType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UIBlockType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UIBlockType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): UIBlockType {
        val name = decoder.decodeString().uppercase().trim()
        return when (name) {
            "TEXT_AREA", "LABEL" -> UIBlockType.TEXT
            "ICON", "BACKGROUND", "SYMBOL" -> UIBlockType.IMAGE
            "SPIN_BUTTON" -> UIBlockType.BUTTON
            "REEL", "LIST", "ROLL_LIST", "GLIST" -> UIBlockType.LIST
            "SPINE", "SKELETON", "MOVIE_CLIP" -> UIBlockType.SPINE
            "PANEL", "GROUP", "DECORATION", "CONTAINER", "WIN_DISPLAY", "HEADER", "FOOTER", "SLOTS_WINDOW", "VIEW", "COMPONENT" -> UIBlockType.COMPONENT
            else -> try {
                UIBlockType.valueOf(name)
            } catch (_: Exception) {
                UIBlockType.COMPONENT // 未知类型统一降级为 COMPONENT
            }
        }
    }
}
