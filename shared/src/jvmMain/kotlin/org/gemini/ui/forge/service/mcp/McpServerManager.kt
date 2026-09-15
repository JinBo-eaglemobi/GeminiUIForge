package org.gemini.ui.forge.service.mcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.utils.AppLogger

/**
 * JVM 桌面端 MCP 服务生命周期管理器（单例）
 *
 * 核心特性：
 * 1. 挂载 Streamable HTTP 协议端点 `/mcp`；
 * 2. 具备 Bearer Token 鉴权拦截器，防御未授权访问；
 * 3. CORS 严密收缩至本地环境，防御恶意网页跨站探测；
 * 4. 支持工具执行超时熔断控制；
 * 5. 全面支持返回 [TextContent] 与 [ImageContent] 多模态结果，支持工具进度通知。
 */
object McpServerManager {
    private var serverEngine: EmbeddedServer<*, *>? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _currentPort = MutableStateFlow(18330)
    val currentPort: StateFlow<Int> = _currentPort.asStateFlow()

    private var activeToken: String? = null
    private var lastActiveSessionId: String? = null
    private var lastActiveClientName: String? = null

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
                val startTime = org.gemini.ui.forge.getCurrentTimeMillis()
                val currentSessionId = lastActiveSessionId
                val currentClientName = lastActiveClientName
                val argsJsonStr = try {
                    org.gemini.ui.forge.utils.looseJson.encodeToString(JsonObject.serializer(), args)
                } catch (_: Exception) {
                    args.toString()
                }

                McpTrafficInspector.recordInbound(
                    methodOrTool = def.name,
                    summary = "客户端调用: ${def.name}",
                    payloadJson = argsJsonStr,
                    sessionId = currentSessionId,
                    clientName = currentClientName,
                    type = McpTrafficType.TOOL_CALL
                )

                try {
                    // 超时保护控制
                    val toolResult = withTimeout(def.timeoutMs) {
                        def.execute(args) { progress, message ->
                            // 进度上报日志
                            AppLogger.d("McpServer", "[${def.name}] 进度: ${(progress * 100).toInt()}% - $message")
                        }
                    }

                    val duration = org.gemini.ui.forge.getCurrentTimeMillis() - startTime
                    val resultSummary = if (toolResult.isError) "执行失败" else "执行成功 (${toolResult.content.size} 项产物)"

                    val resultJson = buildJsonObject {
                        put("isError", toolResult.isError)
                        put("contentCount", toolResult.content.size)
                        val textItems = toolResult.content.filterIsInstance<McpContent.Text>()
                        if (textItems.isNotEmpty()) {
                            put("textPayload", textItems.joinToString("\n") { it.text })
                        }
                        val imageItems = toolResult.content.filterIsInstance<McpContent.Image>()
                        if (imageItems.isNotEmpty()) {
                            put("imageCount", imageItems.size)
                            put("imageFormats", imageItems.joinToString { it.mimeType })
                        }
                    }.toString()

                    McpTrafficInspector.recordOutbound(
                        methodOrTool = def.name,
                        summary = "响应结果: $resultSummary",
                        payloadJson = resultJson,
                        durationMs = duration,
                        isError = toolResult.isError,
                        sessionId = currentSessionId,
                        clientName = currentClientName,
                        type = McpTrafficType.TOOL_RESULT
                    )

                    // 映射纯文本与 ImageContent 图文多模态数据
                    val sdkContents: List<ContentBlock> = toolResult.content.map { item ->
                        when (item) {
                            is McpContent.Text -> TextContent(text = item.text)
                            is McpContent.Image -> ImageContent(data = item.base64Data, mimeType = item.mimeType)
                        }
                    }

                    CallToolResult(
                        content = sdkContents,
                        isError = toolResult.isError
                    )
                } catch (e: Throwable) {
                    val duration = org.gemini.ui.forge.getCurrentTimeMillis() - startTime
                    McpTrafficInspector.recordOutbound(
                        methodOrTool = def.name,
                        summary = "执行异常: ${e.message}",
                        payloadJson = """{"error": "${e.message}"}""",
                        durationMs = duration,
                        isError = true,
                        sessionId = currentSessionId,
                        clientName = currentClientName,
                        type = McpTrafficType.ERROR
                    )

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
    fun start(host: String = "127.0.0.1", port: Int = 18330, token: String? = null): Boolean {
        if (_isRunning.value) {
            AppLogger.w("McpServer", "MCP 服务已在运行中，请先停止再启动")
            return true
        }

        activeToken = token?.trim()?.ifBlank { null }

        try {
            val engine = embeddedServer(CIO, host = host, port = port) {
                // 1. 安全 CORS 策略：仅收缩放行本地 Origin，防御恶意外部网站探测
                install(CORS) {
                    allowHost("localhost", schemes = listOf("http", "https"))
                    allowHost("127.0.0.1", schemes = listOf("http", "https"))
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("Mcp-Session-Id")
                    allowHeader("Mcp-Protocol-Version")
                }

                // 2. 客户端心跳与 Session 探针记录 (心跳静默保活，不入报文流水) + Bearer Token 鉴权
                intercept(ApplicationCallPipeline.Plugins) {
                    val sessionId = call.request.header("Mcp-Session-Id") ?: call.request.header("Session-Id") ?: "session_local"
                    val ua = call.request.header(HttpHeaders.UserAgent) ?: ""
                    val remoteHost = call.request.local.remoteHost
                    val resolvedName = McpTrafficInspector.recordClientHeartbeat(sessionId = sessionId, userAgent = ua, ip = remoteHost)
                    lastActiveSessionId = sessionId
                    lastActiveClientName = resolvedName

                    val expectedToken = activeToken
                    if (!expectedToken.isNullOrBlank()) {
                        // 预检 OPTIONS 请求不鉴权
                        if (call.request.httpMethod == HttpMethod.Options) {
                            return@intercept
                        }

                        val authHeader = call.request.header(HttpHeaders.Authorization)?.trim()
                        val isValid = authHeader == "Bearer $expectedToken" || authHeader == expectedToken
                        if (!isValid) {
                            AppLogger.w("McpServer", "⛔ 拒绝未授权的 MCP 访问 (Token 校验失败)")
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                "Unauthorized: Missing or invalid MCP Bearer token."
                            )
                            finish()
                        }
                    }
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
            AppLogger.i("McpServer", "🚀 MCP 服务已成功启动: $url (鉴权状态: ${if (activeToken != null) "已启用" else "无鉴权"})")
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
            activeToken = null
        }
    }
}
