package org.gemini.ui.forge.service

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LocalFileStorage {
    
    private val context: Context? by lazy {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val application = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.app.Application
            application
        } catch (e: Exception) {
            null
        }
    }

    private var currentDir: File = context?.filesDir ?: File("/data/data/org.gemini.ui.forge/files")

    init {
        if (!currentDir.exists()) {
            currentDir.mkdirs()
        }
        
        // 确保 templates 目录存在
        val templatesDir = File(currentDir, "templates")
        if (!templatesDir.exists()) templatesDir.mkdirs()

        // 迁移逻辑：如果旧的 scripts 或 prompts 还在 templates 内部，则移动出来
        migrateInternalDir("templates/scripts", "scripts")
        migrateInternalDir("templates/prompts", "prompts")
    }

    private fun migrateInternalDir(oldRelativePath: String, newRelativePath: String) {
        val oldDir = File(currentDir, oldRelativePath)
        val newDir = File(currentDir, newRelativePath)
        if (oldDir.exists() && oldDir.isDirectory) {
            if (!newDir.exists()) {
                oldDir.renameTo(newDir)
            } else {
                oldDir.listFiles()?.forEach { file ->
                    val targetFile = File(newDir, file.name)
                    if (!targetFile.exists()) file.renameTo(targetFile)
                }
                oldDir.delete()
            }
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

    actual suspend fun listDirectories(parentDir: String?): List<String> = withContext(Dispatchers.IO) {
        val base = if (parentDir != null) File(currentDir, parentDir) else currentDir
        return@withContext base.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
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