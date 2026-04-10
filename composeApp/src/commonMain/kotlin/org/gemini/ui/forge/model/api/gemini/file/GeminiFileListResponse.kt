package org.gemini.ui.forge.model.api.gemini.file
import kotlinx.serialization.Serializable

/**
 * 当调用 Gemini File API 执行“列出文件 (List Files)”操作时，服务器返回的响应体对象。
 * 用于获取当前项目下所有有效（未过期且未被主动删除）的文件列表，是实现“云端资产管理器”核心数据同步的基石。
 *
 * @property files 当前页返回的 [GeminiFile] 对象列表。如果当前没有任何云端文件，则该列表可能为空。
 * @property nextPageToken 用于分页查询的凭证。当服务器上的文件总数超过单次请求的限制（默认通常为 100）时，API 会返回此 Token。
 * 在下一次请求的 Query 参数中携带此 Token（`?pageToken=xxx`），即可获取下一页的文件列表。如果该字段为空，则表示已经到达最后一页，无需继续请求。
 */
@Serializable
data class GeminiFileListResponse(
    val files: List<GeminiFile>? = null,
    val nextPageToken: String? = null
)
