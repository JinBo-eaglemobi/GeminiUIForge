package org.gemini.ui.forge.service

actual class LocalFileStorage {
    actual fun saveToFile(fileName: String, content: String): String {
        return ""
    }
    actual fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        return ""
    }
    actual fun readFromFile(fileName: String): String? = null
    actual fun readBytesFromFile(fileName: String): ByteArray? = null
    actual fun listFiles(): List<String> = emptyList()
    actual fun listDirectories(): List<String> = emptyList()
    actual fun getFilePath(fileName: String): String = ""
    actual fun deleteFile(fileName: String): Boolean = false
    actual fun deleteDirectory(dirName: String): Boolean = false
}
