package org.gemini.ui.forge

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.gemini.ui.forge.manager.ConfigManager
import org.jetbrains.skiko.hostOs
import java.io.File
import kotlin.math.abs

fun main(args: Array<String>) {
    // 1. 尝试实现内存自举拦截 (Trampoline)
    // 检查是否存在用户的 .vmoptions 文件，如果是生产打包环境，并且内存不符合预期，则注入环境变量重启
    val vmOptionsFile = File(userHomePath, ".geminiuiforge/app.vmoptions")
    if (vmOptionsFile.exists()) {
        try {
            val lines = vmOptionsFile.readLines()
            val xmxLine = lines.firstOrNull { it.trim().startsWith("-Xmx") }?.trim()
            if (xmxLine != null) {
                // 简单估算目标内存 (GB为单位)
                val targetValueStr = xmxLine.removePrefix("-Xmx")
                val isG = targetValueStr.endsWith("G", ignoreCase = true)
                val isM = targetValueStr.endsWith("M", ignoreCase = true)
                val num = targetValueStr.dropLast(1).toLongOrNull() ?: 2L
                
                val targetBytes = when {
                    isG -> num * 1024 * 1024 * 1024
                    isM -> num * 1024 * 1024
                    else -> 2L * 1024 * 1024 * 1024
                }

                val currentMaxBytes = Runtime.getRuntime().maxMemory()
                
                // 允许 10% 的误差，因为底层分配可能略微不一致
                val diffRatio = abs(currentMaxBytes - targetBytes).toDouble() / targetBytes
                
                // 如果当前进程的最大内存与配置的目标内存差异较大 (> 10%)，且没有设置特殊的防循环标记
                if (diffRatio > 0.1 && System.getenv("GEMINI_FORGE_TRAMPOLINE") != "1") {
                    // 获取当前运行的执行文件路径（jpackage 打包的 .exe 或 .jar）
                    val appPath = System.getProperty("jpackage.app-path") 
                        ?: System.getProperty("java.class.path")

                    if (appPath != null) {
                        val command = mutableListOf<String>()
                        if (appPath.endsWith(".exe", ignoreCase = true) || appPath.endsWith(".app", ignoreCase = true)) {
                            command.add(appPath)
                        } else {
                            // 开发环境或纯 jar 环境
                            val javaHome = System.getProperty("java.home")
                            val javaBin = java.io.File(javaHome, "bin/java" + (if (hostOs.isWindows) ".exe" else "")).absolutePath
                            command.add(javaBin)
                            command.add("-cp")
                            command.add(appPath)
                            command.add("org.gemini.ui.forge.MainKt")
                        }

                        command.addAll(args)

                        val pb = ProcessBuilder(command)
                        val env = pb.environment()
                        // 使用 JDK_JAVA_OPTIONS 将自定义内存参数注入子进程
                        env["JDK_JAVA_OPTIONS"] = lines.joinToString(" ")
                        // 设置防无限循环标记
                        env["GEMINI_FORGE_TRAMPOLINE"] = "1"
                        
                        pb.start()
                        kotlin.system.exitProcess(0)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val configManager = ConfigManager()

    // 同步加载上次保存的窗口状态
    val savedWidth = runBlocking { configManager.loadKey("WINDOW_WIDTH") }?.toFloatOrNull() ?: 1280f
    val savedHeight = runBlocking { configManager.loadKey("WINDOW_HEIGHT") }?.toFloatOrNull() ?: 800f
    val isMaximized = runBlocking { configManager.loadKey("WINDOW_MAXIMIZED") } == "true"
    
    application {
        val windowState = rememberWindowState(
            placement = if (isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize(savedWidth.dp, savedHeight.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
        )

        // 监听窗口尺寸变化，只在非全屏时实时保存尺寸
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .filter { windowState.placement == WindowPlacement.Floating }
                .collect { size ->
                    configManager.saveKey("WINDOW_WIDTH", size.width.value.toString())
                    configManager.saveKey("WINDOW_HEIGHT", size.height.value.toString())
                }
        }

        Window(
            onCloseRequest = {
                // 关闭前只保存当前是否全屏的状态
                runBlocking {
                    val currentIsMaximized = windowState.placement == WindowPlacement.Maximized
                    configManager.saveKey("WINDOW_MAXIMIZED", currentIsMaximized.toString())
                }
                exitApplication()
            },
            title = "Gemini UI Forge v${ProjectConfig.VERSION}",
            state = windowState
        ) {
            App()
        }
    }
}
