package org.gemini.ui.forge

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "GeminiUIForge",
        state = rememberWindowState(
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            size = DpSize(1024.dp, 768.dp)
        )
    ) {
        App()
    }
}
