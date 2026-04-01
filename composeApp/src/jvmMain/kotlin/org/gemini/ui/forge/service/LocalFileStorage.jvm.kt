package org.gemini.ui.forge.service

import java.io.File

actual class LocalFileStorage {
    private val dataDir = File(System.getProperty("user.home"), ".geminiuiforge/templates")

    init {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    actual fun saveToFile(fileName: String, content: String): String {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeText(content)
        val path = target.absolutePath
        println("【已保存文本】: $path")
        return path
    }

    actual fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        val path = target.absolutePath
        println("【已保存资源】: $path")
        return path
    }

    actual fun readFromFile(fileName: String): String? {
        val file = File(dataDir, fileName)
        return if (file.exists()) file.readText() else null
    }

    actual fun readBytesFromFile(fileName: String): ByteArray? {
        val file = File(dataDir, fileName)
        return if (file.exists()) file.readBytes() else null
    }

    actual fun listFiles(): List<String> {
        return dataDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.map { it.name } ?: emptyList()
    }

    actual fun listDirectories(): List<String> {
        return dataDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    actual fun deleteFile(fileName: String): Boolean {
        val file = File(dataDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    actual fun deleteDirectory(dirName: String): Boolean {
        val dir = File(dataDir, dirName)
        return dir.deleteRecursively()
    }

    actual fun getFilePath(fileName: String): String {
        return File(dataDir, fileName).absolutePath
    }
}
