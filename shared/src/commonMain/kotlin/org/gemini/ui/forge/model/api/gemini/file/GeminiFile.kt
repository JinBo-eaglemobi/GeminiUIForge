package org.gemini.ui.forge.model.api.gemini.file
import kotlinx.serialization.Serializable

/**
 * 表示存储在 Gemini 服务器上的单个文件元数据（Metadata）。
 * 该对象通常由 Gemini File API 返回，用于在使用 Gemini 大语言模型时引用已上传的多媒体资源。
 *
 * @property name 文件的全局唯一标识符（Resource Name），格式始终为 "files/{file_id}"。用于在 API 调用中指定目标文件进行查询或删除。
 * @property displayName 用户在上传时指定的文件显示名称。用于在 UI 资产管理器中进行可读性展示（如 "Template_Login_Screen"）。
 * @property mimeType 文件的 MIME 类型（例如 "image/png" 或 "image/jpeg"），标识该文件的媒体格式。
 * @property sizeBytes 文件的大小（以字节为单位），以字符串形式返回以防止超大文件导致的整数溢出。
 * @property createTime 文件的创建时间戳，采用 RFC 3339 格式的字符串（例如 "2024-05-01T12:00:00Z"）。
 * @property expirationTime 文件的自动过期时间戳。Gemini File API 默认会在文件创建后 48 小时自动将其永久删除。
 * @property updateTime 文件的最后更新时间戳。
 * @property state 当前文件的处理状态。可能的值包括："PROCESSING"（处理中，暂不可用）、"ACTIVE"（已就绪，可供模型推理调用）、"FAILED"（处理失败）。
 * @property uri 用于在多模态 Prompt 中引用此文件的内部网络地址（URI）。**注意：该地址是供大模型内部网络访问的文件指针，不能直接在公网通过浏览器或普通的 HTTP 请求下载。**
 */
@Serializable
data class GeminiFile(
    val name: String,
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: String? = null,
    val createTime: String? = null,
    val expirationTime: String? = null,
    val updateTime: String? = null,
    val state: String? = null,
    val uri: String? = null
)
