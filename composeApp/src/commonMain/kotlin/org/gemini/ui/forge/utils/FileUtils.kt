package org.gemini.ui.forge.utils

/**
 * 跨平台读取本地文件为字节数组
 */
expect fun readLocalFileBytes(filePath: String): ByteArray?

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
