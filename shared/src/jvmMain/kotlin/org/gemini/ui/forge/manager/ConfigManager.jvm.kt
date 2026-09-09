package org.gemini.ui.forge.manager

import java.io.File
import java.lang.management.ManagementFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.utils.AppLogger

actual open class ConfigManager {
    /**
     * JVM (桌面端) 平台的配置文件路径。
     * 将配置存储在用户的 Home 目录下的隐藏文件夹中（`~/.geminiuiforge/config.conf`），
     * 保证桌面应用的配置持久化且不会污染项目代码目录。
     */
    private val envFile = File(org.gemini.ui.forge.userHomePath, ".geminiuiforge/config.conf")

    /**
     * JVM 专用启动参数配置文件路径。
     */
    private val vmOptionsFile = File(org.gemini.ui.forge.userHomePath, ".geminiuiforge/app.vmoptions")

    init {
        // 确保父目录和文件存在
        if (!envFile.parentFile.exists()) {
            envFile.parentFile.mkdirs()
        }
        if (!envFile.exists()) {
            envFile.createNewFile()
        }
        if (!vmOptionsFile.exists()) {
            // 动态获取当前运行的 JVM 参数，避免写死默认值
            val runtime = ManagementFactory.getRuntimeMXBean()
            val inputArgs = runtime.inputArguments
            
            val currentXmx = inputArgs.firstOrNull { it.startsWith("-Xmx") }
                ?: "-Xmx${formatMemorySize(Runtime.getRuntime().maxMemory())}"
            
            val currentXms = inputArgs.firstOrNull { it.startsWith("-Xms") }
                ?: "-Xms${formatMemorySize(Runtime.getRuntime().totalMemory())}"
                
            vmOptionsFile.writeText("$currentXmx\n$currentXms")
        }
    }

    /**
     * 将字节数转换为人类可读的 JVM 内存格式（如 2G, 512M）。
     */
    private fun formatMemorySize(bytes: Long): String {
        if (bytes <= 0) return "512M"
        val mb = bytes / (1024 * 1024)
        val gb = Math.round(mb.toDouble() / 1024).toInt()
        // 如果接近整数 GB (误差在 10% 以内)，返回 GB 格式，否则返回 MB
        return if (gb > 0 && Math.abs(gb.toLong() * 1024 - mb) < (gb.toLong() * 1024 * 0.1)) {
            "${gb}G"
        } else {
            "${mb}M"
        }
    }

    actual open suspend fun loadJvmXmx(): String = withContext(Dispatchers.IO) {
        if (!vmOptionsFile.exists()) return@withContext formatMemorySize(Runtime.getRuntime().maxMemory())
        val xmxLine = vmOptionsFile.readLines().firstOrNull { it.trim().startsWith("-Xmx") }
        return@withContext xmxLine?.removePrefix("-Xmx")?.trim() ?: formatMemorySize(Runtime.getRuntime().maxMemory())
    }

    actual open suspend fun saveJvmXmx(xmxValue: String) = withContext(Dispatchers.IO) {
        val lines = if (vmOptionsFile.exists()) vmOptionsFile.readLines() else emptyList()
        val newLines = mutableListOf<String>()
        val targetXmx = "-Xmx$xmxValue"
        var xmxFound = false
        
        for (line in lines) {
            if (line.trim().startsWith("-Xmx")) {
                newLines.add(targetXmx)
                xmxFound = true
            } else {
                newLines.add(line)
            }
        }
        if (!xmxFound) newLines.add(0, targetXmx)
        
        // 同时也尝试同步更新 -Xms (为 Xmx 的 1/4，但不超过 1G)
        val xmxNumeric = xmxValue.filter { it.isDigit() }.toLongOrNull() ?: 2L
        val unit = xmxValue.filter { it.isLetter() }.uppercase()
        val xmsValue = if (unit == "G") {
            if (xmxNumeric >= 4) "1G" else "512M"
        } else {
            "${xmxNumeric / 4}M"
        }
        
        if (!newLines.any { it.trim().startsWith("-Xms") }) {
            newLines.add("-Xms$xmsValue")
        }

        vmOptionsFile.writeText(newLines.joinToString("\n"))
    }

    actual open suspend fun loadGlobalGeminiKey(): String? = withContext(Dispatchers.IO) {
        // 1. 优先尝试从系统环境变量中获取 (macOS/Linux 用户最常用的方式)
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        
        // 2. 其次尝试从 JVM 启动属性获取
        System.getProperty("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        
        // 3. 退避策略：尝试读取 `~/.gemini/.env` 通用配置文件
        val globalEnv = File(org.gemini.ui.forge.userHomePath, ".gemini/.env")
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