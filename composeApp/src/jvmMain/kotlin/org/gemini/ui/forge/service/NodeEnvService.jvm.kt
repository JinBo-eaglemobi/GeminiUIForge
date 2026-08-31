package org.gemini.ui.forge.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.gameproject.NodeEnvStatus
import org.jetbrains.skiko.hostOs
import java.util.concurrent.TimeUnit

/**
 * Node.js 环境检测服务的 JVM（桌面端）实现。
 * 通过 ProcessBuilder 调用 node / npm / pnpm 命令行获取版本与配置信息。
 * 注意：Windows 上 npm/pnpm 多为 .cmd 脚本，无法被 ProcessBuilder 直接解析，
 * 必须经 cmd /c 包装调用。
 */
class JvmNodeEnvService : NodeEnvService {

    override suspend fun checkNodeEnv(): NodeEnvStatus = withContext(Dispatchers.IO) {
        val nodeVer = runCommand("node", "--version")?.removePrefix("v")
        val npmVer = runPackageCommand("npm", "--version")
        val registry = runPackageCommand("npm", "config", "get", "registry")?.takeIf { it.isNotBlank() }
        val prefix = runPackageCommand("npm", "config", "get", "prefix")?.takeIf { it.isNotBlank() }
        val pnpmVer = runPackageCommand("pnpm", "--version")
        NodeEnvStatus(
            nodeInstalled = nodeVer != null,
            nodeVersion = nodeVer,
            npmInstalled = npmVer != null,
            npmVersion = npmVer,
            npmRegistry = registry,
            npmPrefix = prefix,
            pnpmInstalled = pnpmVer != null,
            pnpmVersion = pnpmVer
        )
    }

    /**
     * 执行可直接解析的可执行文件（node.exe 等）。
     * @return 成功且输出非空时返回 trim 后的输出；否则 null
     */
    private fun runCommand(vararg command: String): String? = try {
        val process = ProcessBuilder(command.toList()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
    } catch (e: Exception) {
        null
    }

    /**
     * 执行 npm / pnpm 命令：Windows 上经 cmd /c 包装以解析 .cmd 脚本。
     */
    private fun runPackageCommand(tool: String, vararg args: String): String? {
        val full = if (hostOs.isWindows) {
            listOf("cmd", "/c", tool) + args.toList()
        } else {
            listOf(tool) + args.toList()
        }
        return try {
            val process = ProcessBuilder(full).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }
}

/** JVM 平台的服务实例化工厂实现 */
actual fun createNodeEnvService(): NodeEnvService = JvmNodeEnvService()
