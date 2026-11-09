package org.gemini.ui.forge.service

import geminiuiforge.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipFile
import org.gemini.ui.forge.utils.EnvCliPromptCoordinator

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
     * - Windows: 优先使用 WinGet 安装；若未安装 WinGet 则按需自动准备前置运行库依赖与 WinGet 核心程序；WinGet 不可用或失败时降级走官方安装包
     * - macOS: 优先探测 Homebrew 自动安装；若无 Homebrew 则打开官方下载页
     */
    /**
     * 前置检查当前是否具备管理员特权；若处于受限普通模式，主动提示用户提权并重新拉起
     *
     * @param actionName 操作名称（如 "安装 Python 3.12" 或 "卸载 Python"）
     * @return true 表示符合管理员权限（或非 Windows 平台），可继续执行；false 表示已被用户拒绝或正在提权中退出
     */
    private suspend fun FlowCollector<String>.ensureElevationGate(actionName: String): Boolean {
        if (!hostOs.isWindows || org.gemini.ui.forge.utils.SystemElevationHelper.isAdministrator()) {
            return true
        }

        val promptMsg = "⚠️ 当前操作 [$actionName] 需要 Windows 管理员特权，但当前程序运行于受限普通模式。\n是否立即提升权限以管理员身份重新拉起应用并继续？ [Y/n]: "
        AppLogger.w(TAG, promptMsg)
        emit(promptMsg)

        val agreed = org.gemini.ui.forge.utils.EnvCliPromptCoordinator.awaitUserDecision("python", promptMsg)
        if (agreed) {
            emit("🚀 正在唤起 Windows UAC 请求管理员特权重新拉起应用...")
            val elevated = org.gemini.ui.forge.utils.SystemElevationHelper.tryElevateSelfOnWindows(emptyArray())
            if (elevated) {
                emit("✅ 管理员特权进程已成功拉起，当前界面即将安全重启切换...")
                kotlin.system.exitProcess(0)
            } else {
                emit("⚠️ 提权操作未能完成或被用户在 UAC 界面取消，操作已中止。")
                return false
            }
        } else {
            val cancelMsg = "⚠️ 用户已取消管理员提权，操作已中止。您可以随时在设置中重新开启「以管理员身份启动应用」或手动以管理员身份运行。"
            AppLogger.i(TAG, cancelMsg)
            emit(cancelMsg)
            return false
        }
    }

    private suspend fun FlowCollector<String>.installPythonFlow() {
        if (hostOs.isWindows) {
            if (!ensureElevationGate("安装 Python 3.12 运行环境")) {
                return
            }
            var wingetExe = PythonEnvResolver.resolveWingetExecutable()
            if (wingetExe == null) {
                wingetExe = ensureWingetInstalledFlow()
            } else {
                val msg = "✅ 检测到系统已安装 WinGet ($wingetExe)"
                AppLogger.i(TAG, msg)
                emit(msg)
            }

            var installSuccess = false
            if (wingetExe != null) {
                emit("════════ 阶段 3/3: 优先使用 WinGet 自动安装 Python 3.12 ════════")
                installSuccess = installPythonViaWinget(wingetExe)
            }

            if (!installSuccess) {
                val promptMsg = if (wingetExe != null) {
                    "WinGet 安装 Python 未能成功完成，是否切换为 Python 官方安装程序继续安装？"
                } else {
                    "WinGet 部署未成功或在此 Windows 账户受限，是否切换为 Python 官方安装程序继续安装？"
                }
                val reasonTitle = if (wingetExe != null) "WinGet 安装 Python 过程受阻" else "WinGet 核心未能在此 Windows 账户中激活"
                emit("⚠️ $reasonTitle")
                emit("❓ 正在等待用户确认是否切换模式...")
                val userAgreed = EnvCliPromptCoordinator.awaitUserDecision("python", promptMsg)
                if (userAgreed) {
                    emit("✅ 用户已授权：正在切换为 Python 官方安装程序继续安装...")
                    emit("════════ 切换模式: 使用 Python 官方安装程序进行静默安装 ════════")
                    emit("🔍 正在探测 Python $PYTHON_WIN_VERSION 可用下载源并准备安装...")
                    installPythonOnWindowsViaDirectInstaller(wingetExe)
                } else {
                    val cancelMsg = "❌ 用户取消切换模式：终止本次 Python 安装操作。"
                    AppLogger.i(TAG, cancelMsg)
                    emit(cancelMsg)
                    return
                }
            }
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
     * 优先通过 WinGet 静默安装 Python 3.12 (Python.Python.3.12)
     */
    private suspend fun FlowCollector<String>.installPythonViaWinget(wingetExe: String): Boolean {
        emit("🚀 正在通过 WinGet 静默安装 Python 3.12 (包标识: Python.Python.3.12)...")
        val wingetCmd = listOf(
            wingetExe,
            "install",
            "Python.Python.3.12",
            "--silent",
            "--accept-package-agreements",
            "--accept-source-agreements"
        )
        val exitOk = runProcessAndEmitLogs("python-winget-install", wingetCmd)
        val resolvedPython = PythonEnvResolver.resolvePythonExecutable()
        val resolvedVer = PythonEnvResolver.getPythonVersion()
        return if (resolvedPython != null) {
            val okMsg = "✅ python 安装完成！(版本: ${resolvedVer ?: PYTHON_WIN_VERSION}, 路径: $resolvedPython)"
            AppLogger.i(TAG, okMsg)
            emit(okMsg)
            true
        } else {
            val failMsg = "⚠️ WinGet 安装命令执行完毕，但未在系统标准路径中检测到有效 python.exe"
            AppLogger.w(TAG, failMsg)
            emit(failMsg)
            false
        }
    }

    /**
     * 将 PowerShell 脚本转换为 UTF-16LE 的 Base64 编码，供 -EncodedCommand 使用，彻底杜绝命令行单双引号与特殊字符转义缺陷
     * 自动注入 $ProgressPreference = 'SilentlyContinue'，防止 PowerShell 原生进度流作为 CLIXML 泄漏到错误流中
     */
    private fun encodePowerShellCommand(script: String): String {
        val cleanScript = "\$ProgressPreference = 'SilentlyContinue';\n$script"
        return Base64.getEncoder().encodeToString(cleanScript.toByteArray(Charsets.UTF_16LE))
    }

    /**
     * 对标 pip 的包获取流水线：
     * 1. 优先联网并发探测远程源的权威 Content-Length 与版本元数据；
     * 2. 本地若存在同名文件，严格比对本地字节数与远程权威大小：
     *    - 大小完全一致：直接复用 (Using cached ...)，跳过下载；
     *    - 本地文件残缺 (小于远程大小)：自动重命名为 .part 临时文件发起断点续传补全，杜绝半截损坏安装包被误用；
     *    - 本地文件超出远程大小：清理异常旧文件并重新下载；
     * 3. 本地若存在 .part 临时文件：
     *    - 大小达到远程大小：原子重命名提交并复用；
     *    - 小于远程大小：继续在此基础上 HTTP Range 断点续传；
     * 4. 远程探测不到大小时回退至 minSizeBytes 校验保底。
     */
    private suspend fun FlowCollector<String>.fetchOrDownloadPackage(
        packageName: String,
        fileName: String,
        candidateSources: List<Pair<String, String>>,
        minSizeBytes: Long = 1024 * 1024,
        expectedVersion: String? = null
    ): File? {
        val targetFile = File(DownloadSourceSpeedTester.ENV_DOWNLOADS_DIR, fileName)
        val partFile = File(targetFile.parentFile, targetFile.name + ".part")

        // 步骤一：优先联网并发探测远程候选源权威元数据（文件大小、版本支持）
        val probeMsg = "🔍 正在联网查询 $packageName 权威元数据与文件大小..."
        AppLogger.i(TAG, probeMsg)
        emit(probeMsg)

        val remoteMeta = DownloadSourceSpeedTester.probeRemoteMetadata(candidateSources)
        val remoteSize = remoteMeta?.contentLength ?: -1L

        if (remoteSize > 0L) {
            val remoteMb = String.format(Locale.US, "%.1f", remoteSize / 1048576.0)
            val metaOkMsg = "🌐 权威源元数据解析成功: 期望大小 $remoteMb MB (来自 [${remoteMeta?.sourceLabel}])"
            AppLogger.i(TAG, metaOkMsg)
            emit(metaOkMsg)

            // 检查已完成的正式文件 targetFile
            if (targetFile.exists()) {
                val localLength = targetFile.length()
                val localMb = String.format(Locale.US, "%.1f", localLength / 1048576.0)
                if (localLength == remoteSize) {
                    val verSuffix = if (!expectedVersion.isNullOrBlank()) ", 版本: $expectedVersion" else ""
                    val cachedMsg = "✅ 本地缓存安装包完整性校验通过 (大小: $localMb MB$verSuffix)，跳过下载直接使用"
                    AppLogger.i(TAG, cachedMsg)
                    emit(cachedMsg)
                    return targetFile
                } else if (localLength < remoteSize) {
                    val warnMsg = "⚠️ 检测到本地缓存安装包残缺 (本地: $localMb MB, 远程期望: $remoteMb MB)，自动转换为 .part 发起断点续传补全..."
                    AppLogger.w(TAG, warnMsg)
                    emit(warnMsg)
                    // 若存在 partFile 且其更大，则丢弃较小的 targetFile；否则将 targetFile 转为 partFile
                    if (partFile.exists() && partFile.length() >= localLength) {
                        targetFile.delete()
                    } else {
                        if (partFile.exists()) partFile.delete()
                        targetFile.renameTo(partFile)
                    }
                } else {
                    val warnMsg = "⚠️ 检测到本地缓存安装包超出权威大小 ($localMb MB > $remoteMb MB)，可能已损坏，清理后重新下载..."
                    AppLogger.w(TAG, warnMsg)
                    emit(warnMsg)
                    targetFile.delete()
                    if (partFile.exists()) partFile.delete()
                }
            }

            // 检查断点临时文件 partFile
            if (partFile.exists()) {
                val partLength = partFile.length()
                val partMb = String.format(Locale.US, "%.1f", partLength / 1048576.0)
                if (partLength == remoteSize) {
                    val submitMsg = "✅ 检测到历史断点临时文件已达到完整大小 ($partMb MB)，直接原子提交为正式安装包"
                    AppLogger.i(TAG, submitMsg)
                    emit(submitMsg)
                    if (targetFile.exists()) targetFile.delete()
                    if (partFile.renameTo(targetFile)) {
                        return targetFile
                    }
                } else if (partLength < remoteSize && partLength > 0L) {
                    val resumeMsg = "⚡ 发现历史未完成的断点临时文件 (已完成: $partMb MB / $remoteMb MB)，继续断点续传..."
                    AppLogger.i(TAG, resumeMsg)
                    emit(resumeMsg)
                } else if (partLength > remoteSize) {
                    partFile.delete()
                }
            }
        } else {
            // 远程探测未返回权威大小时的离线/兜底校验
            val cachedFile = DownloadSourceSpeedTester.getCachedDownloadFile(fileName, minSizeBytes)
            if (cachedFile != null) {
                val cachedMb = String.format(Locale.US, "%.1f", cachedFile.length() / 1048576.0)
                val cachedMsg = "Using cached ${cachedFile.absolutePath} ($cachedMb MB)"
                AppLogger.i(TAG, cachedMsg)
                emit(cachedMsg)
                return cachedFile
            }
        }

        val collectingMsg = "Collecting $packageName"
        AppLogger.i(TAG, collectingMsg)
        emit(collectingMsg)

        // 步骤二：候选源测速排序（优先将已嗅探成功的源置顶）
        val prioritizedCandidates = if (remoteMeta != null) {
            listOf(remoteMeta.sourceLabel to remoteMeta.sourceUrl) +
                candidateSources.filterNot { it.first == remoteMeta.sourceLabel }
        } else {
            candidateSources
        }

        val rankedSources = DownloadSourceSpeedTester.probeAndRankSources(
            sources = prioritizedCandidates
        ) { logLine ->
            emit(logLine)
        }

        val sourcesToTry = rankedSources.take(3).ifEmpty { prioritizedCandidates.take(2) }
        var downloadOk = false

        // 步骤三：流式断点续传下载与原子提交
        for ((index, source) in sourcesToTry.withIndex()) {
            val (sourceLabel, urlStr) = source
            val startMsg = "🚀 正在从最快源 [$sourceLabel] 下载 $packageName..."
            AppLogger.i(TAG, "$startMsg URL: $urlStr")
            emit(startMsg)

            downloadOk = downloadFileWithProgress(urlStr, targetFile)
            val isSizeOk = if (remoteSize > 0L) {
                targetFile.exists() && targetFile.length() == remoteSize
            } else {
                targetFile.exists() && targetFile.length() >= minSizeBytes
            }

            if (downloadOk && isSizeOk) {
                break
            } else {
                downloadOk = false
                if (index < sourcesToTry.lastIndex) {
                    val curMbStr = if (partFile.exists() && partFile.length() > 0) {
                        " (已保留断点进度: ${String.format(Locale.US, "%.1f", partFile.length() / 1048576.0)} MB)"
                    } else ""
                    emit("⚠️ 从 [$sourceLabel] 未能完整获取文件，正在自动切换至下一个可用镜像$curMbStr...")
                }
            }
        }

        val finalCheckOk = if (remoteSize > 0L) {
            targetFile.exists() && targetFile.length() == remoteSize
        } else {
            targetFile.exists() && targetFile.length() >= minSizeBytes
        }

        return if (downloadOk && finalCheckOk) {
            targetFile
        } else {
            null
        }
    }

    /**
     * 检测并在缺失时自动下载、测速并安装 Windows WinGet (Microsoft.DesktopAppInstaller)
     * 支持 PowerShell Add-AppxPackage 标准安装及内置 Administrator 账户下独立解压部署兜底
     */
    private suspend fun FlowCollector<String>.ensureWingetInstalledFlow(): String? {
        PythonEnvResolver.resolveWingetExecutable()?.let { return it }

        val noticeMsg = "📦 检测到当前系统未安装 WinGet (Windows 包管理器)"
        val officialMsg = "🌐 WinGet 官方下载地址: ${DownloadSourceSpeedTester.WINGET_OFFICIAL_DOWNLOAD_URL}"
        AppLogger.i(TAG, noticeMsg)
        AppLogger.i(TAG, officialMsg)
        emit(noticeMsg)
        emit(officialMsg)

        if (!ensureElevationGate("部署 WinGet 核心运行环境")) {
            return null
        }

        // 像传统 CLI 交互式工具一样挂起请示用户
        val askMsg = "是否允许自动下载并安装 WinGet 及其运行库组件？ [Y/n]: "
        emit("💡 提示：WinGet 用于管理 Python 与系统工具的自动升级与精准静默卸载。")
        val userAgree = EnvCliPromptCoordinator.requestPrompt("python", askMsg)
        if (!userAgree) {
            emit("❌ 用户取消安装 WinGet，将跳过 WinGet 部署并继续执行...")
            return null
        }
        emit("✅ 用户已确认：继续执行 WinGet 自动收集与安装流程")

        try {
            // 1. 对标 pip 收集并安装 WinGet 前置运行库依赖 (VCLibs / UI.Xaml / WinAppSDK)
            emit("──────── 阶段 1/3: 准备 WinGet 前置运行库依赖环境 (Dependencies.zip) ────────")
            emit("🔍 正在检查 WinGet 前置运行库依赖...")
            val depsFile = fetchOrDownloadPackage(
                packageName = "DesktopAppInstaller_Dependencies.zip (winget-cli pre-requisites)",
                fileName = "DesktopAppInstaller_Dependencies.zip",
                candidateSources = DownloadSourceSpeedTester.WINGET_DEPENDENCIES_SOURCES,
                minSizeBytes = 500 * 1024
            )

            val dependencyFiles = if (depsFile != null && depsFile.exists()) {
                installWingetDependencies(depsFile)
            } else {
                emptyList()
            }

            // 2. 收集并下载 WinGet 官方安装包 (Microsoft.DesktopAppInstaller msixbundle)
            emit("──────── 阶段 2/3: 下载并部署 WinGet 核心程序本体 (winget-cli) ────────")
            val bundleFile = fetchOrDownloadPackage(
                packageName = "winget-cli (Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle)",
                fileName = "Microsoft.DesktopAppInstaller_8wekyb3d8bbwe.msixbundle",
                candidateSources = DownloadSourceSpeedTester.WINGET_INSTALLER_SOURCES,
                minSizeBytes = 2 * 1024 * 1024
            )

            if (bundleFile == null || !bundleFile.exists()) {
                val errMsg = "⚠️ WinGet 安装包下载未成功，将尝试使用系统已有环境继续执行..."
                AppLogger.w(TAG, errMsg)
                emit(errMsg)
                return null
            }

            val deployMsg = "⚙️ 依赖收集完成，正在执行 WinGet 系统注册与静默安装..."
            AppLogger.i(TAG, deployMsg)
            emit(deployMsg)

            val safeBundlePath = bundleFile.absolutePath.replace("'", "''")
            val depArrayItems = dependencyFiles.joinToString(",\n") { "                    '${it.absolutePath.replace("'", "''")}'" }
            val psInstallScript = """
                ${'$'}ErrorActionPreference = 'Stop'
                # 针对 0x80073CF9 (无法安装) 的根因自愈：创建 AppReadiness 临时目录并启动服务
                try {
                    ${'$'}appReadinessDir = Join-Path ${'$'}env:SystemRoot 'AppReadiness'
                    if (-not (Test-Path ${'$'}appReadinessDir)) {
                        New-Item -ItemType Directory -Path ${'$'}appReadinessDir -Force -ErrorAction SilentlyContinue | Out-Null
                    }
                    Start-Service AppReadiness -ErrorAction SilentlyContinue
                } catch {}

                ${'$'}bundlePath = '$safeBundlePath'
                ${'$'}depPaths = @(
$depArrayItems
                )
                try {
                    Write-Output "正在通过 Add-AppxPackage (含关联依赖项) 注册 WinGet..."
                    if (${'$'}depPaths.Count -gt 0) {
                        Add-AppxPackage -Path ${'$'}bundlePath -DependencyPath ${'$'}depPaths -ForceApplicationShutdown -ErrorAction Stop
                    } else {
                        Add-AppxPackage -Path ${'$'}bundlePath -ForceApplicationShutdown -ErrorAction Stop
                    }
                    Write-Output "✅ Add-AppxPackage 部署成功！"
                } catch {
                    Write-Output "⚠️ Add-AppxPackage 未成功: ${'$'}(${'$'}_.Exception.Message)"
                    try {
                        Write-Output "正在尝试通过 Add-AppxProvisionedPackage (挂载依赖包) 为系统预配..."
                        if (${'$'}depPaths.Count -gt 0) {
                            Add-AppxProvisionedPackage -Online -PackagePath ${'$'}bundlePath -DependencyPackagePath ${'$'}depPaths -SkipLicense -ErrorAction Stop | Out-Null
                        } else {
                            Add-AppxProvisionedPackage -Online -PackagePath ${'$'}bundlePath -SkipLicense -ErrorAction Stop | Out-Null
                        }
                        Write-Output "✅ Add-AppxProvisionedPackage 预配成功！"
                    } catch {
                        Write-Output "⚠️ Add-AppxProvisionedPackage 预配失败: ${'$'}(${'$'}_.Exception.Message)"
                    }
                }
            """.trimIndent()

            runProcessAndEmitLogs(
                "winget-install",
                listOf("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodePowerShellCommand(psInstallScript))
            )

            var resolvedWinget = PythonEnvResolver.resolveWingetExecutable(forceRefresh = true)
            if (resolvedWinget == null) {
                emit("🔧 检测到当前 Windows 账户缺少标准 WindowsApps 别名激活通道（0x80073CF9 / 权限受限），正在配置独立运行环境...")
                if (extractWingetStandaloneFromBundle(bundleFile, depsFile)) {
                    resolvedWinget = PythonEnvResolver.resolveWingetExecutable(forceRefresh = true)
                }
            }

            return if (resolvedWinget != null) {
                val okMsg = "✅ WinGet 自动安装完成！($resolvedWinget)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
                resolvedWinget
            } else {
                val warnMsg = "⚠️ WinGet 未能在此 Windows 账户中成功激活 (0x80073CF9 / 权限受限)"
                AppLogger.w(TAG, warnMsg)
                emit(warnMsg)
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动安装 WinGet 异常: ${e.message}", e)
            emit("⚠️ WinGet 自动安装出现异常 (${e.message})")
            return null
        }
    }

    /**
     * 安装 WinGet 官方依赖包解压出的 x64 appx 依赖（VCLibs、UI.Xaml 等）并返回有效的依赖包列表
     */
    private suspend fun FlowCollector<String>.installWingetDependencies(depsZipFile: File): List<File> {
        val validDeps = mutableListOf<File>()
        try {
            val extractDir = File(depsZipFile.parentFile, "winget_dependencies_extracted")
            if (!extractDir.exists()) extractDir.mkdirs()

            ZipFile(depsZipFile).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val outFile = File(extractDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // 严谨架构过滤：排除 ARM64 与纯 x86，精准匹配 x64 与 neutral 运行库
            val appxFiles = extractDir.walkTopDown().filter {
                if (!it.isFile) return@filter false
                val ext = it.extension.lowercase()
                if (ext != "appx" && ext != "msix") return@filter false
                val nameLower = it.name.lowercase()
                val isArm = nameLower.contains("arm")
                val isX86 = nameLower.contains("x86") && !nameLower.contains("x64")
                !isArm && !isX86
            }.toList()

            validDeps.addAll(appxFiles)

            if (appxFiles.isNotEmpty()) {
                emit("📦 检测到 ${appxFiles.size} 个前置运行库，开始逐项注册...")
                for ((index, appx) in appxFiles.withIndex()) {
                    val currentIdx = index + 1
                    val stepMsg = "⚙️ [$currentIdx/${appxFiles.size}] 正在注册前置运行库: ${appx.name} ..."
                    AppLogger.i(TAG, stepMsg)
                    emit(stepMsg)

                    val safePath = appx.absolutePath.replace("'", "''")
                    val psScript = """
                        ${'$'}ErrorActionPreference = 'Stop'
                        try {
                            Add-AppxPackage -Path '$safePath' -ErrorAction Stop
                            Write-Output "✅ 注册成功: ${appx.name}"
                        } catch {
                            Write-Output "ℹ️ 已存在或跳过: ${appx.name}"
                        }
                    """.trimIndent()
                    runProcessAndEmitLogs(
                        "winget-dep",
                        listOf("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodePowerShellCommand(psScript))
                    )
                    emit("✅ [$currentIdx/${appxFiles.size}] 前置运行库已就绪: ${appx.name}")
                }
                emit("🎉 全部 ${appxFiles.size} 个 WinGet 前置运行库已注册完成！")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "安装 WinGet 依赖包出现警告: ${e.message}")
        }
        return validDeps
    }

    /**
     * 从 Microsoft.DesktopAppInstaller.msixbundle 中提取 x64 MSIX 内容到 %LOCALAPPDATA%\Programs\WinGet
     * 解决内置 Administrator 账户、Windows Server 或缺少商店组件时 Add-AppxPackage 无法生成 winget 别名的问题
     */
    private suspend fun FlowCollector<String>.extractWingetStandaloneFromBundle(bundleFile: File, depsZipFile: File? = null): Boolean {
        return try {
            val localAppData = System.getenv("LOCALAPPDATA")
            val userHome = System.getProperty("user.home") ?: ""
            val appDataLocalDir = if (!localAppData.isNullOrBlank()) File(localAppData) else File(userHome, "AppData\\Local")
            val targetDir = File(appDataLocalDir, "Programs\\WinGet")
            if (!targetDir.exists()) targetDir.mkdirs()

            emit("📦 正在解析安装包结构: ${bundleFile.name}...")
            val tempMsixFile = File(bundleFile.parentFile, "AppInstaller_x64_temp.msix")
            try {
                ZipFile(bundleFile).use { bundleZip ->
                    val msixEntries = bundleZip.entries().asSequence().filter {
                        !it.isDirectory && it.name.endsWith(".msix", ignoreCase = true)
                    }.toList()

                    val x64Entry = msixEntries.firstOrNull {
                        val name = it.name.lowercase(Locale.ROOT)
                        name.contains("x64") || name.contains("amd64")
                    } ?: msixEntries.firstOrNull {
                        val name = it.name.lowercase(Locale.ROOT)
                        !name.contains("arm") && !name.contains("x86")
                    }

                    if (x64Entry == null) {
                        emit("⚠️ 未在安装包中找到匹配的 x64/amd64 主程序包，候选包: ${msixEntries.map { File(it.name).name }}")
                        return false
                    }

                    emit("📦 匹配到主程序包: ${File(x64Entry.name).name}，正在提取核心组件...")
                    bundleZip.getInputStream(x64Entry).use { input ->
                        FileOutputStream(tempMsixFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                emit("📂 正在部署核心程序文件到独立目录 ($targetDir)...")
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

                // 深度提取依赖：Dependencies.zip 内包含各依赖的 .appx 包，深入解压各 appx 提取 dll
                if (depsZipFile != null && depsZipFile.exists()) {
                    emit("📦 正在扫描并提取前置依赖运行库 DLL...")
                    var extractedDllCount = 0
                    runCatching {
                        ZipFile(depsZipFile).use { depZip ->
                            for (entry in depZip.entries()) {
                                if (entry.isDirectory) continue
                                val lower = entry.name.lowercase(Locale.ROOT)
                                // 1. 直接在 zip 顶层的 dll
                                if (lower.endsWith(".dll")) {
                                    val dllName = File(entry.name).name
                                    val outDll = File(targetDir, dllName)
                                    if (!outDll.exists()) {
                                        depZip.getInputStream(entry).use { inStream ->
                                            FileOutputStream(outDll).use { outStream -> inStream.copyTo(outStream) }
                                        }
                                        extractedDllCount++
                                    }
                                } else if (lower.endsWith(".appx") || lower.endsWith(".msix")) {
                                    // 2. 深入解压各依赖包（如 VCLibs、WindowsAppRuntime）提取 dll，排除 arm
                                    if (!lower.contains("arm") && (lower.contains("x64") || lower.contains("neutral") || !lower.contains("x86"))) {
                                        val tempAppxFile = File(targetDir, "temp_dep_${System.currentTimeMillis()}_${File(entry.name).name}")
                                        try {
                                            depZip.getInputStream(entry).use { inStream ->
                                                FileOutputStream(tempAppxFile).use { outStream -> inStream.copyTo(outStream) }
                                            }
                                            if (tempAppxFile.exists()) {
                                                ZipFile(tempAppxFile).use { innerAppx ->
                                                    for (appxEntry in innerAppx.entries()) {
                                                        if (appxEntry.isDirectory || !appxEntry.name.endsWith(".dll", ignoreCase = true)) continue
                                                        val dllName = File(appxEntry.name).name
                                                        val outDll = File(targetDir, dllName)
                                                        if (!outDll.exists()) {
                                                            innerAppx.getInputStream(appxEntry).use { inStream ->
                                                                FileOutputStream(outDll).use { outStream -> inStream.copyTo(outStream) }
                                                            }
                                                            extractedDllCount++
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            runCatching { tempAppxFile.delete() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    emit("🔗 已提取并就绪 $extractedDllCount 个伴生运行库 DLL")
                }

                // 将 *_app.dll 映射复制一份标准名称 *.dll，确保独立目录运行时不依赖全局 VC++ UWP 运行库
                targetDir.listFiles { f -> f.isFile && f.name.endsWith("_app.dll", ignoreCase = true) }?.forEach { appDll ->
                    val standardName = appDll.name.replace("_app.dll", ".dll", ignoreCase = true)
                    val standardDll = File(targetDir, standardName)
                    if (!standardDll.exists()) {
                        runCatching { appDll.copyTo(standardDll, overwrite = false) }
                    }
                }

                val wingetExe = File(targetDir, "winget.exe")
                if (!wingetExe.exists()) {
                    emit("⚠️ 独立目录中未找到 winget.exe")
                    return false
                }

                emit("🔍 正在校验独立免安装 WinGet CLI 运行状态...")
                val verOutput = PythonEnvResolver.runQuickCommand(listOf(wingetExe.absolutePath, "--version"))
                if (verOutput != null) {
                    emit("🎉 独立免安装 WinGet CLI 激活成功！(版本: $verOutput)")
                    true
                } else {
                    emit("⚠️ 独立 winget.exe 启动自检未通过 (缺少关键运行时组件)")
                    false
                }
            } finally {
                if (tempMsixFile.exists()) {
                    runCatching { tempMsixFile.delete() }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解压独立 WinGet 运行环境失败: ${e.message}", e)
            emit("⚠️ 独立运行环境提取失败: ${e.message}")
            false
        }
    }

    /**
     * Windows 官方安装包多源测速择优下载与无感静默安装（对标 pip 优先复用本地下载缓存，支持实时下载速度展示）
     * 默认安装至 Windows 官方传统用户目录: %LOCALAPPDATA%\Programs\Python\Python312
     */
    private suspend fun FlowCollector<String>.installPythonOnWindowsViaDirectInstaller(preResolvedWinget: String? = null) {
        val installerFile = fetchOrDownloadPackage(
            packageName = "Python $PYTHON_WIN_VERSION 官方安装包 (amd64)",
            fileName = "python-$PYTHON_WIN_VERSION-amd64.exe",
            candidateSources = DownloadSourceSpeedTester.PYTHON_WIN64_INSTALLER_SOURCES,
            expectedVersion = PYTHON_WIN_VERSION
        )

        if (installerFile == null || !installerFile.exists()) {
            val wingetExe = preResolvedWinget ?: PythonEnvResolver.resolveWingetExecutable()
            if (wingetExe != null) {
                emit("⚠️ 官方安装包下载未成功，检测到系统 winget ($wingetExe)，正在尝试通过 winget 兜底安装...")
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

        try {
            val installDirMsg = "⚙️ 安装包已就绪 (${String.format(Locale.US, "%.1f", installerFile.length() / 1048576.0)} MB)，正在执行静默安装 (这可能需要 1~2 分钟，请稍候)..."
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
            val (exitOk, exitCode) = runProcessWithExitCode("python-installer", installCmd)
            val resolvedPython = PythonEnvResolver.resolvePythonExecutable()
            val resolvedVer = PythonEnvResolver.getPythonVersion()

            when {
                exitCode == 0 -> {
                    emit("✅ 官方安装程序执行完毕 (退出码: 0)")
                }
                exitCode == 3010 -> {
                    emit("⚠️ 官方安装程序执行成功，系统提示需重启生效 (退出码: 3010: ERROR_SUCCESS_REBOOT_REQUIRED)")
                }
                exitCode == 1602 -> {
                    emit("⚠️ 安装被用户取消或被安全策略终止 (退出码: 1602: ERROR_INSTALL_USEREXIT)")
                }
                exitCode == 1603 -> {
                    emit("❌ 官方安装程序遇到致命错误 (退出码: 1603: ERROR_INSTALL_FAILURE)，可能是安装包破损或缺少运行库")
                }
                exitCode == 1618 -> {
                    emit("❌ 另一个安装程序正在进行中，请等待其完成后重试 (退出码: 1618: ERROR_INSTALL_ALREADY_RUNNING)")
                }
                else -> {
                    emit("⚙️ 官方安装程序结束 (退出码: $exitCode)")
                }
            }

            if ((exitOk || exitCode == 3010) && resolvedPython != null) {
                val okMsg = "✅ python 安装完成！(版本: ${resolvedVer ?: PYTHON_WIN_VERSION}, 路径: $resolvedPython)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
            } else if (resolvedPython != null) {
                val okMsg = "✅ python 安装完成！(版本: ${resolvedVer ?: PYTHON_WIN_VERSION}, 路径: $resolvedPython)"
                AppLogger.i(TAG, okMsg)
                emit(okMsg)
            } else {
                val failMsg = "❌ python 静默安装未成功完成 (退出码: $exitCode)，系统标准目录中未检测到 python.exe"
                AppLogger.e(TAG, failMsg)
                emit(failMsg)
                emit("💡 提示：建议点击上方「官方下载页」按钮在浏览器中下载安装")
            }
        } catch (e: Exception) {
            val errMsg = "❌ Python 自动安装过程出错: ${e.message}"
            AppLogger.e(TAG, errMsg, e)
            emit(errMsg)
        }
        // 对标 pip 持久化下载缓存，不再在 finally 中删除 installerFile
    }

    /**
     * 流式下载文件（支持 HTTP Range 断点续传、同源原位 3 次自愈重试与 .part 临时文件原子提交）
     * 实时计算当前下载速度（MB/s 或 KB/s）向 UI 与 AppLogger 汇报进度
     */
    private suspend fun FlowCollector<String>.downloadFileWithProgress(urlStr: String, destFile: File): Boolean {
        val partFile = File(destFile.parentFile, destFile.name + ".part")
        val maxInPlaceRetries = 3
        var attempt = 0

        while (attempt < maxInPlaceRetries) {
            attempt++
            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L

                val connection = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeminiUIForge/1.0")
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }

                val code = connection.responseCode
                val isPartial = (code == 206)
                val isFullOk = (code == 200)

                // HTTP 416 Range Not Satisfiable: 说明已有文件已完整或超出服务端范围
                if (code == 416) {
                    if (partFile.exists() && partFile.length() > 0) {
                        AppLogger.i(TAG, "HTTP 416: 本地临时文件已完整，直接执行原子提交")
                        if (destFile.exists()) destFile.delete()
                        if (partFile.renameTo(destFile)) return true
                    }
                    partFile.delete()
                    continue
                }

                if (!isPartial && !isFullOk) {
                    AppLogger.e(TAG, "下载响应码异常: HTTP $code ($urlStr)")
                    if (attempt < maxInPlaceRetries) {
                        emit("🔄 响应异常 (HTTP $code)，1.5 秒后尝试重新连接 (第 $attempt/$maxInPlaceRetries 次)...")
                        delay(1500)
                        continue
                    }
                    return false
                }

                val appendMode = isPartial && existingBytes > 0L
                val currentDownloadedBytes = if (appendMode) existingBytes else 0L
                val totalBytes = if (isPartial) {
                    val contentRange = connection.getHeaderField("Content-Range")
                    val parsedTotal = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    parsedTotal ?: (existingBytes + connection.contentLengthLong)
                } else {
                    connection.contentLengthLong
                }

                if (!appendMode && partFile.exists()) {
                    partFile.delete()
                }

                if (appendMode) {
                    val resumeMb = String.format(Locale.US, "%.1f", existingBytes / 1048576.0)
                    val totalMb = String.format(Locale.US, "%.1f", totalBytes / 1048576.0)
                    val resumeMsg = "⚡ 触发 HTTP Range 断点续传，从 ${resumeMb} MB 处继续下载 (共 ${totalMb} MB)..."
                    AppLogger.i(TAG, resumeMsg)
                    emit(resumeMsg)
                }

                var downloadedBytes = currentDownloadedBytes
                var lastReportedStep = -1
                val startTimeMs = System.currentTimeMillis()
                var lastUiReportTimeMs = startTimeMs
                var lastUiReportBytes = downloadedBytes
                var streamCompleted = false

                connection.inputStream.use { input ->
                    FileOutputStream(partFile, appendMode).use { output ->
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
                                    ((downloadedBytes - currentDownloadedBytes) * 1000.0) / totalElapsed
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
                        output.flush()
                    }
                }

                streamCompleted = (totalBytes <= 0L || downloadedBytes >= totalBytes)

                if (streamCompleted && partFile.exists() && (totalBytes <= 0 || partFile.length() >= totalBytes)) {
                    if (destFile.exists()) destFile.delete()
                    if (partFile.renameTo(destFile)) {
                        AppLogger.i(TAG, "✅ 文件下载完成并原子提交: ${destFile.absolutePath} (${destFile.length()} 字节)")
                        return true
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "下载中途流中断 ($urlStr): ${e.message}")
                if (attempt < maxInPlaceRetries) {
                    val curMb = String.format(Locale.US, "%.1f", (if (partFile.exists()) partFile.length() else 0L) / 1048576.0)
                    emit("🔄 网络连接中断 (已断点保存 $curMb MB)，1.5 秒后原位断点续传 (第 $attempt/$maxInPlaceRetries 次)...")
                    delay(1500)
                    continue
                }
            }
        }
        return false
    }

    /**
     * 运行子进程并流式回显输出，同时返回 (是否成功 to 退出码)
     * 内置 PowerShell CLIXML 进度流泄露碎片防御与过滤
     */
    private suspend fun FlowCollector<String>.runProcessWithExitCode(
        tag: String,
        commandList: List<String>
    ): Pair<Boolean, Int> {
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
                // 彻底过滤 PowerShell 原生进度流泄漏的 CLIXML 序列化碎片
                if (currentLine.startsWith("#< CLIXML") ||
                    currentLine.contains("<Objs Version=") ||
                    currentLine.contains("<Obj RefId=") ||
                    currentLine.contains("<TN RefId=") ||
                    currentLine.contains("<ToString>")
                ) {
                    continue
                }
                if (currentLine.isNotEmpty()) {
                    AppLogger.d(TAG, "[$tag] $currentLine")
                    emit(currentLine)
                }
            }
            val exitCode = process.waitFor()
            AppLogger.i(TAG, "🏁 子命令 [$tag] 结束，退出码: $exitCode")
            (exitCode == 0) to exitCode
        } catch (e: Exception) {
            AppLogger.e(TAG, "⚠️ 子命令 [$tag] 执行异常: ${e.message}")
            false to -1
        }
    }

    /**
     * 运行子进程并流式回显输出，返回退出码是否为 0（内部子步骤使用，不直接打最终 ❌ 标签以免误触顶层中断）
     */
    private suspend fun FlowCollector<String>.runProcessAndEmitLogs(tag: String, commandList: List<String>): Boolean {
        return runProcessWithExitCode(tag, commandList).first
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
                if (currentLine.startsWith("#< CLIXML") ||
                    currentLine.contains("<Objs Version=") ||
                    currentLine.contains("<Obj RefId=") ||
                    currentLine.contains("<TN RefId=") ||
                    currentLine.contains("<ToString>")
                ) {
                    continue
                }
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
        if (!ensureElevationGate("卸载 Python 运行环境")) {
            return
        }
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

        // 若 winget 卸载后原 Python 可执行文件仍存在，或者 winget 部署未成功，准备切换注册表卸载通道时强制征询用户确认
        val stillExists = if (beforePythonExe != null && File(beforePythonExe).isAbsolute) {
            File(beforePythonExe).exists()
        } else {
            PythonEnvResolver.resolvePythonExecutable() != null
        }

        if (!uninstalledOk || stillExists) {
            val promptMsg = if (wingetExe != null) {
                "WinGet 未能完全卸载目标 Python，是否切换为 Windows 系统注册表静默卸载通道继续清理？"
            } else {
                "WinGet 部署未成功或在此 Windows 账户受限，是否切换为 Windows 系统注册表静默卸载通道继续清理？"
            }
            val reasonTitle = if (wingetExe != null) "WinGet 卸载 Python 过程受阻" else "WinGet 核心未能在此 Windows 账户中激活"
            emit("⚠️ $reasonTitle")
            emit("❓ 正在等待用户确认是否切换模式...")
            val userAgreed = EnvCliPromptCoordinator.awaitUserDecision("python", promptMsg)
            if (userAgreed) {
                emit("✅ 用户已授权：正在切换为 Windows 系统注册表卸载通道清理 Python ${beforeVersion ?: "3.x"}...")
                emit("════════ 切换模式: 使用 Windows 系统注册表卸载通道静默清理 ════════")
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
            } else {
                val cancelMsg = "❌ 用户取消切换模式：终止本次 Python 卸载操作。"
                AppLogger.i(TAG, cancelMsg)
                emit(cancelMsg)
                return
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
            listOf("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodePowerShellCommand(psScript))
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
