package org.gemini.ui.forge

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

@OptIn(ExperimentalComposeUiApi::class, InternalResourceApi::class)
fun main() {

    ComposeViewport {
        var typography by remember { mutableStateOf<Typography?>(null) }
        if (typography == null) {
            val scope = rememberCoroutineScope()
            val defaultTypography = MaterialTheme.typography
            scope.launch {
                println(defaultTypography.bodyMedium.fontFamily)
                val byte = readResourceBytes("font/AlimamaShuHeiTi-Bold.woff2")
                val font = Font("Arial", byte)
                val fontFamily = font.toFontFamily()
                typography = Typography(
                    defaultTypography.displayLarge.copy(fontFamily = fontFamily),
                    defaultTypography.displayMedium.copy(fontFamily = fontFamily),
                    defaultTypography.displaySmall.copy(fontFamily = fontFamily),
                    defaultTypography.headlineLarge.copy(fontFamily = fontFamily),
                    defaultTypography.headlineMedium.copy(fontFamily = fontFamily),
                    defaultTypography.headlineSmall.copy(fontFamily = fontFamily),
                    defaultTypography.titleLarge.copy(fontFamily = fontFamily),
                    defaultTypography.titleMedium.copy(fontFamily = fontFamily),
                    defaultTypography.titleSmall.copy(fontFamily = fontFamily),
                    defaultTypography.bodyLarge.copy(fontFamily = fontFamily),
                    defaultTypography.bodyMedium.copy(fontFamily = fontFamily),
                    defaultTypography.bodySmall.copy(fontFamily = fontFamily),
                    defaultTypography.labelLarge.copy(fontFamily = fontFamily),
                    defaultTypography.labelMedium.copy(fontFamily = fontFamily),
                    defaultTypography.labelSmall.copy(fontFamily = fontFamily)
                )
            }
        } else {
            println(typography?.displayLarge?.fontFamily)
            App(typography!!)
        }
    }
}
