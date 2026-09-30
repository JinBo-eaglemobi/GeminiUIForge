package org.gemini.ui.forge.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [TemplateFile] 的自定义序列化器
 *
 * 保证在持久化模板 JSON 时仅序列化纯净的规范化相对路径字符串（[TemplateFile.relativePath]），
 * 完全剥离宿主机本地的绝对路径与环境特定的根目录，确保跨操作系统与跨设备迁移的一致性。
 */
object TemplateFileSerializer : KSerializer<TemplateFile> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TemplateFile", PrimitiveKind.STRING)

    /** 将 [TemplateFile] 序列化为规范相对路径字符串 */
    override fun serialize(encoder: Encoder, value: TemplateFile) {
        encoder.encodeString(value.relativePath)
    }

    /** 将相对路径字符串反序列化重构成挂靠当前运行环境的 [TemplateFile] 实例 */
    override fun deserialize(decoder: Decoder): TemplateFile {
        return TemplateFile(decoder.decodeString())
    }
}
