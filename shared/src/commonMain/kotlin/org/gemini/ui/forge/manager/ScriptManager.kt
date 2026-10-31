package org.gemini.ui.forge.manager

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage

/**
 * 脚本管理器
 * 负责管理本地 Python 脚本的提取与路径获取
 */
class ScriptManager(private val storage: LocalFileStorage) {
    private val TAG = "ScriptManager"
    private val SCRIPTS_DIR = "scripts"

    /**
     * 获取脚本的本地绝对路径。
     * 如果本地不存在，则从资源文件中提取并保存。
     */
    @OptIn(InternalResourceApi::class)
    suspend fun getScriptPath(scriptName: String): String? {
        val fileName = if (scriptName.endsWith(".py")) scriptName else "$scriptName.py"
        val relativePath = "$SCRIPTS_DIR/$fileName"

        try {
            val resourcePath = "scripts/$fileName"
            val resBytes = try {
                readResourceBytes(resourcePath)
            } catch (e: Exception) {
                AppLogger.w(TAG, "⚠️ 无法从内置资源读取脚本: $resourcePath, 尝试降级使用本地磁盘既有文件", e)
                null
            }

            val existsOnDisk = storage.exists(relativePath)

            if (resBytes != null) {
                var shouldOverwrite = !existsOnDisk
                if (existsOnDisk) {
                    val localBytes = storage.readBytesFromFile(relativePath)
                    // 严密对比大小与内容/CRC（contentEquals），只要有任一差异立刻拷贝覆盖
                    if (localBytes == null || localBytes.size != resBytes.size || !localBytes.contentEquals(resBytes)) {
                        AppLogger.i(
                            TAG,
                            "🔄 检测到脚本版本/内容差异: $fileName (本地: ${localBytes?.size ?: 0} 字节, 内置资源: ${resBytes.size} 字节)，正在执行覆盖同步"
                        )
                        shouldOverwrite = true
                    }
                }

                if (shouldOverwrite) {
                    storage.saveBytesToFile(relativePath, resBytes)
                    AppLogger.i(TAG, "✅ 脚本已更新同步至本地: $fileName")
                }
            } else if (!existsOnDisk) {
                AppLogger.e(TAG, "❌ 内置资源缺失且本地无脚本文件: $fileName")
                return null
            }

            // 返回脚本本地绝对路径
            return storage.getFilePath(relativePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 获取/同步脚本失败: $fileName", e)
            return null
        }
    }
}
