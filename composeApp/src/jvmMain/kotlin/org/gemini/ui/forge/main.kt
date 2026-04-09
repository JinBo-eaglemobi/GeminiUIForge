package org.gemini.ui.forge

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.gemini.ui.forge.service.ConfigManager

fun main() {
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
            title = "GeminiUIForge",
            state = windowState
        ) {
            App()
        }
    }
}
