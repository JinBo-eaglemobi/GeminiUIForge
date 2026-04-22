package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.data.TemplateFile

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
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val dialog = FileDialog(activeWindow as? Frame, "Select Images", FileDialog.LOAD)
            dialog.isAlwaysOnTop = true
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                onResult(files.map { it.absolutePath })
            }
            dialog.dispose()
        }.start()
    }
}

@Composable
actual fun TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val initialDir = this.getAbsolutePath()
    return {
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val dialog = FileDialog(activeWindow as? Frame, "Select Images", FileDialog.LOAD)
            dialog.isAlwaysOnTop = true
            dialog.directory = initialDir
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                onResult(files.map { it.absolutePath })
            }
            dialog.dispose()
        }.start()
    }
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            getWarmChooser(title)
        }
    }

    return {
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val chooser = getWarmChooser(title)
            val dialog = object : javax.swing.JDialog(activeWindow as? Frame, title, true) {}
            dialog.isAlwaysOnTop = true
            
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
