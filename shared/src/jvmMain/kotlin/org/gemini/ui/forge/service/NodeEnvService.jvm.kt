package org.gemini.ui.forge.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.gameproject.NodeEnvStatus
import org.jetbrains.skiko.hostOs
import java.util.concurrent.TimeUnit

/**
 * 本地开发环境检测服务的 JVM（桌面端）实现。
 * 通过 ProcessBuilder 并发调用 node / npm / pnpm / git / python / java / gradle 命令行获取版本与配置信息。
 * 注意：Windows 上 npm/pnpm/gradle 多为 .cmd/.bat 脚本，无法被 ProcessBuilder 直接解析，
 * 必须经 cmd /c 包装调用。
 */
class JvmNodeEnvService : NodeEnvService {

    override suspend fun checkNodeEnv(): NodeEnvStatus = withContext(Dispatchers.IO) {
        // 使用 async 并发检测，消除串行进程阻塞，大幅压缩耗时
        val nodeDeferred = async { runCommand("node", "--version")?.removePrefix("v") }
        val npmDeferred = async { runPackageCommand("npm", "--version") }
        val registryDeferred = async { runPackageCommand("npm", "config", "get", "registry")?.takeIf { it.isNotBlank() } }
        val prefixDeferred = async { runPackageCommand("npm", "config", "get", "prefix")?.takeIf { it.isNotBlank() } }
        val pnpmDeferred = async { runPackageCommand("pnpm", "--version") }

        // Git 检测
        val gitDeferred = async {
            runCommand("git", "--version")?.let { raw ->
                // 输出形如 "git version 2.43.0.windows.1"
                raw.removePrefix("git version ").trim().substringBefore(" ")
            }
        }

        // Python & pip 检测
        val pythonDeferred = async {
            val py = runCommand("python", "--version") ?: runCommand("python3", "--version")
            py?.removePrefix("Python ")?.trim()
        }
        val pipDeferred = async {
            val pip = runPackageCommand("pip", "--version") ?: runPackageCommand("pip3", "--version")
            pip?.let { raw ->
                // 输出形如 "pip 24.0 from ..."
                val parts = raw.split(" ")
                if (parts.size >= 2) parts[1] else raw
            }
        }

        // Java 检测
        val javaDeferred = async {
            runCommand("java", "-version")?.let { raw ->
                // 输出形如 "openjdk version \"25.0.2\" ..." 或 "java version \"17.0.2\""
                val match = Regex("\"([^\"]+)\"").find(raw)
                match?.groupValues?.getOrNull(1) ?: raw.lineSequence().firstOrNull()?.trim()
            }
        }

        // Gradle 检测
        val gradleDeferred = async {
            runPackageCommand("gradle", "--version")?.let { raw ->
                // 搜索包含 "Gradle " 的行
                raw.lineSequence().firstOrNull { it.startsWith("Gradle ") }?.removePrefix("Gradle ")?.trim()
            }
        }

        val nodeVer = nodeDeferred.await()
        val npmVer = npmDeferred.await()
        val registry = registryDeferred.await()
        val prefix = prefixDeferred.await()
        val pnpmVer = pnpmDeferred.await()
        val gitVer = gitDeferred.await()
        val pythonVer = pythonDeferred.await()
        val pipVer = pipDeferred.await()
        val javaVer = javaDeferred.await()
        val gradleVer = gradleDeferred.await()

        NodeEnvStatus(
            nodeInstalled = nodeVer != null,
            nodeVersion = nodeVer,
            npmInstalled = npmVer != null,
            npmVersion = npmVer,
            npmRegistry = registry,
            npmPrefix = prefix,
            pnpmInstalled = pnpmVer != null,
            pnpmVersion = pnpmVer,
            gitInstalled = gitVer != null,
            gitVersion = gitVer,
            pythonInstalled = pythonVer != null,
            pythonVersion = pythonVer,
            pipInstalled = pipVer != null,
            pipVersion = pipVer,
            javaInstalled = javaVer != null,
            javaVersion = javaVer,
            gradleInstalled = gradleVer != null,
            gradleVersion = gradleVer
        )
    }

    /**
     * 执行可直接解析的可执行文件（node.exe, git.exe 等）。
     * @return 成功且输出非空时返回 trim 后的输出；否则 null
     */
    private fun runCommand(vararg command: String): String? = try {
        val process = ProcessBuilder(command.toList()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
    } catch (e: Exception) {
        null
    }

    /**
     * 执行 npm / pnpm / gradle 等命令：Windows 上经 cmd /c 包装以解析 .cmd/.bat 脚本。
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
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }
}

/** JVM 平台的服务实例化工厂实现 */
actual fun createNodeEnvService(): NodeEnvService = JvmNodeEnvService()
