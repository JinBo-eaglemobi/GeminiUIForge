package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.utils.AppLogger

/**
 * 全局共享的 HTTP 客户端。
 * Ktor 的 HttpClient 是线程安全的，建议在整个应用程序生命周期中重用同一个实例，
 * 避免频繁创建和销毁带来的巨大性能开销（连接池重建、线程池分配等）。
 */
object NetworkClient {
    val shared: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(HttpRequestRetry)
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000L // 2 minutes for complete multi-modal analysis
                connectTimeoutMillis = 10_000L  // 10 seconds to fail-fast if no internet/proxy issue
                socketTimeoutMillis = 60_000L   // 60 seconds for streaming large JSON responses
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = object : Logger {
                    override fun log(message: String) {
                        // 统一输出网络请求日志
                        AppLogger.d("KtorNetwork", message)
                    }
                }
            }
        }
    }
}
