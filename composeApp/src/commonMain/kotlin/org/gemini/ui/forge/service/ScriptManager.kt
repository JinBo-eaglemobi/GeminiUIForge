package org.gemini.ui.forge.service

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.gemini.ui.forge.utils.AppLogger

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
            // 1. 检查本地是否已存在
            if (!storage.exists(relativePath)) {
                AppLogger.i(TAG, "🚀 正在从资源中提取脚本: $fileName")
                val resourcePath = "scripts/$fileName"
                val bytes = try {
                    readResourceBytes(resourcePath)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "❌ 无法从资源读取脚本: $resourcePath", e)
                    null
                }

                if (bytes != null) {
                    storage.saveBytesToFile(relativePath, bytes)
                    AppLogger.i(TAG, "✅ 脚本提取成功: $fileName")
                } else {
                    return null
                }
            }
            
            // 2. 返回绝对路径
            return storage.getFilePath(relativePath)
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 获取脚本路径失败: $fileName", e)
            return null
        }
    }
}
