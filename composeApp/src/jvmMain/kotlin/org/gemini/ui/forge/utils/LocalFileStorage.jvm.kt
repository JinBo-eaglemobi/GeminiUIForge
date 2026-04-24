package org.gemini.ui.forge.utils

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.state.GlobalAppEnv

import org.gemini.ui.forge.utils.AppLogger

actual class LocalFileStorage {
    private var dataDir = File(System.getProperty("user.home"), ".geminiuiforge")

    init {
        // 1. 检查并创建应用根存储目录（默认为用户家目录下的 .geminiuiforge）
        if (!dataDir.exists()) {
            val success = dataDir.mkdirs()
            AppLogger.i("LocalFileStorage", "📂 初始化应用根目录: ${dataDir.absolutePath} (创建成功: $success)")
        } else {
            AppLogger.d("LocalFileStorage", "📂 应用根目录已存在: ${dataDir.absolutePath}")
        }
        
        // 2. 将确定的存储路径同步至全局环境变量，供其他跨平台模块参考
        // 关键修复：仅当路径发生实际变化时才通知 GlobalAppEnv，防止因对象重复创建导致的全局重绘死循环
        val absolutePath = dataDir.absolutePath
        if (GlobalAppEnv.currentRootPath != absolutePath) {
            AppLogger.d("LocalFileStorage", "🔗 正在更新 GlobalAppEnv 数据根路径...")
            GlobalAppEnv.updateDataRoot(absolutePath)
        } else {
            AppLogger.d("LocalFileStorage", "🔗 GlobalAppEnv 数据路径未变，跳过更新")
        }
        
        // 3. 确保核心子目录 templates 存在，用于存放项目 JSON 定义
        val templatesDir = File(dataDir, "templates")
        if (!templatesDir.exists()) {
            templatesDir.mkdirs()
            AppLogger.d("LocalFileStorage", "📁 创建 templates 目录")
        }

        // 4. 执行目录结构迁移逻辑：将旧版本中嵌套在 templates 内的 scripts 和 prompts 提升至根目录
        // 这样可以使目录结构更清晰，符合最新的跨平台资源管理规范
        AppLogger.d("LocalFileStorage", "🚚 开始执行目录结构检查与迁移...")
        migrateInternalDir("templates/scripts", "scripts")
        migrateInternalDir("templates/prompts", "prompts")
        AppLogger.i("LocalFileStorage", "✅ LocalFileStorage 初始化完成")
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