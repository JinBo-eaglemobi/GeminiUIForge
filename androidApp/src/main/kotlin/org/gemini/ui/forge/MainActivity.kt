package org.gemini.ui.forge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.gemini.ui.forge.utils.initAndroidLogConfig
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // 关键：初始化 Android 端的日志持久化路径 (Internal Storage)
        val logsDir = File(filesDir, "logs").apply { if (!exists()) mkdirs() }
        initAndroidLogConfig(logsDir.absolutePath)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}