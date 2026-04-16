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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.app.UpdateInfo
import org.gemini.ui.forge.utils.AppLogger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import kotlinx.io.write
import kotlin.math.min

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L
)

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
            requestTimeoutMillis = 600000
            socketTimeoutMillis = 1800000
            connectTimeoutMillis = 30000
        }
    }

    private val GITHUB_REPO = "JinBo-eaglemobi/GeminiUIForge"

    /** 检查更新 */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.Default) {
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val response = client.get(url)
            if (response.status != HttpStatusCode.OK) {
                val errorMsg = response.bodyAsText()
                AppLogger.e("UpdateService", "❌ GitHub API 请求失败: url: $url ${response.status}. 响应内容: $errorMsg")
                return@withContext null
            }

            val release = response.body<GitHubRelease>()
            if (release.tagName.isBlank()) {
                AppLogger.e("UpdateService", "❌ 无法从响应中解析出有效的版本号 (tagName 为空)")
                return@withContext null
            }

            val latestVersion = release.tagName.removePrefix("v")
            
            if (isNewer(latestVersion, currentVersion)) {
                val osName = org.gemini.ui.forge.getPlatform().name.lowercase()
                val targetExt = if (osName.contains("java") || osName.contains("windows")) "exe" else "dmg"
                val asset = release.assets.find { it.name.endsWith(targetExt) } ?: release.assets.firstOrNull()
                
                if (asset != null) {
                    return@withContext UpdateInfo(
                        version = latestVersion,
                        releaseNotes = release.body ?: "",
                        downloadUrl = asset.downloadUrl,
                        fileName = asset.name,
                        publishDate = release.publishedAt
                    )
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.e("UpdateService", "❌ 检查更新过程中抛出异常", e)
            null
        }
    }

    /** 下载更新包并报告进度 (使用 kotlinx-io 跨平台写入，确保异步) */
    fun downloadUpdate(url: String, targetPath: Path): Flow<Float> = flow {
        try {
            val response = client.prepareGet(url).execute()
            val contentLength = response.contentLength() ?: 1L
            val channel = response.bodyAsChannel()
            
            val sink = SystemFileSystem.sink(targetPath).buffered()
            
            var bytesRead = 0L
            val buffer = ByteArray(16384)
            
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                bytesRead += read
                emit(bytesRead.toFloat() / contentLength)
            }
            sink.flush()
            sink.close()
        } catch (e: Exception) {
            AppLogger.e("UpdateService", "❌ 下载更新失败", e)
            throw e
        }
    }.flowOn(Dispatchers.Default)

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until min(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }
}
