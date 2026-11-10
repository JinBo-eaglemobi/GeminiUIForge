package org.gemini.ui.forge.service.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.utils.AppLogger
import org.jetbrains.skiko.hostOs
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 游戏运行环境管理器 (JVM 平台)。
 * 负责检测 Node.js、npm、pnpm 运行环境，若缺失则自动执行环境自愈与安装。
 */
object GameEnvironmentManager {

    private const val TAG = "GameEnvironmentManager"

    data class EnvStatus(
        val nodeAvailable: Boolean,
        val nodeVersion: String?,
        val pnpmAvailable: Boolean,
        val pnpmVersion: String?
    )

    /**
     * 快速检测当前系统是否已具备 Node.js 和 pnpm。
     */
    suspend fun checkEnvironment(): EnvStatus = withContext(Dispatchers.IO) {
        val nodeVersion = runQuickCommand(if (hostOs.isWindows) listOf("cmd", "/c", "node", "-v") else listOf("node", "-v"))
            ?.removePrefix("v")?.trim()
        val pnpmVersion = runQuickCommand(if (hostOs.isWindows) listOf("cmd", "/c", "pnpm", "-v") else listOf("pnpm", "-v"))
            ?.removePrefix("v")?.trim()

        EnvStatus(
            nodeAvailable = !nodeVersion.isNullOrBlank(),
            nodeVersion = nodeVersion,
            pnpmAvailable = !pnpmVersion.isNullOrBlank(),
            pnpmVersion = pnpmVersion
        )
    }

    /**
     * 确保环境就绪。若未安装 Node.js/pnpm，则全自动为用户安装。
     *
     * @param onLog 实时安装与自检日志回调
     * @return true 表示环境已就绪，false 表示环境安装失败
     */
    suspend fun ensureEnvironment(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onLog("🔍 正在检查 Node.js / pnpm 运行环境...")
        var status = checkEnvironment()

        if (status.nodeAvailable && status.pnpmAvailable) {
            onLog("✅ 环境已就绪: Node.js v${status.nodeVersion}, pnpm v${status.pnpmVersion}")
            return@withContext true
        }

        // 1. 若 Node.js 缺失，尝试自动安装
        if (!status.nodeAvailable) {
            onLog("⚠️ 未检测到 Node.js，正在尝试自动安装 Node.js LTS 运行环境...")
            val nodeInstalledOk = installNodeJs(onLog)
            if (!nodeInstalledOk) {
                onLog("❌ Node.js 自动安装失败，请手动安装 Node.js (https://nodejs.org/) 后重试")
                return@withContext false
            }
            onLog("✅ Node.js 安装成功，重新检测...")
        }

        // 2. 若 Node.js 可用但 pnpm 缺失，通过 npm install -g pnpm 自动安装
        status = checkEnvironment()
        if (!status.pnpmAvailable) {
            onLog("⚠️ 未检测到 pnpm，正在通过 npm 自动安装 pnpm...")
            val pnpmInstalledOk = installPnpmViaNpm(onLog)
            if (!pnpmInstalledOk) {
                onLog("❌ pnpm 自动安装失败，请手动执行 `npm install -g pnpm` 后重试")
                return@withContext false
            }
            onLog("✅ pnpm 安装成功！")
        }

        // 3. 最终确认
        status = checkEnvironment()
        val isReady = status.nodeAvailable && status.pnpmAvailable
        if (isReady) {
            onLog("🎉 全套环境自愈完成: Node.js v${status.nodeVersion}, pnpm v${status.pnpmVersion}")
        } else {
            onLog("❌ 最终环境自检未完全通过: Node=${status.nodeAvailable}, pnpm=${status.pnpmAvailable}")
        }
        isReady
    }

    /**
     * 在 Windows 下通过 winget 或直接命令行尝试安装 Node.js LTS
     */
    private suspend fun installNodeJs(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (hostOs.isWindows) {
            val wingetCheck = runQuickCommand(listOf("cmd", "/c", "winget", "--version"))
            if (!wingetCheck.isNullOrBlank()) {
                onLog("📦 检测到 Windows 软件包管理器 (winget)，正在通过 winget 静默安装 Node.js LTS...")
                val (ok, _) = runProcess(
                    workDir = null,
                    command = listOf(
                        "cmd", "/c", "winget", "install", "OpenJS.NodeJS.LTS",
                        "--silent", "--accept-source-agreements", "--accept-package-agreements"
                    ),
                    onLog = onLog
                )
                if (ok) return@withContext true
            }
        }
        false
    }

    /**
     * 通过 npm install -g pnpm 安装 pnpm
     */
    private suspend fun installPnpmViaNpm(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val (ok, _) = runProcess(
            workDir = null,
            command = if (hostOs.isWindows) {
                listOf("cmd", "/c", "npm", "install", "-g", "pnpm")
            } else {
                listOf("npm", "install", "-g", "pnpm")
            },
            onLog = onLog
        )
        ok
    }

    /**
     * 通用进程执行工具，支持捕获控制台输出并通过 onLog 流式回调
     */
    suspend fun runProcess(
        workDir: File?,
        command: List<String>,
        timeoutMinutes: Long = 10,
        onLog: (String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Running process in ${workDir?.absolutePath ?: "CWD"}: ${command.joinToString(" ")}")
        try {
            val pb = ProcessBuilder(command).apply {
                if (workDir != null) directory(workDir)
                redirectErrorStream(true)
                // 统一强制现代命令行工具（node/pnpm/git）输出 UTF-8 编码，杜绝 Windows 控制台符号与边框乱码
                environment()["LANG"] = "en_US.UTF-8"
                environment()["LC_ALL"] = "en_US.UTF-8"
                environment()["PYTHONIOENCODING"] = "utf-8"
            }
            val process = pb.start()
            // 现代开发工具链（Node.js/npm/pnpm）默认均以 UTF-8 输出流，统一采用 UTF-8 解析杜绝 Unicode 符号与边框乱码
            val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
            val fullOutput = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line!!
                fullOutput.appendLine(text)
                onLog(text)
            }
            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
            val exitCode = if (finished) process.exitValue() else -1
            if (finished && exitCode == 0) {
                Pair(true, fullOutput.toString())
            } else {
                Pair(false, "Process failed with exit code $exitCode\n$fullOutput")
            }
        } catch (e: Exception) {
            val errorMsg = "Process exception: ${e.message}"
            AppLogger.e(TAG, errorMsg, e)
            onLog("❌ $errorMsg")
            Pair(false, errorMsg)
        }
    }

    private fun runQuickCommand(command: List<String>): String? = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(8, TimeUnit.SECONDS)
        if (finished && process.exitValue() == 0 && text.isNotEmpty()) text else null
    } catch (e: Exception) {
        null
    }
}
