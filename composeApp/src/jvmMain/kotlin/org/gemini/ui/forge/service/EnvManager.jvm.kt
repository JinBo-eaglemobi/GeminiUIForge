package org.gemini.ui.forge.service

import java.io.File

actual class EnvManager {
    // 配置文件路径指向用户目录下的 .gemini/.env
    private val envFile = File(System.getProperty("user.home"), ".gemini/.env")

    init {
        if (!envFile.parentFile.exists()) {
            envFile.parentFile.mkdirs()
        }
        if (!envFile.exists()) {
            envFile.createNewFile()
        }
    }

    actual fun saveKey(keyName: String, keyValue: String) {
        val lines = if (envFile.exists()) envFile.readLines() else emptyList()
        val newLines = mutableListOf<String>()
        val targetEntry = "export $keyName=\"$keyValue\""
        var foundAndActivated = false

        // 第一步：处理现有的行
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 如果这一行是当前活跃的相同名称的 Key (但值不同)
            if (trimmedLine.startsWith("export $keyName=") && !trimmedLine.contains("\"$keyValue\"")) {
                // 注释掉它
                newLines.add("/$trimmedLine")
            } 
            // 如果这一行是已经被注释掉的、且内容完全匹配新 Key 的行
            else if (trimmedLine.startsWith("/export $keyName=") && trimmedLine.contains("\"$keyValue\"") && !foundAndActivated) {
                // 取消注释，激活它
                newLines.add(trimmedLine.removePrefix("/"))
                foundAndActivated = true
            }
            else {
                // 其他行原样保留
                newLines.add(line)
            }
        }

        // 第二步：如果没有找到可激活的旧行，则追加新行
        if (!foundAndActivated) {
            // 再次检查是否已经有活跃的完全一样的行在列表里了
            val alreadyActive = newLines.any { it.trim() == targetEntry }
            if (!alreadyActive) {
                newLines.add(targetEntry)
            }
        }

        envFile.writeText(newLines.joinToString("\n"))
    }

    actual fun loadKey(keyName: String): String? {
        if (!envFile.exists()) return null
        // 只读取未被注释 (不以 / 或 # 开头) 的活跃 export 行
        return envFile.readLines()
            .firstOrNull { 
                val trimmedLine = it.trim()
                trimmedLine.startsWith("export $keyName=") 
            }
            ?.substringAfter("=")
            ?.trim()
            ?.removeSurrounding("\"")
    }
}
