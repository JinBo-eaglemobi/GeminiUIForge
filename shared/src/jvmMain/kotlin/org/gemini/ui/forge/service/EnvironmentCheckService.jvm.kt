package org.gemini.ui.forge.service

import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.gemini.ui.forge.model.app.EnvironmentItemStatus
import org.gemini.ui.forge.model.app.FullEnvironmentStatus
import org.gemini.ui.forge.model.app.PipPackageInfo
import org.gemini.ui.forge.model.app.PipPackageJson
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.DownloadSourceSpeedTester
import org.gemini.ui.forge.utils.PythonEnvResolver
import org.jetbrains.skiko.hostOs
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.zip.ZipFile

class JvmEnvironmentCheckService : EnvironmentCheckService {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "JvmEnvironmentCheckService"
        private const val PYTHON_WIN_VERSION = "3.12.8"
    }

    // 推荐的包列表 (未安装时显示在推荐中)
    private val recommendedPackages = listOf(
        "opencv-python" to "开源计算机视觉库，用于图像处理",
        "matplotlib" to "强大的 2D 绘图与数据可视化库",
        "torch" to "PyTorch 深度学习框架",
        "numpy" to "高性能的科学计算与数组处理库",
        "requests" to "简洁易用的 HTTP 网络请求库",
        "fastapi" to "现代、快速的 Web 框架",
        "uvicorn" to "用于运行 FastAPI 的 ASGI 服务器",
        "pydantic" to "数据验证与设置管理库",
        "beautifulsoup4" to "用于解析 HTML 和 XML 的爬虫库",
        "tqdm" to "快速、可扩展的进度条库",
        "scikit-image" to "基于 SciPy 的图像处理算法库",
        "diffusers" to "HuggingFace 扩散模型库",
        "transformers" to "HuggingFace 预训练模型库",
        "moviepy" to "视频编辑与处理库",
        "imageio" to "读取和写入图像数据的通用库",
        "pillow" to "强大的 Python 图像处理库 (PIL fork)",
        "rembg" to "AI 自动移除图片背景库",
        "onnxruntime" to "ONNX 模型的跨平台推理引擎",
        "openai" to "OpenAI 官方 API 客户端"
    )

    override suspend fun checkAll(): FullEnvironmentStatus = withContext(Dispatchers.IO) {
        val items = mutableListOf<EnvironmentItemStatus>()

        // 1. Check Python (支持 PATH 与传统系统标准安装目录免重启寻址)
        val pythonVer = PythonEnvResolver.getPythonVersion()
        val isPythonReady = pythonVer != null
        items.add(
            EnvironmentItemStatus(
                name = "python",
                labelRes = Res.string.env_item_python,
                isInstalled = isPythonReady,
                version = pythonVer,
                officialUrl = DownloadSourceSpeedTester.getOfficialDownloadUrl("python")
            )
        )

        val pipBase = if (isPythonReady) PythonEnvResolver.resolvePipCommand() else emptyList()

        // 2. Check Rembg
        val rembgInfo = if (isPythonReady) PythonEnvResolver.runQuickCommand(pipBase + listOf("show", "rembg"), 10L) else null
        items.add(
            EnvironmentItemStatus(
                name = "rembg",
                labelRes = Res.string.env_item_rembg,
                isInstalled = rembgInfo != null,
                version = extractVersion(rembgInfo),
                officialUrl = DownloadSourceSpeedTester.getOfficialDownloadUrl("rembg")
            )
        )

        // 3. Check Pillow
        val pillowInfo = if (isPythonReady) PythonEnvResolver.runQuickCommand(pipBase + listOf("show", "pillow"), 10L) else null
        items.add(
            EnvironmentItemStatus(
                name = "pillow",
                labelRes = Res.string.env_item_pillow,
                isInstalled = pillowInfo != null,
                version = extractVersion(pillowInfo),
                officialUrl = DownloadSourceSpeedTester.getOfficialDownloadUrl("pillow")
            )
        )

        // 4. Check ONNX Runtime
        val onnxInfo = if (isPythonReady) PythonEnvResolver.runQuickCommand(pipBase + listOf("show", "onnxruntime"), 10L) else null
        items.add(
            EnvironmentItemStatus(
                name = "onnxruntime",
                labelRes = Res.string.env_item_onnxruntime,
                isInstalled = onnxInfo != null,
                version = extractVersion(onnxInfo),
                officialUrl = DownloadSourceSpeedTester.getOfficialDownloadUrl("onnxruntime")
            )
        )

        FullEnvironmentStatus(items = items, isChecking = false)
    }

    override fun installItem(name: String): Flow<String> = flow {
        val officialUrl = DownloadSourceSpeedTester.getOfficialDownloadUrl(name)
        val officialMsg = "🌐 官方下载地址: $officialUrl （点击上方「官方下载页」按钮可直接用默认浏览器访问）"
        AppLogger.i(TAG, officialMsg)
        emit(officialMsg)

        if (name == "python") {
            installPythonFlow()
            return@flow
        }

        // 安装 Pip 子依赖前先确认 Python 可用
        val pythonExe = PythonEnvResolver.resolvePythonExecutable()
        if (pythonExe == null) {
            val msg = "❌ 未检测到可用的 Python 3 环境，请先点击安装 Python 后再安装 $name"
            AppLogger.e(TAG, msg)
            emit(msg)
            return@flow
        }

        if (name !in listOf("rembg", "pillow", "onnxruntime")) {
            return@flow
        }

        // 列出探测到的国内外 PyPI 镜像源并进行 10 秒链接测速，优先使用最快源
        val rankedPypiSources = DownloadSourceSpeedTester.probeAndRankSources(
            sources = DownloadSourceSpeedTester.PYPI_INDEX_SOURCES
        ) { logLine ->
            emit(logLine)
        }

        val pipBase = PythonEnvResolver.resolvePipCommand()
        val candidateSources = rankedPypiSources.take(3).ifEmpty { DownloadSourceSpeedTester.PYPI_INDEX_SOURCES.take(1) }
        var installedOk = false

        for ((index, source) in candidateSources.withIndex()) {
            val (sourceName, sourceUrl) = source
            val host = runCatching { URI(sourceUrl).host }.getOrNull()
            val installMsg = "🚀 正在使用最优源 [$sourceName] 安装 $name..."
            AppLogger.i(TAG, "$installMsg ($sourceUrl)")
            emit(installMsg)

            val commandList = buildList {
                addAll(pipBase)
                add("install")
                add("--upgrade")
                add(name)
                add("-i")
                add(sourceUrl)
                if (!host.isNullOrBlank()) {
                    add("--trusted-host")
                    add(host)
                }
            }

            installedOk = runProcessAndEmitLogs(name, commandList)
            if (installedOk) {
                val okMsg = "✅ $name 安装完成！(使用源: $sourceName)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
                break
            } else if (index < candidateSources.lastIndex) {
                val retryMsg = "⚠️ 从 [$sourceName] 安装未成功，正在自动切换至次优源重试..."
                AppLogger.w(TAG, retryMsg)
                emit(retryMsg)
            }
        }

        if (!installedOk) {
            val errMsg = "❌ $name 安装失败，请检查网络连接或查看上方详细日志"
            AppLogger.e(TAG, errMsg)
            emit(errMsg)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 跨平台 Python 3 自动安装流水线：
     * - Windows: 先检测是否已安装 winget，若未安装则首先自动安装 winget；随后列出探测到的国内外候选下载源并执行 10 秒链接测速，择优下载或通过 winget 安装
     * - macOS: 优先探测 Homebrew 自动安装；若无 Homebrew 则打开官方下载页
     */
    private suspend fun FlowCollector<String>.installPythonFlow() {
        if (hostOs.isWindows) {
            var wingetExe = PythonEnvResolver.resolveWingetExecutable()
            if (wingetExe == null) {
                wingetExe = ensureWingetInstalledFlow()
            } else {
                val msg = "✅ 检测到系统已安装 WinGet ($wingetExe)"
                AppLogger.i(TAG, msg)
                emit(msg)
            }
            emit("🔍 正在探测 Python $PYTHON_WIN_VERSION 可用下载源并准备安装...")
            installPythonOnWindowsViaDirectInstaller(wingetExe)
        } else if (hostOs.isMacOS) {
            val brewExe = PythonEnvResolver.resolveBrewExecutable()
            if (brewExe != null) {
                emit("🍺 检测到 macOS Homebrew ($brewExe)，正在自动安装 Python 3...")
                val ok = runProcessAndEmitLogs("python", listOf(brewExe, "install", "python"))
                if (ok && PythonEnvResolver.resolvePythonExecutable() != null) {
                    val ver = PythonEnvResolver.getPythonVersion() ?: "3.x"
                    AppLogger.i(TAG, "✅ python 通过 Homebrew 安装完成 ($ver)")
                    emit("✅ python 安装完成！(版本: $ver)")
                } else {
                    emit("❌ Homebrew 安装 Python 失败，请检查终端权限或网络状态")
                }
            } else {
                emit("🌐 未检测到 Homebrew，已为您打开 Python 官方下载页。")
                withContext(Dispatchers.Main) {
                    org.gemini.ui.forge.getPlatform().openInBrowser("https://www.python.org/downloads/macos/")
                }
            }
        } else {
            emit("🌐 暂不支持此平台的自动安装，已为您打开 Python 官网。")
            withContext(Dispatchers.Main) {
                org.gemini.ui.forge.getPlatform().openInBrowser("https://www.python.org/downloads/")
            }
        }
    }

    /**
     * 检测并在缺失时自动下载、测速并安装 Windows WinGet (Microsoft.DesktopAppInstaller)
     * 支持 PowerShell Add-AppxPackage 标准安装及内置 Administrator 账户下独立解压部署兜底
     */
    private suspend fun FlowCollector<String>.ensureWingetInstalledFlow(): String? {
        PythonEnvResolver.resolveWingetExecutable()?.let { return it }

        val noticeMsg = "📦 检测到当前系统未安装 WinGet (Windows 包管理器)，正在首先自动安装 WinGet..."
        val officialMsg = "🌐 WinGet 官方下载地址: ${DownloadSourceSpeedTester.WINGET_OFFICIAL_DOWNLOAD_URL}"
        AppLogger.i(TAG, noticeMsg)
        AppLogger.i(TAG, officialMsg)
        emit(noticeMsg)
        emit(officialMsg)

        val tempDir = File(System.getProperty("java.io.tmpdir"), "geminiuiforge_env")
        if (!tempDir.exists()) tempDir.mkdirs()
        val bundleFile = File(tempDir, "Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle")

        try {
            val rankedSources = DownloadSourceSpeedTester.probeAndRankSources(
                sources = DownloadSourceSpeedTester.WINGET_INSTALLER_SOURCES
            ) { logLine ->
                emit(logLine)
            }

            var downloadSuccess = false
            val candidateSources = rankedSources.take(3).ifEmpty { DownloadSourceSpeedTester.WINGET_INSTALLER_SOURCES.take(2) }

            for ((index, source) in candidateSources.withIndex()) {
                val (sourceLabel, urlStr) = source
                val startMsg = "🚀 正在从最快源 [$sourceLabel] 下载 WinGet 安装包..."
                AppLogger.i(TAG, "$startMsg URL: $urlStr")
                emit(startMsg)

                downloadSuccess = downloadFileWithProgress(urlStr, bundleFile)
                if (downloadSuccess && bundleFile.exists() && bundleFile.length() > 2 * 1024 * 1024) {
                    break
                } else {
                    downloadSuccess = false
                    if (index < candidateSources.lastIndex) {
                        emit("⚠️ 从 [$sourceLabel] 下载 WinGet 未成功，正在自动切换至次优源...")
                    }
                }
            }

            if (!downloadSuccess) {
                val errMsg = "⚠️ WinGet 安装包下载未成功，将回退使用内置直连通道继续执行..."
                AppLogger.w(TAG, errMsg)
                emit(errMsg)
                return null
            }

            val deployMsg = "⚙️ WinGet 安装包下载完成 (${String.format(Locale.US, "%.1f", bundleFile.length() / 1048576.0)} MB)，正在静默安装 WinGet..."
            AppLogger.i(TAG, deployMsg)
            emit(deployMsg)

            val safeBundlePath = bundleFile.absolutePath.replace("'", "''")
            val psInstallScript = """
                try {
                    Add-AppxPackage -Path '$safeBundlePath' -ErrorAction Stop
                } catch {
                    Add-AppxProvisionedPackage -Online -PackagePath '$safeBundlePath' -SkipLicense -ErrorAction SilentlyContinue | Out-Null
                }
            """.trimIndent()
            runProcessAndEmitLogs(
                "winget-install",
                listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", psInstallScript)
            )

            var resolvedWinget = PythonEnvResolver.resolveWingetExecutable(forceRefresh = true)
            if (resolvedWinget == null) {
                emit("🔧 正在为当前账户配置 WinGet 独立运行环境...")
                if (extractWingetStandaloneFromBundle(bundleFile)) {
                    resolvedWinget = PythonEnvResolver.resolveWingetExecutable(forceRefresh = true)
                }
            }

            return if (resolvedWinget != null) {
                val okMsg = "✅ WinGet 自动安装完成！($resolvedWinget)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
                resolvedWinget
            } else {
                val warnMsg = "⚠️ WinGet 未能在当前系统环境中直接激活，将使用内置原生安装/卸载器继续执行"
                AppLogger.w(TAG, warnMsg)
                emit(warnMsg)
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动安装 WinGet 异常: ${e.message}", e)
            emit("⚠️ WinGet 自动安装出现异常 (${e.message})，将继续执行后续流程")
            return null
        } finally {
            if (bundleFile.exists()) {
                runCatching { bundleFile.delete() }
            }
        }
    }

    /**
     * 从 Microsoft.DesktopAppInstaller.msixbundle 中提取 x64 MSIX 内容到 %LOCALAPPDATA%\Programs\WinGet
     * 解决内置 Administrator 账户、Windows Server 或缺少商店组件时 Add-AppxPackage 无法生成 winget 别名的问题
     */
    private fun extractWingetStandaloneFromBundle(bundleFile: File): Boolean {
        return try {
            val localAppData = System.getenv("LOCALAPPDATA")
            val userHome = System.getProperty("user.home") ?: ""
            val appDataLocalDir = if (!localAppData.isNullOrBlank()) File(localAppData) else File(userHome, "AppData\\Local")
            val targetDir = File(appDataLocalDir, "Programs\\WinGet")
            if (!targetDir.exists()) targetDir.mkdirs()

            val tempMsixFile = File(bundleFile.parentFile, "AppInstaller_x64_temp.msix")
            try {
                ZipFile(bundleFile).use { bundleZip ->
                    val x64Entry = bundleZip.entries().asSequence().firstOrNull { entry ->
                        !entry.isDirectory &&
                            entry.name.endsWith(".msix", ignoreCase = true) &&
                            entry.name.contains("x64", ignoreCase = true)
                    } ?: return false

                    bundleZip.getInputStream(x64Entry).use { input ->
                        FileOutputStream(tempMsixFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                ZipFile(tempMsixFile).use { msixZip ->
                    for (entry in msixZip.entries()) {
                        if (entry.isDirectory) continue
                        val outFile = File(targetDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        msixZip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                // 将 *_app.dll 映射复制一份标准名称 *.dll，确保独立目录运行时不依赖全局 VC++ UWP 运行库
                targetDir.listFiles { f -> f.isFile && f.name.endsWith("_app.dll", ignoreCase = true) }?.forEach { appDll ->
                    val standardName = appDll.name.replace("_app.dll", ".dll", ignoreCase = true)
                    val standardDll = File(targetDir, standardName)
                    if (!standardDll.exists()) {
                        runCatching { appDll.copyTo(standardDll, overwrite = false) }
                    }
                }

                File(targetDir, "winget.exe").exists()
            } finally {
                if (tempMsixFile.exists()) {
                    runCatching { tempMsixFile.delete() }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解压独立 WinGet 运行环境失败: ${e.message}")
            false
        }
    }

    /**
     * Windows 官方安装包多源测速择优下载与无感静默安装（支持实时下载速度展示）
     * 默认安装至 Windows 官方传统用户目录: %LOCALAPPDATA%\Programs\Python\Python312
     */
    private suspend fun FlowCollector<String>.installPythonOnWindowsViaDirectInstaller(preResolvedWinget: String? = null) {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "geminiuiforge_env")
        if (!tempDir.exists()) tempDir.mkdirs()
        val installerFile = File(tempDir, "python-$PYTHON_WIN_VERSION-amd64.exe")

        try {
            // 1. 列出探测到的国内外候选下载源并进行 10 秒链接测速，返回按速度排序的可用列表
            val rankedSources = DownloadSourceSpeedTester.probeAndRankSources(
                sources = DownloadSourceSpeedTester.PYTHON_WIN64_INSTALLER_SOURCES
            ) { logLine ->
                emit(logLine)
            }

            var downloadSuccess = false
            val candidateSources = rankedSources.take(3).ifEmpty { DownloadSourceSpeedTester.PYTHON_WIN64_INSTALLER_SOURCES.take(2) }

            for ((index, source) in candidateSources.withIndex()) {
                val (sourceLabel, urlStr) = source
                val startMsg = "🚀 正在从最快源 [$sourceLabel] 下载 Python $PYTHON_WIN_VERSION 安装包..."
                AppLogger.i(TAG, "$startMsg URL: $urlStr")
                emit(startMsg)

                downloadSuccess = downloadFileWithProgress(urlStr, installerFile)
                if (downloadSuccess && installerFile.exists() && installerFile.length() > 5 * 1024 * 1024) {
                    break
                } else {
                    downloadSuccess = false
                    if (index < candidateSources.lastIndex) {
                        emit("⚠️ 从 [$sourceLabel] 下载未成功，正在自动切换至下一个最快源...")
                    }
                }
            }

            if (!downloadSuccess) {
                val wingetExe = preResolvedWinget ?: PythonEnvResolver.resolveWingetExecutable()
                if (wingetExe != null) {
                    emit("⚠️ 镜像直连下载未成功，检测到系统 winget ($wingetExe)，正在尝试通过 winget 兜底安装...")
                    val wingetCmd = listOf(
                        wingetExe, "install", "Python.Python.3.12",
                        "--silent", "--accept-package-agreements", "--accept-source-agreements"
                    )
                    if (runProcessAndEmitLogs("python", wingetCmd) && PythonEnvResolver.resolvePythonExecutable() != null) {
                        val ver = PythonEnvResolver.getPythonVersion() ?: PYTHON_WIN_VERSION
                        emit("✅ python 安装完成！(版本: $ver)")
                        return
                    }
                }

                val errMsg = "❌ Python 安装包下载失败，请检查网络连接或防火墙设置"
                AppLogger.e(TAG, errMsg)
                emit(errMsg)
                emit("💡 提示：您也可以点击上方「官方下载页」按钮在默认浏览器中手动下载安装")
                return
            }

            val installDirMsg = "⚙️ 安装包下载完成 (${String.format(Locale.US, "%.1f", installerFile.length() / 1048576.0)} MB)，正在执行静默安装 (这可能需要 1~2 分钟，请稍候)..."
            AppLogger.i(TAG, installDirMsg)
            emit(installDirMsg)

            // 执行 Python 官方安装程序按用户静默安装（无需管理员 UAC 即可安装至 %LOCALAPPDATA%\Programs\Python\Python312 并包含 pip）
            val installCmd = listOf(
                installerFile.absolutePath,
                "/quiet",
                "InstallAllUsers=0",
                "PrependPath=1",
                "Include_pip=1",
                "Include_test=0",
                "Include_doc=0"
            )
            val exitOk = runProcessAndEmitLogs("python-installer", installCmd)
            val resolvedPython = PythonEnvResolver.resolvePythonExecutable()
            val resolvedVer = PythonEnvResolver.getPythonVersion()

            if (exitOk && resolvedPython != null) {
                val okMsg = "✅ python 安装完成！(版本: ${resolvedVer ?: PYTHON_WIN_VERSION}, 路径: $resolvedPython)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
            } else if (resolvedPython != null) {
                val okMsg = "✅ python 安装完成！(版本: ${resolvedVer ?: PYTHON_WIN_VERSION}, 路径: $resolvedPython)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
            } else {
                val failMsg = "❌ python 静默安装未成功完成，系统标准目录中未检测到 python.exe"
                AppLogger.e(TAG, failMsg)
                emit(failMsg)
                emit("💡 提示：建议点击上方「官方下载页」按钮在浏览器中下载安装")
            }
        } catch (e: Exception) {
            val errMsg = "❌ Python 自动安装过程出错: ${e.message}"
            AppLogger.e(TAG, errMsg, e)
            emit(errMsg)
        } finally {
            if (installerFile.exists()) {
                runCatching { installerFile.delete() }
            }
        }
    }

    /**
     * 流式下载文件并实时计算当前下载速度（MB/s 或 KB/s）向 UI 与 AppLogger 汇报进度
     */
    private suspend fun FlowCollector<String>.downloadFileWithProgress(urlStr: String, destFile: File): Boolean {
        return try {
            val connection = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                AppLogger.e(TAG, "下载响应码异常: HTTP $code ($urlStr)")
                return false
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastReportedStep = -1
            val startTimeMs = System.currentTimeMillis()
            var lastUiReportTimeMs = startTimeMs
            var lastUiReportBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val nowMs = System.currentTimeMillis()
                        val timeDeltaMs = nowMs - lastUiReportTimeMs
                        val isCompleted = totalBytes > 0 && downloadedBytes >= totalBytes

                        // 每 350ms 或下载完成时刷新一次实时下载速度与进度
                        if (timeDeltaMs >= 350L || isCompleted) {
                            val bytesDelta = downloadedBytes - lastUiReportBytes
                            val speedBps = if (timeDeltaMs > 0) {
                                (bytesDelta * 1000.0) / timeDeltaMs
                            } else {
                                val totalElapsed = (nowMs - startTimeMs).coerceAtLeast(1L)
                                (downloadedBytes * 1000.0) / totalElapsed
                            }
                            val speedStr = DownloadSourceSpeedTester.formatDownloadSpeed(speedBps)
                            lastUiReportTimeMs = nowMs
                            lastUiReportBytes = downloadedBytes

                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                val dlMb = String.format(Locale.US, "%.1f", downloadedBytes / 1048576.0)
                                val totalMb = String.format(Locale.US, "%.1f", totalBytes / 1048576.0)
                                val progressLine = "⬇️ 下载进度: ${percent}% ($dlMb MB / $totalMb MB) | 当前速度: $speedStr"
                                emit(progressLine)

                                val step = percent / 20
                                if (step > lastReportedStep) {
                                    lastReportedStep = step
                                    AppLogger.d(TAG, progressLine)
                                }
                            } else {
                                val dlMb = String.format(Locale.US, "%.1f", downloadedBytes / 1048576.0)
                                val progressLine = "⬇️ 已下载: $dlMb MB | 当前速度: $speedStr"
                                emit(progressLine)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载文件失败 ($urlStr): ${e.message}")
            false
        }
    }

    /**
     * 运行子进程并流式回显输出，返回退出码是否为 0（内部子步骤使用，不直接打最终 ❌ 标签以免误触顶层中断）
     */
    private suspend fun FlowCollector<String>.runProcessAndEmitLogs(tag: String, commandList: List<String>): Boolean {
        AppLogger.i(TAG, "🚀 执行子命令 [$tag]: ${commandList.joinToString(" ")}")
        return try {
            val process = ProcessBuilder(commandList)
                .redirectErrorStream(true)
                .start()

            val charset = try {
                if (hostOs.isWindows) java.nio.charset.Charset.forName("GBK") else Charsets.UTF_8
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream, charset))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trimEnd()
                if (currentLine.isNotEmpty()) {
                    AppLogger.d(TAG, "[$tag] $currentLine")
                    emit(currentLine)
                }
            }
            val exitCode = process.waitFor()
            AppLogger.i(TAG, "🏁 子命令 [$tag] 结束，退出码: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            AppLogger.e(TAG, "⚠️ 子命令 [$tag] 执行异常: ${e.message}")
            false
        }
    }

    /**
     * 通用命令执行流（用于 pip install / uninstall 等顶层命令）
     */
    private suspend fun FlowCollector<String>.executeCommandFlow(
        tag: String,
        actionLabel: String,
        commandList: List<String>
    ) {
        AppLogger.i(TAG, "🚀 开始执行依赖$actionLabel [$tag], 命令行: ${commandList.joinToString(" ")}")
        try {
            val process = ProcessBuilder(commandList)
                .redirectErrorStream(true)
                .start()

            val charset = try {
                if (hostOs.isWindows) java.nio.charset.Charset.forName("GBK") else Charsets.UTF_8
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream, charset))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!
                AppLogger.d(TAG, "[$tag-$actionLabel] $currentLine")
                emit(currentLine)
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                AppLogger.i(TAG, "✅ $tag ${actionLabel}完成，退出码: 0")
                emit("✅ $tag ${actionLabel}完成！")
            } else {
                AppLogger.e(TAG, "❌ $tag ${actionLabel}失败，退出码: $exitCode")
                emit("❌ $tag ${actionLabel}失败，退出码: $exitCode")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ $tag ${actionLabel}出错: ${e.message}", e)
            emit("❌ ${actionLabel}出错: ${e.message}")
        }
    }

    override suspend fun isPythonAvailable(): Boolean = withContext(Dispatchers.IO) {
        PythonEnvResolver.resolvePythonExecutable() != null
    }

    override fun uninstallItem(name: String): Flow<String> = flow {
        if (name == "python") {
            if (hostOs.isWindows) {
                uninstallPythonOnWindowsFlow()
            } else {
                emit("❌ 暂不支持自动卸载系统级 Python，请使用系统包管理器手动卸载。")
            }
            return@flow
        }

        val pipBase = PythonEnvResolver.resolvePipCommand()
        val commandList = when (name) {
            "rembg", "pillow", "onnxruntime" -> pipBase + listOf("uninstall", "-y", name)
            else -> return@flow
        }

        executeCommandFlow(tag = name, actionLabel = "卸载", commandList = commandList)
    }.flowOn(Dispatchers.IO)

    /**
     * Windows 平台 Python 自动卸载流水线：
     * 1. 首先检查是否已安装 winget，若未安装则自动调用 ensureWingetInstalledFlow() 前置安装 winget
     * 2. 根据当前检测到的 Python 版本（如 3.12.8 -> Python.Python.3.12）调用 winget uninstall 静默卸载
     * 3. 若目标 Python 仍存在（例如由独立离线安装包安装），自动调用 Windows 注册表 QuietUninstallString / 官方缓存安装器完成静默卸载
     */
    private suspend fun FlowCollector<String>.uninstallPythonOnWindowsFlow() {
        val beforePythonExe = PythonEnvResolver.resolvePythonExecutable()
        val beforeVersion = PythonEnvResolver.getPythonVersion()
        val majorMinor = beforeVersion?.split(".")?.take(2)?.joinToString(".")?.takeIf { it.startsWith("3.") }

        var wingetExe = PythonEnvResolver.resolveWingetExecutable()
        if (wingetExe == null) {
            wingetExe = ensureWingetInstalledFlow()
        } else {
            val msg = "✅ 检测到系统已安装 WinGet ($wingetExe)"
            AppLogger.i(TAG, msg)
            emit(msg)
        }

        var uninstalledOk = false

        if (wingetExe != null) {
            val candidateIds = buildList {
                if (majorMinor != null) add("Python.Python.$majorMinor")
                add("Python.Python.3.12")
                add("Python.Python.3.11")
                add("Python.Python.3.10")
            }.distinct()

            for (pkgId in candidateIds) {
                emit("🔍 正在通过 WinGet 卸载 Python ($pkgId)...")
                val cmd = listOf(
                    wingetExe,
                    "uninstall",
                    "--id",
                    pkgId,
                    "--silent",
                    "--accept-source-agreements"
                )
                val exitOk = runProcessAndEmitLogs("python-winget-uninstall", cmd)
                val remainingExe = PythonEnvResolver.resolvePythonExecutable()
                if (exitOk || remainingExe == null || (beforePythonExe != null && !File(beforePythonExe).exists())) {
                    uninstalledOk = true
                    break
                }
            }
        }

        // 若 winget 卸载后原 Python 可执行文件仍存在，通过 Windows 注册表 QuietUninstallString 兜底静默卸载
        val stillExists = if (beforePythonExe != null && File(beforePythonExe).isAbsolute) {
            File(beforePythonExe).exists()
        } else {
            PythonEnvResolver.resolvePythonExecutable() != null
        }

        if (!uninstalledOk || stillExists) {
            emit("⚙️ 正在调用 Windows 系统注册表卸载通道清理 Python ${beforeVersion ?: "3.x"}...")
            val regOk = uninstallPythonViaWindowsRegistry(majorMinor)
            val afterExe = PythonEnvResolver.resolvePythonExecutable()
            val targetGone = if (beforePythonExe != null && File(beforePythonExe).isAbsolute) {
                !File(beforePythonExe).exists()
            } else {
                afterExe == null
            }
            if (regOk || targetGone) {
                uninstalledOk = true
            }
        }

        if (uninstalledOk) {
            val okMsg = "✅ python 卸载完成！"
            AppLogger.i(TAG, okMsg)
            emit(okMsg)
        } else {
            val errMsg = "❌ python 自动卸载未完全清除，请确认没有正在运行的 Python 进程占用文件"
            AppLogger.e(TAG, errMsg)
            emit(errMsg)
        }
    }

    /**
     * 通过查询 Windows 注册表 Uninstall 项中的 Python 官方缓存卸载器执行无感静默卸载
     */
    private suspend fun FlowCollector<String>.uninstallPythonViaWindowsRegistry(majorMinor: String?): Boolean {
        val versionFilter = majorMinor ?: "3."
        val psScript = """
            ${'$'}paths = @(
                'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                'HKLM:\Software\WOW6432Node\Software\Microsoft\Windows\CurrentVersion\Uninstall\*'
            )
            ${'$'}items = Get-ItemProperty ${'$'}paths -ErrorAction SilentlyContinue | Where-Object {
                ${'$'}_.DisplayName -like "Python $versionFilter*" -and (${'$'}_.QuietUninstallString -or ${'$'}_.UninstallString)
            }
            ${'$'}found = ${'$'}false
            foreach (${'$'}item in ${'$'}items) {
                ${'$'}cmd = if (${'$'}item.QuietUninstallString) { ${'$'}item.QuietUninstallString } else { "${'$'}(${'$'}item.UninstallString) /quiet" }
                if (${'$'}cmd -match '^"([^"]+)"\s*(.*)$') {
                    ${'$'}exe = ${'$'}Matches[1]
                    ${'$'}args = if (${'$'}Matches[2]) { "${'$'}(${'$'}Matches[2]) /quiet" } else { "/uninstall /quiet" }
                    if (Test-Path ${'$'}exe) {
                        Write-Output "正在执行卸载器: ${'$'}exe ${'$'}args"
                        ${'$'}p = Start-Process -FilePath ${'$'}exe -ArgumentList ${'$'}args -Wait -PassThru -WindowStyle Hidden
                        if (${'$'}p.ExitCode -eq 0) { ${'$'}found = ${'$'}true }
                    }
                }
            }
            if (${'$'}found) { exit 0 } else { exit 1 }
        """.trimIndent()

        return runProcessAndEmitLogs(
            "python-registry-uninstall",
            listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", psScript)
        )
    }

    override suspend fun getInstalledPipPackages(): List<PipPackageInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PipPackageInfo>()
        if (!isPythonAvailable()) return@withContext result

        val pipBase = PythonEnvResolver.resolvePipCommand()
        val installedJsonStr = PythonEnvResolver.runQuickCommand(pipBase + listOf("list", "--format=json"), 15L)
        val installedList = try {
            if (!installedJsonStr.isNullOrBlank()) {
                json.decodeFromString<List<PipPackageJson>>(installedJsonStr)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val installedNames = mutableSetOf<String>()
        installedList.forEach { pkg ->
            installedNames.add(pkg.name.lowercase())
            result.add(
                PipPackageInfo(
                    name = pkg.name,
                    version = pkg.version,
                    isInstalled = true
                )
            )
        }

        // 追加推荐但未安装的包
        recommendedPackages.forEach { (name, desc) ->
            if (!installedNames.contains(name.lowercase())) {
                result.add(
                    PipPackageInfo(
                        name = name,
                        isInstalled = false,
                        isRecommended = true,
                        description = desc
                    )
                )
            }
        }

        result.sortedBy { it.name }
    }

    override suspend fun fetchOutdatedPipPackages(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!isPythonAvailable()) return@withContext emptyMap()

        val pipBase = PythonEnvResolver.resolvePipCommand()
        val outdatedJsonStr = PythonEnvResolver.runQuickCommand(pipBase + listOf("list", "--outdated", "--format=json"), 20L)
        try {
            if (!outdatedJsonStr.isNullOrBlank()) {
                val outdatedList = json.decodeFromString<List<PipPackageJson>>(outdatedJsonStr)
                outdatedList.associate { it.name to (it.latestVersion ?: "") }
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun searchPipPackage(query: String): PipPackageInfo? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        try {
            val urlString = "https://pypi.org/pypi/$query/json"
            val url = URI(urlString).toURL()
            AppLogger.d(TAG, "🌐 正在请求包详情: $urlString")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                AppLogger.d(TAG, "📥 收到响应 (${responseText.length} bytes): ${responseText.take(200)}...")

                val descRegex = """"summary"\s*:\s*"([^"]*)"""".toRegex()
                val versionRegex = """"version"\s*:\s*"([^"]+)"""".toRegex()

                val repoRegex = """"Repository"\s*:\s*"([^"]+)"""".toRegex()
                val homeRegex = """"Homepage"\s*:\s*"([^"]+)"""".toRegex()
                val sourceRegex = """"Source"\s*:\s*"([^"]+)"""".toRegex()

                val desc = descRegex.find(responseText)?.groups?.get(1)?.value ?: "来自 PyPI 的在线扩展包"
                val version = versionRegex.find(responseText)?.groups?.get(1)?.value ?: ""

                val repoMatch = repoRegex.find(responseText)?.groups?.get(1)?.value
                val sourceMatch = sourceRegex.find(responseText)?.groups?.get(1)?.value
                val homeMatch = homeRegex.find(responseText)?.groups?.get(1)?.value
                val projectUrl = repoMatch ?: sourceMatch ?: homeMatch

                PipPackageInfo(
                    name = query,
                    version = null,
                    latestVersion = version,
                    isInstalled = false,
                    isRecommended = false,
                    description = desc,
                    projectUrl = projectUrl
                )
            } else {
                AppLogger.i(TAG, "⚠️ 包详情请求失败, 响应码: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 搜索包时出错: ${e.message}", e)
            null
        }
    }

    override suspend fun fetchPackageUrl(packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://pypi.org/pypi/$packageName/json"
            val url = URI(urlString).toURL()
            AppLogger.d(TAG, "🌐 正在请求包 URL: $urlString")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                AppLogger.d(TAG, "📥 收到 URL 响应 (${responseText.length} bytes)")
                val repoRegex = """"Repository"\s*:\s*"([^"]+)"""".toRegex()
                val homeRegex = """"Homepage"\s*:\s*"([^"]+)"""".toRegex()
                val sourceRegex = """"Source"\s*:\s*"([^"]+)"""".toRegex()

                val repoMatch = repoRegex.find(responseText)?.groups?.get(1)?.value
                val sourceMatch = sourceRegex.find(responseText)?.groups?.get(1)?.value
                val homeMatch = homeRegex.find(responseText)?.groups?.get(1)?.value

                repoMatch ?: sourceMatch ?: homeMatch
            } else {
                AppLogger.i(TAG, "⚠️ URL 请求失败, 响应码: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 获取包 URL 时出错: ${e.message}", e)
            null
        }
    }

    override suspend fun fetchTopPackages(): List<String> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://hugovk.github.io/top-pypi-packages/top-pypi-packages-30-days.min.json"
            val url = URI(urlString).toURL()
            AppLogger.d(TAG, "🌐 正在从云端拉取 Top 排行榜: $urlString")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                AppLogger.d(TAG, "✅ 排行榜拉取成功, 响应长度: ${responseText.length} bytes")
                val response = json.decodeFromString<org.gemini.ui.forge.model.app.TopPipPackagesResponse>(responseText)
                response.rows.map { it.project }
            } else {
                AppLogger.i(TAG, "⚠️ 排行榜拉取失败, 响应码: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 拉取排行榜时出错: ${e.message}", e)
            emptyList()
        }
    }

    override fun batchInstallPipPackages(names: List<String>): Flow<String> = flow {
        if (names.isEmpty()) return@flow
        val rankedPypiSources = DownloadSourceSpeedTester.probeAndRankSources(
            sources = DownloadSourceSpeedTester.PYPI_INDEX_SOURCES
        ) { logLine ->
            emit(logLine)
        }
        val bestSource = rankedPypiSources.firstOrNull() ?: DownloadSourceSpeedTester.PYPI_INDEX_SOURCES.first()
        val host = runCatching { URI(bestSource.second).host }.getOrNull()
        emit("🚀 正在使用最优源 [${bestSource.first}] 批量安装/更新 ${names.joinToString(", ")}...")

        val pipBase = PythonEnvResolver.resolvePipCommand()
        val commandList = buildList {
            addAll(pipBase)
            add("install")
            add("--upgrade")
            addAll(names)
            add("-i")
            add(bestSource.second)
            if (!host.isNullOrBlank()) {
                add("--trusted-host")
                add(host)
            }
        }

        executeCommandFlow(tag = "批量安装", actionLabel = "批量安装/更新", commandList = commandList)
    }.flowOn(Dispatchers.IO)

    override fun batchUninstallPipPackages(names: List<String>): Flow<String> = flow {
        if (names.isEmpty()) return@flow
        val pipBase = PythonEnvResolver.resolvePipCommand()
        val commandList = mutableListOf<String>()
        commandList.addAll(pipBase)
        commandList.addAll(listOf("uninstall", "-y"))
        commandList.addAll(names)

        executeCommandFlow(tag = "批量卸载", actionLabel = "批量卸载", commandList = commandList)
    }.flowOn(Dispatchers.IO)

    private fun extractVersion(pipOutput: String?): String? {
        if (pipOutput == null) return null
        return pipOutput.lines().find { it.startsWith("Version:") }?.replace("Version:", "")?.trim()
    }
}

actual fun createEnvironmentCheckService(): EnvironmentCheckService = JvmEnvironmentCheckService()
