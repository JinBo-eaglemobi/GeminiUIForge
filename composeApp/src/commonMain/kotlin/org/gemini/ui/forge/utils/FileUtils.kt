package org.gemini.ui.forge.utils

/**
 * 跨平台读取本地文件为字节数组 (异步版，以支持 JS OPFS)
 */
expect suspend fun readLocalFileBytes(filePath: String): ByteArray?

/**
 * 跨平台计算字节数组的 MD5 哈希值（十六进制字符串）
 */
expect fun ByteArray.calculateMd5(): String

/**
 * 跨平台判断本地文件是否存在 (异步版，以支持 JS OPFS)
 * 用于轻量化预验证，避免直接加载大文件进内存。
 */
expect suspend fun isFileExists(filePath: String): Boolean

/**
 * 根据文件路径或 URI 后缀名推断准确的 MIME 类型。
 * 确保 Gemini API 能接收到正确的媒体格式描述，从而提高识别准确度。
 */
fun getMimeType(uri: String): String {
    val extension = uri.substringAfterLast(".", "").lowercase()
    return when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "gif" -> "image/gif"
        else -> "image/jpeg" // 默认兜底为 JPEG
    }
}
