package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import org.gemini.ui.forge.utils.AppLogger

/**
 * 全局共享的 HTTP 客户端。
 * Ktor 的 HttpClient 是线程安全的，建议在整个应用程序生命周期中重用同一个实例，
 * 避免频繁创建和销毁带来的巨大性能开销（连接池重建、线程池分配等）。
 */
object NetworkClient {
    val shared: HttpClient by lazy {
        HttpClient {
            install(HttpRequestRetry)
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000L // 5 minutes
                connectTimeoutMillis = 60_000L  // 1 minute
                socketTimeoutMillis = 300_000L  // 5 minutes
            }
            install(Logging) {
                level = LogLevel.INFO
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
