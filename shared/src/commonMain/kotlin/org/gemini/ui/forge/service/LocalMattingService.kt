package org.gemini.ui.forge.service

import org.gemini.ui.forge.getCurrentTimeMillis
import org.gemini.ui.forge.manager.ScriptManager
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.deleteLocalFile
import org.gemini.ui.forge.utils.executeSystemCommand
import org.gemini.ui.forge.utils.isFileExists
import org.gemini.ui.forge.utils.readLocalFileBytes

/**
 * 专职负责本地图像离线抠图与透明通道处理的本地服务 (Local Matting Service)
 *
 * 架构分离原则：
 * 本服务专职负责调度本地 Python 运行环境及离线算法脚本 (如 rembg, pillow)，
 * 不涉及任何大模型网络通信，与面向云端 API 的 [AIGenerationService] 完全解耦。
 */
class LocalMattingService(
    private val storage: LocalFileStorage,
    private val scriptManager: ScriptManager = ScriptManager(storage)
) {
    private val TAG = "LocalMattingService"

    /**
     * 内部辅助方法：同步将日志发送到 UI 回调和磁盘日志系统。
     */
    private fun syncLog(message: String, onLog: (String) -> Unit) {
        onLog(message)
        AppLogger.i(TAG, message)
    }

    /**
     * 调用本地 Python 离线脚本执行图像背景去除 (rembg/pillow)
     *
     * @param imageBytes 原始待处理图像字节数组。
     * @param onLog 执行过程中的日志回调。
     * @return 成功返回去除背景后的透明通道 PNG 图像字节数组；失败返回 null。
     */
    suspend fun removeBackground(
        imageBytes: ByteArray,
        onLog: (String) -> Unit = {}
    ): ByteArray? {
        val scriptPath = scriptManager.getScriptPath("remove_bg.py") ?: run {
            syncLog("❌ 未能获取本地抠图脚本路径 (remove_bg.py)", onLog)
            return null
        }

        val timestamp = getCurrentTimeMillis()
        val inputPath = storage.getFilePath("temp/input_$timestamp.png")
        val outputPath = storage.getFilePath("temp/output_$timestamp.png")

        return try {
            storage.saveBytesToFile("temp/input_$timestamp.png", imageBytes)

            syncLog("🚀 启动本地 Python 离线抠图引擎...", onLog)

            val commands = listOf("python", "python3")
            var success = false
            val executionLogs = StringBuilder()

            for (cmd in commands) {
                success = executeSystemCommand(
                    command = cmd,
                    args = listOf(scriptPath, inputPath, outputPath),
                    onLog = { 
                        syncLog("[LocalRembg] $it", onLog)
                        executionLogs.appendLine(it)
                    }
                )
                if (success) break
            }

            if (success && isFileExists(outputPath)) {
                val result = readLocalFileBytes(outputPath)
                syncLog("✅ 本地离线抠图处理完成", onLog)
                result
            } else {
                syncLog("❌ 本地抠图脚本执行失败。可能的原因如下:\n$executionLogs", onLog)
                null
            }
        } catch (e: Exception) {
            syncLog("❌ 本地抠图异常: ${e.message}\n${e.stackTraceToString()}", onLog)
            null
        } finally {
            deleteLocalFile(inputPath)
            deleteLocalFile(outputPath)
        }
    }
}
