package org.gemini.ui.forge.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TemplateFileSerializer : KSerializer<TemplateFile> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TemplateFile", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TemplateFile) {
        encoder.encodeString(value.relativePath)
    }

    override fun deserialize(decoder: Decoder): TemplateFile {
        return TemplateFile(decoder.decodeString())
    }
}
