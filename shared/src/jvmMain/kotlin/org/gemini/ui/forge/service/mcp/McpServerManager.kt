package org.gemini.ui.forge.service.mcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.utils.AppLogger

/**
 * JVM 桌面端 MCP 服务生命周期管理器（单例）
 */
object McpServerManager {
    private var serverEngine: EmbeddedServer<*, *>? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _currentPort = MutableStateFlow(18330)
    val currentPort: StateFlow<Int> = _currentPort.asStateFlow()

    fun createMcpServerInstance(): Server {
        val server = Server(
            serverInfo = Implementation(name = "gemini-ui-forge", version = ProjectConfig.VERSION),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )

        // 注册 McpToolRegistry 中的全部业务工具
        McpToolRegistry.tools.value.forEach { def ->
            val properties = def.inputSchema["properties"]?.let { it as? JsonObject } ?: JsonObject(emptyMap())
            val requiredList = (def.inputSchema["required"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            server.addTool(
                name = def.name,
                description = def.description,
                inputSchema = ToolSchema(
                    properties = properties,
                    required = requiredList
                )
            ) { request ->
                val args = request.arguments ?: JsonObject(emptyMap())
                try {
                    val resultString = def.execute(args)
                    CallToolResult(content = listOf(TextContent(resultString)))
                } catch (e: Throwable) {
                    AppLogger.e("McpServer", "Tool [${def.name}] 执行异常: ${e.message}", e)
                    CallToolResult(
                        content = listOf(TextContent("Error executing tool ${def.name}: ${e.message}")),
                        isError = true
                    )
                }
            }
        }

        return server
    }

    /**
     * 启动 MCP HTTP 服务
     */
    @Synchronized
    fun start(host: String = "127.0.0.1", port: Int = 18330): Boolean {
        if (_isRunning.value) {
            AppLogger.w("McpServer", "MCP 服务已在运行中，请先停止再启动")
            return true
        }

        try {
            val engine = embeddedServer(CIO, host = host, port = port) {
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("Mcp-Session-Id")
                    allowHeader("Mcp-Protocol-Version")
                }

                mcpStreamableHttp(path = "/mcp") {
                    createMcpServerInstance()
                }
            }

            engine.start(wait = false)
            serverEngine = engine
            _currentPort.value = port
            val url = "http://$host:$port/mcp"
            _serverUrl.value = url
            _isRunning.value = true
            AppLogger.i("McpServer", "🚀 MCP 服务已成功启动: $url")
            return true
        } catch (e: Throwable) {
            AppLogger.e("McpServer", "❌ MCP 服务启动失败: ${e.message}", e)
            _isRunning.value = false
            _serverUrl.value = null
            serverEngine = null
            return false
        }
    }

    /**
     * 停止 MCP HTTP 服务
     */
    @Synchronized
    fun stop() {
        try {
            serverEngine?.stop(1000, 2000)
            AppLogger.i("McpServer", "🛑 MCP 服务已停止")
        } catch (e: Throwable) {
            AppLogger.e("McpServer", "停止 MCP 服务时发生异常: ${e.message}", e)
        } finally {
            serverEngine = null
            _isRunning.value = false
            _serverUrl.value = null
        }
    }
}
