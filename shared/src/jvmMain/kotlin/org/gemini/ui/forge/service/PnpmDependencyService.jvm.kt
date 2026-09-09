package org.gemini.ui.forge.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.gemini.ui.forge.model.gameproject.InstallChoice
import org.gemini.ui.forge.model.gameproject.PnpmDependencyStatus
import org.gemini.ui.forge.utils.looseJson
import org.jetbrains.skiko.hostOs
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * pnpm 依赖服务的 JVM（桌面端）实现。
 * 解析 package.json 声明依赖并检查 node_modules 中的实际安装情况，
 * 安装动作通过 cmd /c pnpm install（Windows）或 pnpm install 执行。
 */
class JvmPnpmDependencyService : PnpmDependencyService {

    override suspend fun checkDependencies(projectDir: String): List<PnpmDependencyStatus> =
        withContext(Dispatchers.IO) {
            val pkgFile = File(projectDir, "package.json")
            if (!pkgFile.exists()) return@withContext emptyList()
            val root = try {
                looseJson.parseToJsonElement(pkgFile.readText()).jsonObject
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            val result = mutableListOf<PnpmDependencyStatus>()
            // 依次解析 dependencies 与 devDependencies 两组声明
            for (group in listOf("dependencies", "devDependencies")) {
                val deps = (root[group] as? JsonObject) ?: continue
                deps.forEach { (name, versionElement) ->
                    val versionRange = (versionElement as? JsonPrimitive)?.content ?: ""
                    // pnpm 对直接依赖会在 node_modules 顶层创建符号链接，存在性检查即有效
                    val installed = File(projectDir, "node_modules/$name").exists()
                    result.add(
                        PnpmDependencyStatus(
                            name = name,
                            versionRange = versionRange,
                            isDev = group == "devDependencies",
                            installed = installed,
                            installChoice = InstallChoice.SKIP
                        )
                    )
                }
            }
            result.sortedBy { it.name }
        }

    override suspend fun installDependencies(projectDir: String, onLog: (String) -> Unit): String? =
        withContext(Dispatchers.IO) {
            val full = if (hostOs.isWindows) {
                listOf("cmd", "/c", "pnpm", "install")
            } else {
                listOf("pnpm", "install")
            }
            try {
                val process = ProcessBuilder(full).apply {
                    directory(File(projectDir))
                    redirectErrorStream(true)
                }.start()
                // Windows 下 cmd 工具链输出按 GBK 解码（与既有 EnvironmentCheckService 保持一致）
                val charset = if (hostOs.isWindows) Charset.forName("GBK") else Charsets.UTF_8
                val reader = process.inputStream.bufferedReader(charset)
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onLog(line!!)
                }
                val finished = process.waitFor(30, TimeUnit.MINUTES)
                if (finished && process.exitValue() == 0) null else "pnpm install 失败 (退出码 ${process.exitValue()})"
            } catch (e: Exception) {
                "pnpm 执行失败: ${e.message}"
            }
        }
}

/** JVM 平台的服务实例化工厂实现 */
actual fun createPnpmDependencyService(): PnpmDependencyService = JvmPnpmDependencyService()
