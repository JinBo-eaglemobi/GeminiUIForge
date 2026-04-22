package org.gemini.ui.forge.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.state.GlobalAppEnv

import org.gemini.ui.forge.utils.AppLogger

actual class LocalFileStorage {
    private var dataDir = File(System.getProperty("user.home"), ".geminiuiforge")

    init {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
            AppLogger.i("LocalFileStorage", "📂 初始化应用根目录: ${dataDir.absolutePath}")
        }
        
        // 关键：初始化全局环境根目录
        GlobalAppEnv.updateDataRoot(dataDir.absolutePath)
        
        // 确保 templates 目录存在
        val templatesDir = File(dataDir, "templates")
        if (!templatesDir.exists()) templatesDir.mkdirs()

        // 迁移逻辑：如果旧的 scripts 或 prompts 还在 templates 内部，则移动出来
        migrateInternalDir("templates/scripts", "scripts")
        migrateInternalDir("templates/prompts", "prompts")
    }

    private fun migrateInternalDir(oldRelativePath: String, newRelativePath: String) {
        val oldDir = File(dataDir, oldRelativePath)
        val newDir = File(dataDir, newRelativePath)
        if (oldDir.exists() && oldDir.isDirectory) {
            if (!newDir.exists()) {
                val success = oldDir.renameTo(newDir)
                AppLogger.i("LocalFileStorage", "🚚 迁移目录: $oldRelativePath -> $newRelativePath (成功: $success)")
            } else {
                // 如果新目录已存在，尝试合并文件
                oldDir.listFiles()?.forEach { file ->
                    val targetFile = File(newDir, file.name)
                    if (!targetFile.exists()) file.renameTo(targetFile)
                }
                oldDir.delete()
                AppLogger.i("LocalFileStorage", "🔗 合并并清理旧目录: $oldRelativePath")
            }
        }
    }

    actual suspend fun updateDataDir(newPath: String): Boolean = withContext(Dispatchers.IO) {
        val newDir = File(newPath)
        if (newDir.absolutePath == dataDir.absolutePath) return@withContext true
        
        AppLogger.i("LocalFileStorage", "🔄 正在迁移数据目录至: $newPath")
        try {
            if (!newDir.exists()) newDir.mkdirs()
            if (dataDir.exists()) {
                dataDir.listFiles()?.forEach { file ->
                    file.renameTo(File(newDir, file.name))
                }
            }
            
            dataDir = newDir
            GlobalAppEnv.updateDataRoot(dataDir.absolutePath)
            AppLogger.i("LocalFileStorage", "✅ 目录迁移成功")
            return@withContext true
        } catch (e: Exception) {
            AppLogger.e("LocalFileStorage", "❌ 目录迁移失败", e)
            return@withContext false
        }
    }

    actual suspend fun getDataDir(): String = withContext(Dispatchers.IO) { dataDir.absolutePath }

    actual suspend fun saveToFile(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeText(content)
        AppLogger.d("LocalFileStorage", "📝 文本已保存: $fileName (${content.length} chars)")
        return@withContext fileName
    }

    actual suspend fun saveBytesToFile(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(dataDir, fileName)
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        AppLogger.d("LocalFileStorage", "🎨 资源已保存: $fileName (${bytes.size / 1024} KB)")
        return@withContext fileName
    }

    actual suspend fun readFromFile(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        val exists = file.exists()
        if (!exists) AppLogger.d("LocalFileStorage", "🔍 读取文件不存在: $fileName")
        return@withContext if (exists) file.readText() else null
    }

    actual suspend fun readBytesFromFile(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        return@withContext if (file.exists()) file.readBytes() else null
    }

    actual suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        return@withContext dataDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.map { it.name } ?: emptyList()
    }

    actual suspend fun listDirectories(parentDir: String?): List<String> = withContext(Dispatchers.IO) {
        val base = if (parentDir != null) File(dataDir, parentDir) else dataDir
        return@withContext base.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    actual suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(dataDir, fileName)
        val success = if (file.exists()) file.delete() else false
        if (success) AppLogger.d("LocalFileStorage", "🗑️ 文件已删除: $fileName")
        return@withContext success
    }

    actual suspend fun deleteDirectory(dirName: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(dataDir, dirName)
        val success = dir.deleteRecursively()
        if (success) AppLogger.i("LocalFileStorage", "🗑️ 目录已递归删除: $dirName")
        return@withContext success
    }

    actual suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext File(dataDir, fileName).exists()
    }

    actual suspend fun getFilePath(fileName: String): String = withContext(Dispatchers.IO) {
        return@withContext File(dataDir, fileName).absolutePath
    }
}