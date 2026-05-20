package org.gemini.ui.forge.utils

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LocalFileStorage {
    private var dataDir = File(org.gemini.ui.forge.userHomePath, ".geminiuiforge")

    companion object {
        private var hasMigrated = false
    }

    init {
        AppLogger.d("LocalFileStorage", "🚀 正在启动 LocalFileStorage 初始化... (JVM)")
        
        // 1. 基础根目录检查 (快速)
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        
        val absolutePath = dataDir.absolutePath
        if (GlobalAppEnv.currentRootPath != absolutePath) {
            GlobalAppEnv.updateDataRoot(absolutePath)
        }
        
        // 2. 耗时的迁移逻辑移至协程，避免阻塞 UI
        if (!hasMigrated) {
            hasMigrated = true
            // 注意：由于 init 无法直接使用协程，且 LocalFileStorage 并不持有 scope，
            // 这里的迁移逻辑应在第一次 save/read 调用时被触发，或者通过 Dispatchers.IO 快速切走
            // 为了保证简单且不阻塞 init，我们仅确保 templates 目录存在，其余迁移逻辑延迟或静默执行
            val templatesDir = File(dataDir, "templates")
            if (!templatesDir.exists()) templatesDir.mkdirs()

            // 迁移任务通常在第一次真正执行 IO 时更合适，或者直接在此处开启一个线程 (非协程) 处理
            Thread {
                try {
                    AppLogger.d("LocalFileStorage", "🚚 [Background] 开始执行目录结构检查与迁移...")
                    migrateInternalDir("templates/scripts", "scripts")
                    migrateInternalDir("templates/prompts", "prompts")
                    AppLogger.i("LocalFileStorage", "✅ [Background] 目录迁移完成")
                } catch (e: Exception) {
                    AppLogger.e("LocalFileStorage", "❌ [Background] 迁移异常", e)
                }
            }.start()
        }
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