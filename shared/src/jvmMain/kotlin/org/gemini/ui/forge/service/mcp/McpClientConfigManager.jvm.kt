package org.gemini.ui.forge.service.mcp

import kotlinx.serialization.json.*
import org.gemini.ui.forge.utils.AppLogger
import org.jetbrains.skiko.hostOs
import java.io.File

actual object McpClientConfigManager {

    private val userHome: String = System.getProperty("user.home")
    private val appData: String = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"

    private val prettyJson = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private fun resolveConfigFile(clientType: McpClientType): File {
        return when (clientType) {
            McpClientType.OPEN_CODE -> {
                val jsonc = File(userHome, ".config/opencode/opencode.jsonc")
                if (jsonc.exists()) jsonc else {
                    val json = File(userHome, ".config/opencode/opencode.json")
                    if (json.exists()) json else jsonc
                }
            }
            McpClientType.CLAUDE_CODE -> File(userHome, ".claude.json")
            McpClientType.CLAUDE_DESKTOP -> {
                when {
                    hostOs.isWindows -> File(appData, "Claude/claude_desktop_config.json")
                    hostOs.isMacOS -> File(userHome, "Library/Application Support/Claude/claude_desktop_config.json")
                    else -> File(userHome, ".config/Claude/claude_desktop_config.json")
                }
            }
            McpClientType.GEMINI_CLI -> File(userHome, ".gemini/settings.json")
            McpClientType.CURSOR -> File(userHome, ".cursor/mcp.json")
        }
    }

    actual fun getSupportedClientsStatus(serverUrl: String): List<ClientAppConfigStatus> {
        return McpClientType.entries.map { clientType ->
            val file = resolveConfigFile(clientType)
            val exists = file.exists()
            var isConfigured = false
            var configuredUrl: String? = null
            var isServiceDisabled = false

            if (exists) {
                try {
                    val text = file.readText()
                    if (clientType == McpClientType.OPEN_CODE) {
                        // OpenCode JSONC 处理
                        val urlMatch = Regex(""""gemini-ui-forge"\s*:\s*\{[^}]*"url"\s*:\s*"([^"]+)"""").find(text)
                        if (urlMatch != null) {
                            isConfigured = true
                            configuredUrl = urlMatch.groupValues[1]
                            // 探测是否声明了 "enabled": false
                            isServiceDisabled = Regex(""""gemini-ui-forge"\s*:\s*\{[^}]*"enabled"\s*:\s*false""").containsMatchIn(text)
                        }
                    } else {
                        // 标准 JSON 处理
                        val root = prettyJson.parseToJsonElement(text).jsonObject
                        val servers = root["mcpServers"]?.jsonObject
                        val ourServer = servers?.get("gemini-ui-forge")?.jsonObject
                        if (ourServer != null) {
                            isConfigured = true
                            configuredUrl = ourServer["url"]?.jsonPrimitive?.contentOrNull
                            // 探测是否声明了 "disabled": true
                            isServiceDisabled = ourServer["disabled"]?.jsonPrimitive?.booleanOrNull == true
                        }
                    }
                } catch (e: Throwable) {
                    AppLogger.w("McpConfig", "解析客户端配置文件异常: ${file.absolutePath}, ${e.message}")
                }
            }

            ClientAppConfigStatus(
                clientType = clientType,
                name = clientType.displayName,
                configPath = file.absolutePath,
                isFileExists = exists,
                isConfigured = isConfigured,
                configuredUrl = configuredUrl,
                isServiceDisabled = isServiceDisabled
            )
        }
    }

    actual fun toggleClientConfig(clientType: McpClientType, enable: Boolean, serverUrl: String): Boolean {
        val file = resolveConfigFile(clientType)
        return try {
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }

            if (clientType == McpClientType.OPEN_CODE) {
                toggleOpenCodeJsonc(file, enable, serverUrl)
            } else {
                toggleStandardJson(file, clientType, enable, serverUrl)
            }
        } catch (e: Throwable) {
            AppLogger.e("McpConfig", "切换客户端配置失败 [${clientType.displayName}]: ${e.message}", e)
            false
        }
    }

    /**
     * 对 OpenCode 的 opencode.jsonc 进行精准增删（严格保护文件内的注释和其他配置）
     */
    private fun toggleOpenCodeJsonc(file: File, enable: Boolean, serverUrl: String): Boolean {
        val originalText = if (file.exists()) file.readText() else "{\n  \"mcp\": {}\n}"
        val hasMcpKey = Regex(""""mcp"\s*:""").containsMatchIn(originalText)
        val hasOurConfig = Regex(""""gemini-ui-forge"\s*:""").containsMatchIn(originalText)

        val newText = if (enable) {
            if (hasOurConfig) {
                // 已有配置，仅更新 url
                originalText.replace(
                    Regex("""("gemini-ui-forge"\s*:\s*\{[^}]*"url"\s*:\s*")[^"]*(")"""),
                    "$1$serverUrl$2"
                )
            } else if (hasMcpKey) {
                // 有 "mcp": { 则在紧跟着的大括号后插入配置条目（OpenCode 官方规范：默认注入 "enabled": false 保持默认不启用）
                originalText.replaceFirst(
                    Regex("""("mcp"\s*:\s*\{)"""),
                    "$1\n    \"gemini-ui-forge\": {\n      \"type\": \"remote\",\n      \"url\": \"$serverUrl\",\n      \"enabled\": false\n    },"
                )
            } else {
                // 无 "mcp" 根对象，在外层根结构开头插入（默认注入 "enabled": false 保持默认不启用）
                originalText.replaceFirst(
                    Regex("""\{\s*"""),
                    "{\n  \"mcp\": {\n    \"gemini-ui-forge\": {\n      \"type\": \"remote\",\n      \"url\": \"$serverUrl\",\n      \"enabled\": false\n    }\n  },\n  "
                )
            }
        } else {
            if (!hasOurConfig) return true
            // 移除 gemini-ui-forge 配置块及可能伴生的逗号和多余空行
            var cleaned = originalText.replace(
                Regex("""[ \t]*"gemini-ui-forge"\s*:\s*\{[^}]*\},?[\r\n]*"""),
                ""
            )
            // 清除可能遗留的孤立尾部逗号例如: , \n }
            cleaned = cleaned.replace(Regex(""",\s*(\r?\n\s*\})"""), "$1")
            cleaned
        }

        file.writeText(newText)
        AppLogger.i("McpConfig", "✅ 已更新 OpenCode 配置: enable=$enable")
        return true
    }

    /**
     * 对标准 JSON（Claude Code, Claude Desktop, Gemini CLI, Cursor）进行精准增删改
     */
    private fun toggleStandardJson(file: File, clientType: McpClientType, enable: Boolean, serverUrl: String): Boolean {
        val originalContent = if (file.exists()) file.readText() else "{}"
        val rootMap = try {
            prettyJson.parseToJsonElement(originalContent).jsonObject.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf<String, JsonElement>()
        }

        val mcpServersMap = (rootMap["mcpServers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()

        if (enable) {
            mcpServersMap["gemini-ui-forge"] = buildJsonObject {
                put("url", serverUrl)
                // Cursor 官方标准规范：若支持禁用配置，默认注入 "disabled": true 保持默认不启用
                if (clientType == McpClientType.CURSOR) {
                    put("disabled", true)
                }
            }
            rootMap["mcpServers"] = JsonObject(mcpServersMap)
        } else {
            mcpServersMap.remove("gemini-ui-forge")
            if (mcpServersMap.isEmpty()) {
                rootMap.remove("mcpServers")
            } else {
                rootMap["mcpServers"] = JsonObject(mcpServersMap)
            }
        }

        val updatedContent = prettyJson.encodeToString(JsonObject(rootMap))
        file.writeText(updatedContent)
        AppLogger.i("McpConfig", "✅ 已更新客户端配置 [${file.name}]: enable=$enable")
        return true
    }
}
