package org.gemini.ui.forge

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import geminiuiforge.composeapp.generated.resources.AlimamaShuHeiTi_Bold
import geminiuiforge.composeapp.generated.resources.Res

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded(Res.font.AlimamaShuHeiTi_Bold) {
            App()
        }
    }
}
