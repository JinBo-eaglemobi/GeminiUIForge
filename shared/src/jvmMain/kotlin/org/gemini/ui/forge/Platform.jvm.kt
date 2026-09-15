package org.gemini.ui.forge

import androidx.compose.ui.input.pointer.PointerIcon
import org.jetbrains.skiko.hostOs
import java.awt.Cursor
import java.awt.Desktop
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    
    override fun openInBrowser(url: String) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun openInFileExplorer(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) return

            val isDirectory = file.isDirectory
            val success = try {
                when {
                    // Windows: 如果是文件，尝试打开并高亮选中；如果是目录，直接打开。
                    hostOs.isWindows -> {
                        if (isDirectory) {
                            ProcessBuilder("explorer.exe", file.absolutePath).start()
                        } else {
                            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                        }
                        true
                    }
                    // macOS: 尝试打开并高亮选中文件
                    hostOs.isMacOS -> {
                        ProcessBuilder("open", "-R", file.absolutePath).start()
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                // 如果命令行调用失败（例如权限问题或路径特殊字符问题），抛出异常让后备方案接管
                false
            }

            // 如果特定平台的命令行执行失败，或者不是 Win/Mac，使用 Java 原生的 Desktop API 作为后备方案
            if (!success) {
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    // 尝试高亮选中 (Java 9+)
                    if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                        desktop.browseFileDirectory(file)
                    } else {
                        // 降级：仅打开所在的文件夹
                        desktop.open(if (isDirectory) file else file.parentFile)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun openCredentialStore() {
        try {
            when {
                // Windows：唤起控制面板的凭据管理器页
                hostOs.isWindows -> ProcessBuilder("control.exe", "/name", "Microsoft.CredentialManager").start()
                // macOS：打开钥匙串访问应用
                hostOs.isMacOS -> ProcessBuilder("open", "-a", "Keychain Access").start()
                else -> Unit
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun applyUpdateAndRestart(tempFilePath: String) {
        try {
            // 1. 获取当前程序路径
            val currentFile = File(System.getProperty("compose.application.resources.dir") ?: ".").parentFile
                ?.let { File(it, "GeminiUIForge.exe") } // 假设打包后的名称
                ?: File(JVMPlatform::class.java.protectionDomain.codeSource.location.toURI().path)
            
            val destPath = currentFile.absolutePath
            val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]

            if (hostOs.isWindows) {
                val batchFile = File.createTempFile("updater", ".bat")
                batchFile.writeText("""
                    @echo off
                    title GeminiUIForge Updater
                    :wait
                    tasklist /fi "pid eq $pid" | find ":" > nul
                    if errorlevel 1 (
                        timeout /t 1 /nobreak > nul
                        goto wait
                    )
                    copy /y "$tempFilePath" "$destPath"
                    start "" "$destPath"
                    del "%~f0"
                """.trimIndent(), charset("GBK"))
                
                ProcessBuilder("cmd", "/c", "start", "", batchFile.absolutePath).start()
            } else {
                val shFile = File.createTempFile("updater", ".sh")
                shFile.setExecutable(true)
                shFile.writeText("""
                    #!/bin/bash
                    while kill -0 $pid 2>/dev/null; do sleep 1; done
                    cp -f "$tempFilePath" "$destPath"
                    open "$destPath"
                    rm -- "${'$'}0"
                """.trimIndent())
                
                ProcessBuilder("bash", shFile.absolutePath).start()
            }

            // 2. 立即退出，让影子脚本接管
            exitProcess(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun getPlatform(): Platform = JVMPlatform()

actual val ResizeHorizontalIcon: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
actual val ResizeVerticalIcon: PointerIcon = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))

actual fun getProcessorCount(): Int = Runtime.getRuntime().availableProcessors()

actual val userHomePath: String = System.getProperty("user.home")

actual val runDir: String
    get() = System.getProperty("user.dir")

actual val appDir: String
    get() = try {
        val path = JVMPlatform::class.java.protectionDomain.codeSource.location.toURI().path
        val rawFile = java.io.File(path)

        // 1. 开发环境智能探测：若类文件处于构建输出目录（如 build/classes 或 out/production）
        // 则向上递归回溯定位当前代码直接所属的子模块工程根目录（如 shared/ 或 desktopApp/）
        val moduleRoot = findProjectRootInDev(rawFile)
        if (moduleRoot != null) {
            moduleRoot.absolutePath.replace("\\", "/").trimEnd('/')
        } else {
            // 2. 生产打包环境：应用已编译打包为真实二进制 EXE/MSI/JAR，返回该可执行包体所在的物理安装根目录
            val abs = if (rawFile.isFile) rawFile.parentFile.absolutePath else rawFile.absolutePath
            abs.replace("\\", "/").trimEnd('/')
        }
    } catch (_: Throwable) {
        System.getProperty("user.dir").replace("\\", "/").trimEnd('/') // 降级安全兜底
    }

/**
 * 在开发或测试运行环境下，从类文件物理加载路径一路向上回溯遍历父级目录，
 * 优先定位当前正在执行的直接所属模块根目录（以当前模块的 build.gradle.kts / build.gradle 为准）。
 */
private fun findProjectRootInDev(startFile: java.io.File): java.io.File? {
    var current: java.io.File? = if (startFile.isFile) startFile.parentFile else startFile
    while (current != null && current.exists()) {
        val hasBuildGradle = java.io.File(current, "build.gradle.kts").exists() ||
                java.io.File(current, "build.gradle").exists()
        val hasSettingsGradle = java.io.File(current, "settings.gradle.kts").exists() ||
                java.io.File(current, "settings.gradle").exists()

        // 优先命中第一个包含构建脚本的目录（即当前代码所属的直接子模块目录，如 shared/ 或 desktopApp/）
        if (hasBuildGradle || hasSettingsGradle) {
            return current
        }
        current = current.parentFile
    }
    return null
}

actual fun captureActiveScreenShot(): ByteArray? {
    return try {
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultScreen = ge.defaultScreenDevice
        val bounds = defaultScreen.defaultConfiguration.bounds
        val robot = java.awt.Robot()
        val screenCapture = robot.createScreenCapture(bounds)
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(screenCapture, "png", baos)
        baos.toByteArray()
    } catch (_: Throwable) {
        null
    }
}