package org.gemini.ui.forge.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.gemini.ui.forge.model.app.RemoteSourceMetadata
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/**
 * 环境依赖安装源多节点探测器与 10 秒链接测速择优工具
 *
 * 负责维护国内外（国内高速镜像 + 国际/海外官方与知名开源镜像）多区域候选下载源矩阵，
 * 在发起实际下载前输出候选源清单，并在 10 秒预算窗口内并发探测各节点的真实连接与首包响应耗时，
 * 自动按响应速度从快到慢排序，锁定当前网络环境下访问最快的下载源。
 */
object DownloadSourceSpeedTester {

    private const val TAG = "DownloadSourceSpeedTester"

    /** 默认链接测速总时间窗口（10 秒） */
    const val DEFAULT_SPEED_TEST_WINDOW_MS = 10_000L

    /** 测速窗口最小物理夹具（3 秒） */
    private const val MIN_SPEED_TEST_WINDOW_MS = 3_000L

    /** 测速窗口最大物理夹具（30 秒） */
    private const val MAX_SPEED_TEST_WINDOW_MS = 30_000L

    /** Python 3.12.8 官方下载发布页 */
    const val PYTHON_OFFICIAL_DOWNLOAD_URL = "https://www.python.org/downloads/release/python-3128/"

    /** WinGet (Windows 包管理器) 官方下载发布页 */
    const val WINGET_OFFICIAL_DOWNLOAD_URL = "https://github.com/microsoft/winget-cli/releases"

    /**
     * WinGet (Microsoft.DesktopAppInstaller) Windows 安装包候选下载源矩阵（涵盖国内高速镜像与国际/海外官方源）
     */
    val WINGET_INSTALLER_SOURCES: List<Pair<String, String>> = listOf(
        "[国内] GitHub 高速镜像 (GHFast Mirror)" to "https://ghfast.top/https://github.com/microsoft/winget-cli/releases/latest/download/Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle",
        "[国内] GitHub 代理镜像 (GHProxy Mirror)" to "https://gh-proxy.com/https://github.com/microsoft/winget-cli/releases/latest/download/Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle",
        "[国际/海外] 微软官方全球直链 (Microsoft aka.ms CDN)" to "https://aka.ms/getwinget",
        "[国际/海外] GitHub 官方全球主源 (GitHub Releases CDN)" to "https://github.com/microsoft/winget-cli/releases/latest/download/Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle"
    )

    /**
     * WinGet 官方完整前置运行库依赖包 (DesktopAppInstaller_Dependencies.zip) 候选下载源矩阵
     * 内部包含 WinGet 运行所必需的 Microsoft.VCLibs.140.00、Microsoft.VCLibs.140.00.UWPDesktop、Microsoft.WindowsAppRuntime.1.8
     */
    val WINGET_DEPENDENCIES_SOURCES: List<Pair<String, String>> = listOf(
        "[国内] GitHub 代理镜像 (GHProxy Mirror)" to "https://gh-proxy.com/https://github.com/microsoft/winget-cli/releases/latest/download/DesktopAppInstaller_Dependencies.zip",
        "[国内] GitHub 高速镜像 (GHFast Mirror)" to "https://ghfast.top/https://github.com/microsoft/winget-cli/releases/latest/download/DesktopAppInstaller_Dependencies.zip",
        "[国际/海外] GitHub 官方全球主源 (GitHub Releases CDN)" to "https://github.com/microsoft/winget-cli/releases/latest/download/DesktopAppInstaller_Dependencies.zip"
    )

    /**
     * 本地环境安装包持久化下载与缓存目录：~/.geminiuiforge/downloads
     */
    val ENV_DOWNLOADS_DIR: File by lazy {
        File(System.getProperty("user.home"), ".geminiuiforge/downloads").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * 探测本地下载目录中是否已存在有效的缓存安装包（对标 pip "Using cached ..."）
     */
    fun getCachedDownloadFile(fileName: String, expectedMinSizeBytes: Long = 1024L): File? {
        val file = File(ENV_DOWNLOADS_DIR, fileName)
        return if (file.exists() && file.isFile && file.length() >= expectedMinSizeBytes) {
            file
        } else {
            null
        }
    }

    /**
     * Python 3.12.8 Windows 64位安装包候选下载源矩阵（涵盖国内高速镜像与国际/海外镜像）
     */
    val PYTHON_WIN64_INSTALLER_SOURCES: List<Pair<String, String>> = listOf(
        "[国内] 华为云开源镜像 (Huawei Cloud)" to "https://mirrors.huaweicloud.com/python/3.12.8/python-3.12.8-amd64.exe",
        "[国内] 阿里 npmmirror 镜像 (Aliyun)" to "https://registry.npmmirror.com/-/binary/python/3.12.8/python-3.12.8-amd64.exe",
        "[国内] 南京大学开源镜像 (NJU Mirror)" to "https://mirror.nju.edu.cn/python/3.12.8/python-3.12.8-amd64.exe",
        "[国际/海外] Python 官方全球主源 (Fastly Global CDN)" to "https://www.python.org/ftp/python/3.12.8/python-3.12.8-amd64.exe",
        "[国际/海外] 韩国 Kakao 亚太国际镜像 (Kakao Corp, KR)" to "https://mirror.kakao.com/python/3.12.8/python-3.12.8-amd64.exe",
        "[国际/海外] Python 官方全球备用镜像 (Legacy Python.org)" to "https://legacy.python.org/ftp/python/3.12.8/python-3.12.8-amd64.exe",
        "[国际/海外] 美国俄勒冈州立大学开源镜像 (OSUOSL, US)" to "https://ftp.osuosl.org/pub/python/3.12.8/python-3.12.8-amd64.exe",
        "[国际/海外] 欧洲瑞典乌默奥大学开源镜像 (ACC UMU, EU)" to "https://ftp.acc.umu.se/mirror/python.org/ftp/python/3.12.8/python-3.12.8-amd64.exe"
    )

    /**
     * PyPI 第三方依赖包候选索引源矩阵（涵盖国内高速镜像与国际/海外镜像）
     */
    val PYPI_INDEX_SOURCES: List<Pair<String, String>> = listOf(
        "[国内] 清华大学 TUNA 镜像" to "https://pypi.tuna.tsinghua.edu.cn/simple",
        "[国内] 阿里云 PyPI 镜像" to "https://mirrors.aliyun.com/pypi/simple/",
        "[国内] 腾讯云 PyPI 镜像" to "https://mirrors.cloud.tencent.com/pypi/simple",
        "[国内] 中国科学技术大学 USTC 镜像" to "https://pypi.mirrors.ustc.edu.cn/simple/",
        "[国际/海外] PyPI 官方全球主源 (Fastly Global CDN)" to "https://pypi.org/simple",
        "[国际/海外] 韩国 Kakao 亚太国际 PyPI 镜像 (Kakao, KR)" to "https://mirror.kakao.com/pypi/simple",
        "[国际/海外] Python.org 经典官方索引源 (Python.org Index)" to "https://pypi.python.org/simple"
    )

    /**
     * 根据环境依赖项名称获取对应的官方下载页面地址
     */
    fun getOfficialDownloadUrl(itemName: String): String {
        return when (itemName.lowercase(Locale.ROOT)) {
            "winget" -> WINGET_OFFICIAL_DOWNLOAD_URL
            "python" -> PYTHON_OFFICIAL_DOWNLOAD_URL
            "pillow" -> "https://pypi.org/project/pillow/"
            "rembg" -> "https://pypi.org/project/rembg/"
            "onnxruntime" -> "https://pypi.org/project/onnxruntime/"
            else -> "https://pypi.org/project/$itemName/"
        }
    }

    /**
     * 列出探测到的全部候选下载源，执行 10 秒链接测速，并返回按响应速度从快到慢排序的可用下载源列表
     *
     * @param sources 候选下载源列表 (Pair<源显示名称, 完整URL>)
     * @param testWindowMillis 链接测速总预算时间窗口（默认 10,000 ms，即 10 秒）
     * @param onLog 实时日志输出回调
     * @return 按实测响应耗时从快到慢排序的可达下载源列表（若全部超时则回退原列表）
     */
    suspend fun probeAndRankSources(
        sources: List<Pair<String, String>>,
        testWindowMillis: Long = DEFAULT_SPEED_TEST_WINDOW_MS,
        onLog: suspend (String) -> Unit
    ): List<Pair<String, String>> {
        if (sources.isEmpty()) return emptyList()

        val safeWindowMs = testWindowMillis.coerceIn(MIN_SPEED_TEST_WINDOW_MS, MAX_SPEED_TEST_WINDOW_MS)
        val windowSeconds = safeWindowMs / 1000

        // 1. 列出探测到的所有候选下载源
        val headerLine = "📋 已探测到 ${sources.size} 个候选下载源（国内 + 国际/海外）:"
        AppLogger.i(TAG, headerLine)
        onLog(headerLine)
        sources.forEachIndexed { index, (name, url) ->
            val sourceLine = "   ${index + 1}. $name -> $url"
            AppLogger.i(TAG, sourceLine)
            onLog(sourceLine)
        }

        // 2. 启动 10 秒链接测速
        val startProbeMsg = "⏱️ 正在进行 ${windowSeconds} 秒链接测试（并发测试各节点连接响应速度）..."
        AppLogger.i(TAG, startProbeMsg)
        onLog(startProbeMsg)

        val singleTimeoutMs = (safeWindowMs - 500L).coerceAtLeast(2_000L).toInt()

        // Triple<Pair<Name, Url>, LatencyMs, HttpStatusOrError>
        val probeResults: List<Triple<Pair<String, String>, Long, String>> = withContext(Dispatchers.IO) {
            coroutineScope {
                val jobs = sources.map { source ->
                    async {
                        val result = withTimeoutOrNull(safeWindowMs) {
                            measureSingleSourceLatency(source.second, singleTimeoutMs)
                        }
                        if (result != null) {
                            Triple(source, result.first, result.second)
                        } else {
                            Triple(source, Long.MAX_VALUE, "超时 (> ${windowSeconds}s)")
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        // 3. 按响应耗时从快到慢排序并输出每个源的测速报告
        val sortedResults = probeResults.sortedBy { it.second }
        sortedResults.forEach { (source, latencyMs, detail) ->
            val (name, _) = source
            if (latencyMs < Long.MAX_VALUE) {
                val msg = "   ✅ [${latencyMs} ms] $name ($detail)"
                AppLogger.i(TAG, msg)
                onLog(msg)
            } else {
                val msg = "   ❌ [不可达] $name ($detail)"
                AppLogger.i(TAG, msg)
                onLog(msg)
            }
        }

        val reachableSources = sortedResults
            .filter { it.second < Long.MAX_VALUE }
            .map { it.first }

        return if (reachableSources.isNotEmpty()) {
            val best = sortedResults.first()
            val winnerMsg = "🏆 ${windowSeconds}秒链接测速完成！最快下载源: ${best.first.first} (响应耗时 ${best.second} ms)"
            val winnerUrlMsg = "🔗 已锁定首选高速下载链接: ${best.first.second}"
            AppLogger.i(TAG, winnerMsg)
            AppLogger.i(TAG, winnerUrlMsg)
            onLog(winnerMsg)
            onLog(winnerUrlMsg)
            reachableSources
        } else {
            val fallbackMsg = "⚠️ 链接测速均未在 ${windowSeconds} 秒内响应，将按默认顺序尝试下载..."
            AppLogger.w(TAG, fallbackMsg)
            onLog(fallbackMsg)
            sources
        }
    }

    /**
     * 对指定候选源发起快速探测，获取远程目标文件的权威物理大小（字节数）与断点续传支持状态
     */
    suspend fun probeRemoteMetadata(
        sources: List<Pair<String, String>>,
        timeoutMs: Int = 5_000
    ): RemoteSourceMetadata? = withContext(Dispatchers.IO) {
        for ((label, urlStr) in sources) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
                    setRequestProperty("Accept", "*/*")
                }
                conn.connect()
                var code = conn.responseCode
                var length = conn.contentLengthLong
                val acceptRanges = conn.getHeaderField("Accept-Ranges")
                var supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true

                if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_FORBIDDEN || length <= 0L) {
                    conn.disconnect()
                    // 降级为 GET Range 探测真实 Content-Range
                    conn = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
                        setRequestProperty("Range", "bytes=0-0")
                    }
                    conn.connect()
                    code = conn.responseCode
                    if (code == 206) {
                        supportsRange = true
                        val contentRange = conn.getHeaderField("Content-Range")
                        if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                            val totalStr = contentRange.substringAfterLast("/").trim()
                            length = totalStr.toLongOrNull() ?: -1L
                        }
                    } else if (code in 200..299) {
                        length = conn.contentLengthLong
                    }
                }

                if (code in 200..399 && length > 0L) {
                    AppLogger.i(TAG, "[$label] 成功探测到远程文件权威元数据: 大小 $length 字节, 支持Range: $supportsRange")
                    return@withContext RemoteSourceMetadata(
                        sourceLabel = label,
                        sourceUrl = urlStr,
                        contentLength = length,
                        supportsRange = supportsRange
                    )
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "[$label] 探测元数据跳过: ${e.message}")
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }
        null
    }

    /**
     * 测量单个 URL 的 HTTP 握手与首包响应延迟（毫秒）
     */
    private fun measureSingleSourceLatency(
        url: String,
        timeoutMs: Int
    ): Pair<Long, String> {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
                setRequestProperty("Accept", "*/*")
            }
            connection.connect()
            var code = connection.responseCode

            // 部分镜像对 HEAD 返回 405 Method Not Allowed，降级使用带 Range 的轻量 GET 探测
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_FORBIDDEN) {
                connection.disconnect()
                connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
                    setRequestProperty("Range", "bytes=0-1023")
                }
                connection.connect()
                code = connection.responseCode
            }

            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            if (code in 200..399) {
                elapsed to "HTTP $code"
            } else {
                Long.MAX_VALUE to "HTTP $code"
            }
        } catch (e: Exception) {
            val reason = e.javaClass.simpleName.removeSuffix("Exception")
            Long.MAX_VALUE to "连接失败: $reason"
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 格式化实时下载速度（自适应 MB/s 与 KB/s）
     */
    fun formatDownloadSpeed(bytesPerSecond: Double): String {
        if (bytesPerSecond <= 0.0 || bytesPerSecond.isNaN() || bytesPerSecond.isInfinite()) {
            return "0.0 KB/s"
        }
        val mbPerSec = bytesPerSecond / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mbPerSec)
        } else {
            val kbPerSec = bytesPerSecond / 1024.0
            String.format(Locale.US, "%.1f KB/s", kbPerSec)
        }
    }
}
