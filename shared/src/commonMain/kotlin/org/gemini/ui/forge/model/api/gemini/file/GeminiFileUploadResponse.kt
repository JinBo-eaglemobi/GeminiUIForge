package org.gemini.ui.forge.model.api.gemini.file
import kotlinx.serialization.Serializable

/**
 * 当成功向 Gemini 服务器上传一个多媒体文件后，服务器返回的响应体对象。
 *
 * @property file 刚成功上传的文件的元数据对象。该对象包含了新生成的云端唯一标识 (name) 和可供大模型引用的指针 (uri)，
 * 客户端应立即将此对象缓存或更新至“云端资产管理器”，以便在后续的 Prompt 分析与生成任务中进行多模态上下文引用。
 */
@Serializable
data class GeminiFileUploadResponse(
    val file: GeminiFile
)
