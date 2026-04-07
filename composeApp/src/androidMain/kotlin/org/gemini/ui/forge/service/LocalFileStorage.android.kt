package org.gemini.ui.forge.service

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LocalFileStorage {
    
    /**
     * 利用反射获取全局 Application Context 以访问内部存储。
     */
    private val context: Context? by lazy {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val application = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.app.Application
            application
        } catch (e: Exception) {
            null
        }
    }

    // 回退目录，在无法获取 Context 时使用
    private var fallbackDir = File("/data/data/org.gemini.ui.forge/files", "templates")
    
    private var currentDir: File = context?.let { File(it.filesDir, "templates") } ?: fallbackDir

    init {
        if (!currentDir.exists()) {
            currentDir.mkdirs()
        }
    }

    actual suspend fun updateDataDir(newPath: String): Boolean = withContext(Dispatchers.IO) {
        val newDir = File(newPath)
        if (newDir.absolutePath == currentDir.absolutePath) return@withContext true
        
        try {
            if (!newDir.exists()) newDir.mkdirs()
            if (currentDir.exists()) {
                currentDir.listFiles()?.forEach { file ->
                    file.renameTo(File(newDir, file.name))
                }
            }
            currentDir = newDir
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    actual suspend fun getDataDir(): String = withContext(Dispatchers.IO) { currentDir.absolutePath }

    actual suspend fun saveToFile(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val target = File(currentDir, fileName)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return@withContext target.absolutePath
    }

    actual suspend fun saveBytesToFile(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(currentDir, fileName)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return@withContext target.absolutePath
    }

    actual suspend fun readFromFile(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(currentDir, fileName)
        return@withContext if (file.exists()) file.readText() else null
    }

    actual suspend fun readBytesFromFile(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(currentDir, fileName)
        return@withContext if (file.exists()) file.readBytes() else null
    }

    actual suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        return@withContext currentDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.map { it.name } ?: emptyList()
    }

    actual suspend fun listDirectories(): List<String> = withContext(Dispatchers.IO) {
        return@withContext currentDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    actual suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(currentDir, fileName)
        return@withContext if (file.exists()) file.delete() else false
    }

    actual suspend fun deleteDirectory(dirName: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(currentDir, dirName)
        return@withContext dir.deleteRecursively()
    }

    actual suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext File(currentDir, fileName).exists()
    }

    actual suspend fun getFilePath(fileName: String): String = withContext(Dispatchers.IO) {
        return@withContext File(currentDir, fileName).absolutePath
    }
}