package org.gemini.ui.forge.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.app.UpdateInfo
import org.gemini.ui.forge.model.api.*
import org.gemini.ui.forge.utils.AppLogger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import kotlinx.io.write
import kotlin.math.min

/**
 * 软件更新服务
 * 负责直接与 GitHub API 进行交互，处理版本检测和文件下载
 */
class UpdateService(private val currentVersion: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
        }
    }

    private val GITHUB_REPO = "JinBo-eaglemobi/GeminiUIForge"

    /** 检查更新 */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.Default) {
        AppLogger.i("UpdateService", "🔍 正在从 GitHub 检查更新: $GITHUB_REPO ...")
        try {
            val response = client.get("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            
            if (response.status != HttpStatusCode.OK) {
                val errorMsg = response.bodyAsText()
                AppLogger.e("UpdateService", "❌ 获取更新失败 [HTTP ${response.status.value}]. 响应内容: $errorMsg")
                return@withContext null
            }

            val release = response.body<GitHubRelease>()
            if (release.tagName.isBlank()) {
                AppLogger.e("UpdateService", "❌ API 响应解析异常：未找到版本标签 (tagName)")
                return@withContext null
            }

            val latestVersion = release.tagName.removePrefix("v")
            AppLogger.i("UpdateService", "📡 检查完成。远程最新版本: $latestVersion, 当前本地版本: $currentVersion")
            
            if (isNewer(latestVersion, currentVersion)) {
                val osName = org.gemini.ui.forge.getPlatform().name.lowercase()
                val targetExt = if (osName.contains("java") || osName.contains("windows")) "exe" else "dmg"
                val asset = release.assets.find { it.name.endsWith(targetExt) } ?: release.assets.firstOrNull()
                
                if (asset != null) {
                    AppLogger.i("UpdateService", "✨ 发现新版本！准备下载资产: ${asset.name} (${(asset.size / 1024 / 1024)} MB)")
                    return@withContext UpdateInfo(
                        version = latestVersion,
                        releaseNotes = release.body ?: "无更新日志",
                        downloadUrl = asset.downloadUrl,
                        fileName = asset.name,
                        publishDate = release.publishedAt
                    )
                } else {
                    AppLogger.e("UpdateService", "⚠️ 发现新版本但未找到匹配当前平台的下载资产。")
                }
            } else {
                AppLogger.i("UpdateService", "✅ 当前已是最新版本，无需更新。")
            }
            null
        } catch (e: Exception) {
            AppLogger.e("UpdateService", "❌ 检查更新过程中抛出异常: ${e.message}", e)
            null
        }
    }

    /** 下载更新包并报告进度 (使用 kotlinx-io 跨平台写入，确保异步) */
    fun downloadUpdate(url: String, targetPath: Path): Flow<Float> = flow {
        AppLogger.i("UpdateService", "📥 开始下载更新包: $url -> $targetPath")
        try {
            val response = client.prepareGet(url).execute()
            val contentLength = response.contentLength() ?: 1L
            val channel = response.bodyAsChannel()
            
            val sink = SystemFileSystem.sink(targetPath).buffered()
            
            var bytesRead = 0L
            val buffer = ByteArray(16384)
            var lastReportedProgress = 0
            
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                bytesRead += read
                
                val progress = (bytesRead.toFloat() / contentLength)
                emit(progress)

                val currentProgressPct = (progress * 100).toInt()
                if (currentProgressPct >= lastReportedProgress + 10) {
                    AppLogger.i("UpdateService", "🚀 下载进度: $currentProgressPct% (${bytesRead / 1024} KB)")
                    lastReportedProgress = (currentProgressPct / 10) * 10
                }
            }
            sink.flush()
            sink.close()
            AppLogger.i("UpdateService", "✅ 下载完成，文件已保存至本地。")
        } catch (e: Exception) {
            AppLogger.e("UpdateService", "❌ 下载更新失败: ${e.message}", e)
            throw e
        }
    }.flowOn(Dispatchers.Default)

    /**
     * 比对版本号：latest 是否新于 current
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val latestClean = normalizeVersion(latest)
        val currentClean = normalizeVersion(current)
        
        if (latestClean == currentClean) return false

        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until min(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        
        // 如果前缀部分完全相同，则更长的版本号被视为更新 (如 1.0.1 > 1.0)
        return latestParts.size > currentParts.size
    }

    /**
     * 标准化版本号：只保留数字和点，移除后缀 (如 1.0.0-win -> 1.0.0)
     */
    private fun normalizeVersion(v: String): String {
        return v.split("-").first().filter { it.isDigit() || it == '.' }
    }
}
