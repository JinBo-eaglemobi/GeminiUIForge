package org.gemini.ui.forge.utils

import org.jetbrains.skiko.hostOs
import java.io.File

/**
 * 桌面端操作系统权限检测与自动提权调度助手
 *
 * 专职用于：
 * 1. 毫秒级探测当前运行环境是否具备操作系统最高管理员特权 (Administrator / Root)；
 * 2. 在普通受限权限下，通过操作系统原生接口（如 Windows UAC RunAs）拉起新进程完成代码级自提权 (Self-Elevation)；
 * 3. 防死循环提权保护 (GEMINI_FORGE_ELEVATED 状态标记)。
 */
object SystemElevationHelper {

    private const val ELEVATION_ENV_KEY = "GEMINI_FORGE_ELEVATED"
    const val RUN_AS_ADMIN_KEY = "RUN_AS_ADMIN"

    /**
     * 查询是否开启了“以管理员身份运行”全局开关
     *
     * 业务规范：
     * 1. 默认状态：出厂默认恒为 true（开箱即用享受最高特权）；
     * 2. 用户若显式关闭（export RUN_AS_ADMIN="false"），方返回 false。
     */
    fun isRunAsAdminEnabled(): Boolean {
        return try {
            val confFile = File(org.gemini.ui.forge.userHomePath, ".geminiuiforge/config.conf")
            if (!confFile.exists()) return true
            val line = confFile.readLines().firstOrNull { it.trim().startsWith("export $RUN_AS_ADMIN_KEY=") }
            val value = line?.substringAfter("=")?.trim()?.removeSurrounding("\"")
            // 只有显式写了 false 才禁用，其余一律默认 true
            value?.lowercase() != "false"
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 设置“以管理员身份运行”全局开关
     *
     * 同步持久化写入 ~/.geminiuiforge/config.conf
     */
    fun setRunAsAdminEnabled(enabled: Boolean) {
        try {
            val confFile = File(org.gemini.ui.forge.userHomePath, ".geminiuiforge/config.conf")
            if (!confFile.parentFile.exists()) {
                confFile.parentFile.mkdirs()
            }
            val lines = if (confFile.exists()) confFile.readLines() else emptyList()
            val newLines = mutableListOf<String>()
            val targetEntry = "export $RUN_AS_ADMIN_KEY=\"$enabled\""
            var found = false

            for (line in lines) {
                if (line.trim().startsWith("export $RUN_AS_ADMIN_KEY=")) {
                    newLines.add(targetEntry)
                    found = true
                } else {
                    newLines.add(line)
                }
            }
            if (!found) newLines.add(targetEntry)
            confFile.writeText(newLines.joinToString("\n"))
            AppLogger.i("SystemElevation", "📝 管理员身份运行开关已更新为: $enabled")
        } catch (e: Exception) {
            AppLogger.e("SystemElevation", "保存管理员配置失败: ${e.message}", e)
        }
    }

    /**
     * 检测当前进程是否已具备管理员特权
     *
     * @return 若具备管理员特权或非 Windows 平台返回 true；受限权限返回 false
     */
    fun isAdministrator(): Boolean {
        if (!hostOs.isWindows) {
            // macOS / Linux 下通过 uid 判断 (root 为 0)
            return try {
                val proc = ProcessBuilder("id", "-u").start()
                val uid = proc.inputStream.bufferedReader().readLine()?.trim()
                proc.waitFor()
                uid == "0"
            } catch (e: Exception) {
                true
            }
        }

        // Windows 平台：fltmc (筛选器管理器控制程序) 只有管理员身份方可正常执行，退出码为 0
        // 普通受限权限下退出码为 1 并抛出拒绝访问，耗时 < 30ms，纯 Windows 原生自带
        return try {
            val proc = ProcessBuilder("fltmc")
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            // 备用探测：net session 同样需要管理员权限
            try {
                val proc = ProcessBuilder("net", "session")
                    .redirectErrorStream(true)
                    .start()
                val exitCode = proc.waitFor()
                exitCode == 0
            } catch (e2: Exception) {
                // 若系统严重受限无法调用上述命令，则保守放行避免死锁
                false
            }
        }
    }

    /**
     * 在 Windows 环境下通过 UAC (RunAs) 请求管理员权限重新拉起自身
     *
     * @param args 当前进程启动参数
     * @return 是否成功拉起提权进程（若拉起成功，调用方应立即安全退出当前旧进程）
     */
    fun tryElevateSelfOnWindows(args: Array<String>): Boolean {
        if (!hostOs.isWindows) return false

        // 防无限循环守护：若已经处于提权尝试状态，坚决不再重复提权
        if (System.getenv(ELEVATION_ENV_KEY) == "1" || System.getProperty("gemini.forge.elevated") == "1") {
            AppLogger.w("SystemElevation", "⚠️ 检测到提权防环标记已激活，跳过重复提权请求")
            return false
        }

        // 获取当前程序入口路径 (jpackage 打包的 .exe 或当前 JVM 启动环境)
        val appPath = System.getProperty("jpackage.app-path")
            ?: System.getProperty("java.class.path")

        if (appPath == null) {
            AppLogger.w("SystemElevation", "⚠️ 无法获取当前程序路径，跳过自动提权")
            return false
        }

        return try {
            val isNativeExe = appPath.endsWith(".exe", ignoreCase = true)
            val powershellScript: String

            if (isNativeExe) {
                val argListStr = if (args.isNotEmpty()) {
                    args.joinToString(",") { "'${it.replace("'", "''")}'" }
                } else {
                    ""
                }
                powershellScript = buildString {
                    append("\$p = Start-Process -FilePath '$appPath' ")
                    if (argListStr.isNotBlank()) {
                        append("-ArgumentList @($argListStr) ")
                    }
                    append("-Verb RunAs -PassThru -ErrorAction Stop")
                }
            } else {
                // 开发环境或 Jar 启动环境
                val javaHome = System.getProperty("java.home")
                val javaExe = File(javaHome, "bin/java.exe").absolutePath
                val jvmArgs = mutableListOf<String>()
                jvmArgs.add("-cp")
                jvmArgs.add(appPath)
                jvmArgs.add("-Dgemini.forge.elevated=1")
                jvmArgs.add("org.gemini.ui.forge.MainKt")
                jvmArgs.addAll(args)

                val argListStr = jvmArgs.joinToString(",") { "'${it.replace("'", "''")}'" }
                powershellScript = "Start-Process -FilePath '$javaExe' -ArgumentList @($argListStr) -Verb RunAs -PassThru -ErrorAction Stop"
            }

            AppLogger.i("SystemElevation", "🚀 正在调起 Windows UAC 请求全局管理员权限...")
            val pb = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", powershellScript)
            pb.environment()[ELEVATION_ENV_KEY] = "1"
            val proc = pb.start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                AppLogger.i("SystemElevation", "✅ 已成功通过 UAC 拉起具有管理员权限的新进程！")
                true
            } else {
                val errorMsg = proc.errorStream.bufferedReader().readText().trim()
                AppLogger.w("SystemElevation", "⚠️ 用户取消了 UAC 管理员提权或提权执行受限 (Code: $exitCode): $errorMsg")
                false
            }
        } catch (e: Exception) {
            AppLogger.e("SystemElevation", "❌ 尝试管理员自提权发生异常: ${e.message}", e)
            false
        }
    }
}
