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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.app.UpdateInfo
import org.gemini.ui.forge.model.api.*
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.DownloadProgress
import org.gemini.ui.forge.utils.DownloadResult
import org.gemini.ui.forge.utils.FileDownloader
import io.ktor.client.plugins.logging.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.skiko.hostOs

/**
 * 软件更新服务
 * 负责直接与 GitHub API 进行交互，处理版本检测和文件下载
 */
class UpdateService(private val currentVersion: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json(looseJson) {
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 
            connectTimeoutMillis = 15000
        }
        install(HttpRedirect) {
            checkHttpMethod = false
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    AppLogger.d("UpdateService_Http", message)
                }
            }
            level = LogLevel.INFO
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

            val rawLatestVersion = release.tagName.removePrefix("v")
            val latestVersion = rawLatestVersion.substringBefore("-")
            
            AppLogger.i("UpdateService", "📡 检查完成。远程最新版本: $latestVersion, 当前本地版本: $currentVersion")
            
            if (isNewer(latestVersion, currentVersion)) {
                val targetExt = when {
                    hostOs.isWindows -> "exe"
                    hostOs.isMacOS -> "dmg"
                    else -> "tar.gz" // 预留或其他系统
                }
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

    /**
     * 下载更新包并报告完整下载进度。
     * 内部委托通用 [FileDownloader]（多连接并行分块 + 分块级断点续传），
     * 对外输出 [DownloadProgress]（总量/速度/活跃连接数/分块明细，IDM 式视图数据源）。
     */
    fun downloadUpdate(url: String, targetPath: Path): Flow<DownloadProgress> = channelFlow {
        AppLogger.i("UpdateService", "📥 开始下载更新包: $url -> $targetPath")
        val downloader = FileDownloader()
        val targetDir = targetPath.parent?.toString() ?: "."
        val targetName = targetPath.name
        when (val result = downloader.download(url, targetDir, targetName) { p ->
            // 进度回调运行在 IO 线程，经 channelFlow 的非阻塞 trySend 转发完整快照
            trySend(p)
        }) {
            is DownloadResult.Success -> {
                AppLogger.i("UpdateService", "✅ 下载完成，文件已保存至本地。")
                close()
            }
            is DownloadResult.Failed -> {
                AppLogger.e("UpdateService", "❌ 下载更新失败: ${result.message}")
                close(IllegalStateException(result.message))
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * 比对版本号：latest 是否新于 current (基于标准的 SemVer 语义化版本规范)
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val latestClean = normalizeVersion(latest)
        val currentClean = normalizeVersion(current)
        
        if (latestClean == currentClean) return false

        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        
        return false
    }

    /**
     * 标准化版本号：只保留数字和点，移除连字符及后缀 (如 1.0.6-win -> 1.0.6)
     */
    private fun normalizeVersion(v: String): String {
        return v.substringBefore("-").filter { it.isDigit() || it == '.' }
    }
}
