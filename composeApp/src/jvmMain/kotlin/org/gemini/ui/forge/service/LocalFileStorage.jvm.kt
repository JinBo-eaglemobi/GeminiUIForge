package org.gemini.ui.forge.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LocalFileStorage {
    private var dataDir = File(System.getProperty("user.home"), ".geminiuiforge/templates")

    init {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    actual suspend fun updateDataDir(newPath: String): Boolean = withContext(Dispatchers.IO) {
        val newDir = File(newPath)
        if (newDir.absolutePath == dataDir.absolutePath) return@withContext true
        
        try {
            if (!newDir.exists()) newDir.mkdirs()
            if (dataDir.exists()) {
                dataDir.listFiles()?.forEach { file ->
                    file.renameTo(File(newDir, file.name))
                }
            }
            dataDir = newDir
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    actual suspend fun getDataDir(): String = withContext(Dispatchers.IO) { dataDir.absolutePath }

    actual suspend fun saveToFile(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeText(content)
        println("【已保存文本】: ${target.absolutePath}")
        return@withContext target.absolutePath
    }

    actual suspend fun saveBytesToFile(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        println("【已保存资源】: ${target.absolutePath}")
        return@withContext target.absolutePath
    }

    actual suspend fun readFromFile(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        return@withContext if (file.exists()) file.readText() else null
    }

    actual suspend fun readBytesFromFile(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        return@withContext if (file.exists()) file.readBytes() else null
    }

    actual suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        return@withContext dataDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.map { it.name } ?: emptyList()
    }

    actual suspend fun listDirectories(): List<String> = withContext(Dispatchers.IO) {
        return@withContext dataDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    actual suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        return@withContext if (file.exists()) file.delete() else false
    }

    actual suspend fun deleteDirectory(dirName: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(dataDir, dirName)
        return@withContext dir.deleteRecursively()
    }

    actual suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext File(dataDir, fileName).exists()
    }

    actual suspend fun getFilePath(fileName: String): String = withContext(Dispatchers.IO) {

        return@withContext File(dataDir, fileName).absolutePath
    }
}