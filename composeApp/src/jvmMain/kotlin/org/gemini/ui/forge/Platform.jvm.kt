package org.gemini.ui.forge

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor
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

    override fun applyUpdateAndRestart(tempFilePath: String) {
        try {
            // 1. 获取当前程序路径
            val currentFile = File(System.getProperty("compose.application.resources.dir") ?: ".").parentFile
                ?.let { File(it, "GeminiUIForge.exe") } // 假设打包后的名称
                ?: File(JVMPlatform::class.java.protectionDomain.codeSource.location.toURI().path)
            
            val destPath = currentFile.absolutePath
            val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
            val os = System.getProperty("os.name").lowercase()

            if (os.contains("win")) {
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
