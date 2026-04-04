package org.gemini.ui.forge.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual open class ConfigManager {
    /**
     * JVM (桌面端) 平台的配置文件路径。
     * 将配置存储在用户的 Home 目录下的隐藏文件夹中（`~/.geminiuiforge/config.conf`），
     * 保证桌面应用的配置持久化且不会污染项目代码目录。
     */
    private val envFile = File(System.getProperty("user.home"), ".geminiuiforge/config.conf")

    init {
        // 确保父目录和文件存在
        if (!envFile.parentFile.exists()) {
            envFile.parentFile.mkdirs()
        }
        if (!envFile.exists()) {
            envFile.createNewFile()
        }
    }

    actual open suspend fun loadGlobalGeminiKey(): String? = withContext(Dispatchers.IO) {
        // 1. 优先尝试从系统环境变量中获取
        System.getProperty("GEMINI_API_KEY")?.let { return@withContext it }
        
        // 2. 退避策略：尝试读取 `~/.gemini/.env` 这个通用配置文件
        val globalEnv = File(System.getProperty("user.home"), ".gemini/.env")
        if (globalEnv.exists()) {
            globalEnv.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("export GEMINI_API_KEY=")) {
                    return@withContext trimmed.substringAfter("=").trim().removeSurrounding("\"")
                }
            }
        }
        return@withContext null
    }

    actual open suspend fun saveKey(keyName: String, keyValue: String) {
        withContext(Dispatchers.IO) {
            val lines = if (envFile.exists()) envFile.readLines() else emptyList()
            val newLines = mutableListOf<String>()
            val targetEntry = "export $keyName=\"$keyValue\""
            var found = false
            
            // 逐行检查，若发现存在相同 key，则替换该行
            for (line in lines) {
                if (line.trim().startsWith("export $keyName=")) {
                    newLines.add(targetEntry)
                    found = true
                } else {
                    newLines.add(line)
                }
            }
            // 若未找到对应的 key，则追加在末尾
            if (!found) newLines.add(targetEntry)
            envFile.writeText(newLines.joinToString("\n"))
        }
    }

    actual open suspend fun loadKey(keyName: String): String? = withContext(Dispatchers.IO) {
        if (!envFile.exists()) return@withContext null
        return@withContext envFile.readLines()
            .firstOrNull { it.trim().startsWith("export $keyName=") }
            ?.substringAfter("=")?.trim()?.removeSurrounding("\"")
    }
}