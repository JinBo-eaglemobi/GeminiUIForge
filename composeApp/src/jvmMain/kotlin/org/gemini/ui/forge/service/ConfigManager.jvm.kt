package org.gemini.ui.forge.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.utils.AppLogger

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
        // 1. 优先尝试从系统环境变量中获取 (macOS/Linux 用户最常用的方式)
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        
        // 2. 其次尝试从 JVM 启动属性获取
        System.getProperty("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        
        // 3. 退避策略：尝试读取 `~/.gemini/.env` 通用配置文件
        val globalEnv = File(System.getProperty("user.home"), ".gemini/.env")
        if (globalEnv.exists()) {
            try {
                globalEnv.readLines().forEach { line ->
                    val trimmed = line.trim()
                    // 忽略空行、以 # 开头、或以 / (包含 //) 开头的注释行
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("/")) return@forEach
                    
                    // 严格匹配键名，防止匹配到行内包含但非定义的字符串
                    val isMatch = trimmed.startsWith("GEMINI_API_KEY=") || 
                                 trimmed.startsWith("export GEMINI_API_KEY=") ||
                                 trimmed.startsWith("set GEMINI_API_KEY=")
                    
                    if (isMatch) {
                        var value = trimmed.substringAfter("=").trim()
                        
                        // 处理行尾注释：剔除第一个 # 或 / 之后的内容
                        if (value.contains("#")) value = value.substringBefore("#").trim()
                        if (value.contains("/")) value = value.substringBefore("/").trim()
                        
                        // 脱掉引号外壳
                        val finalValue = value.removeSurrounding("\"").removeSurrounding("'")
                        if (finalValue.isNotBlank()) return@withContext finalValue
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("ConfigManager", "读取全局 .env 失败", e)
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