package org.gemini.ui.forge.service

// 简单的 Android 存储变通方案，使用全局变量或传递上下文?
// 在真实的应用程序中，我们将通过依赖注入框架（如 Koin）将上下文传递给 LocalFileStorage?
// 但为了简单起见，在不修改整个应用程序架构的情况下?
// 如果上下文为空，我们将仅使用后备目录，或者在没有上下文的情况下模拟它?

actual class LocalFileStorage {
    actual fun saveToFile(fileName: String, content: String): String {
        return ""
    }
    actual fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        return ""
    }
    actual fun readFromFile(fileName: String): String? {
        return null
    }
    actual fun readBytesFromFile(fileName: String): ByteArray? {
        return null
    }
    actual fun listFiles(): List<String> {
        return emptyList()
    }
    actual fun listDirectories(): List<String> {
        return emptyList()
    }
    actual fun deleteFile(fileName: String): Boolean { return false }
    actual fun deleteDirectory(dirName: String): Boolean { return false }
    actual fun getFilePath(fileName: String): String {
        return ""
    }
}


