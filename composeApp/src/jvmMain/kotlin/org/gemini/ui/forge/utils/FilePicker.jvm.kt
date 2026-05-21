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
            dialog.layout = java.awt.BorderLayout()

            // 创建顶部地址栏面板
            val pathField = javax.swing.JTextField()
            val pathPanel = javax.swing.JPanel(java.awt.BorderLayout())
            // 添加左右内边距让其稍微美观一点
            pathPanel.border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
            pathPanel.add(javax.swing.JLabel(" 路径 (Path): "), java.awt.BorderLayout.WEST)
            pathPanel.add(pathField, java.awt.BorderLayout.CENTER)
            val goBtn = javax.swing.JButton("前往 (Go)")
            pathPanel.add(goBtn, java.awt.BorderLayout.EAST)
            
            // 导航逻辑
            val navigateAction = { _: java.awt.event.ActionEvent? ->
                val f = java.io.File(pathField.text)
                if (f.exists() && f.isDirectory) {
                    chooser.currentDirectory = f
                } else if (f.exists() && f.isFile) {
                    chooser.currentDirectory = f.parentFile
                }
            }
            pathField.addActionListener(navigateAction)
            goBtn.addActionListener(navigateAction)

            // 同步 JFileChooser 的路径改变到地址栏
            val propListener = java.beans.PropertyChangeListener { evt ->
                if (JFileChooser.DIRECTORY_CHANGED_PROPERTY == evt.propertyName) {
                    val dir = evt.newValue as? java.io.File
                    if (dir != null) {
                        pathField.text = dir.absolutePath
                    }
                }
            }
            chooser.addPropertyChangeListener(propListener)
            
            // 初始化地址栏内容
            pathField.text = chooser.currentDirectory?.absolutePath ?: ""

            var selectedPath: String? = null

            // 监听确认或取消按钮
            val actionListener = java.awt.event.ActionListener { evt ->
                if (evt.actionCommand == JFileChooser.APPROVE_SELECTION) {
                    val selectedFile = chooser.selectedFile
                    if (selectedFile != null && selectedFile.exists()) {
                        selectedPath = selectedFile.absolutePath
                    }
                }
                dialog.dispose()
            }
            chooser.addActionListener(actionListener)

            dialog.add(pathPanel, java.awt.BorderLayout.NORTH)
            dialog.add(chooser, java.awt.BorderLayout.CENTER)
            dialog.pack()
            
            // 设置最小宽度以防太窄
            if (dialog.width < 600) {
                dialog.setSize(600, dialog.height)
            }
            dialog.setLocationRelativeTo(activeWindow)
            
            dialog.isVisible = true

            // 弹窗关闭后清理监听器，防止单例内存泄漏或重复触发
            chooser.removePropertyChangeListener(propListener)
            chooser.removeActionListener(actionListener)
            
            onResult(selectedPath)
        }.start()
    }
}

@Composable
actual fun rememberFilePicker(title: String, extensions: List<String>, onResult: (String?) -> Unit): () -> Unit {
    return {
        Thread {
            val activeWindow = java.awt.Window.getWindows().firstOrNull { it.isActive }
            val dialog = FileDialog(activeWindow as? Frame, title, FileDialog.LOAD)
            dialog.isAlwaysOnTop = true
            if (extensions.isNotEmpty()) {
                // 为 AWT FileDialog 简化处理扩展名过滤
                dialog.file = extensions.joinToString(";") { "*.$it" }
            }
            dialog.isVisible = true
            val file = if (dialog.file != null) {
                val dir = dialog.directory
                val fileName = dialog.file
                if (dir != null && fileName != null) {
                    java.io.File(dir, fileName).absolutePath
                } else null
            } else null
            onResult(file)
            dialog.dispose()
        }.start()
    }
}
