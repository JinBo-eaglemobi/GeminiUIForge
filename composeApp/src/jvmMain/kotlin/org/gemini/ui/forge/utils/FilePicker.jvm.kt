package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 全局静态预热，避免点击时才初始化 LookAndFeel 和 FileSystemView 导致卡顿
private var isLafSet = false
private fun ensureSystemLaf() {
    if (!isLafSet) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            isLafSet = true
        } catch (e: Exception) {}
    }
}

private var preWarmedChooser: JFileChooser? = null
private fun getWarmChooser(title: String): JFileChooser {
    ensureSystemLaf()
    if (preWarmedChooser == null) {
        preWarmedChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
    }
    // 每次弹出时应用新的标题（实现多语言实时切换）
    preWarmedChooser!!.dialogTitle = title
    return preWarmedChooser!!
}

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        // Run dialog picking on a background to avoid blocking the main UI event loop
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val dialog = FileDialog(activeWindow as? Frame, "Select Images", FileDialog.LOAD)
            dialog.isAlwaysOnTop = true // 永远置顶
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                val paths = files.map { it.absolutePath }
                // Call result callback
                onResult(paths)
            }
            dialog.dispose()
        }.start()
    }
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    // 界面加载时后台预热 JFileChooser
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            getWarmChooser(title)
        }
    }

    return {
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val chooser = getWarmChooser(title)
            
            // 创建一个临时的 JDialog 包装器并设置 isAlwaysOnTop
            val dialog = object : javax.swing.JDialog(activeWindow as? Frame, title, true) {}
            dialog.isAlwaysOnTop = true // 永远置顶
            
            val result = chooser.showOpenDialog(dialog)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFile = chooser.selectedFile
                if (selectedFile != null && selectedFile.exists()) {
                    onResult(selectedFile.absolutePath)
                } else {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
            dialog.dispose()
        }.start()
    }
}
