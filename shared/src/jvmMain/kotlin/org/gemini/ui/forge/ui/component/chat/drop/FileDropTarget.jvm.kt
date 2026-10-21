package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI

/**
 * 桌面端 (JVM) 平台原生文件拖放监听修饰符实现。
 *
 * 核心机制与规范遵循：
 * 1. 深度对接 Compose 官方 [Modifier.dragAndDropTarget] 体系；
 * 2. 严格遵守 Compose Desktop 官方 AwtDragAndDropManager 契约：
 *    - 严禁在 [onDrop] 中重复调用 acceptDrop 或 dropComplete（AwtDragAndDropManager 已在外层统一受管）；
 * 3. 优先使用官方 [DragData.FilesList] 进行文件提取，兼备 AWT Transferable 多重兜底；
 * 4. 驱动悬停态（isHovering）与释放文件回调（onFilesDropped）。
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onFileDrop(
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit
): Modifier = this.dragAndDropTarget(
    shouldStartDragAndDrop = { _ ->
        // 无条件全量接纳外部拖入会话，彻底根除 Compose Desktop AwtDragAndDropManager
        // 在 dragEnter / dragOver 时由于 MIME 判断不匹配或严格校验而触发 dtde.rejectDrag()
        // 进而导致 Windows 操作系统在鼠标光标上呈现红色禁止图标 (🚫) 的致命缺陷。
        // 文件有效性校验完全后置至 onDrop 中精准闭环。
        true
    },
    target = object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            onDragStateChanged(true)
        }

        override fun onEntered(event: DragAndDropEvent) {
            onDragStateChanged(true)
        }

        override fun onMoved(event: DragAndDropEvent) {
            onDragStateChanged(true)
        }

        override fun onExited(event: DragAndDropEvent) {
            onDragStateChanged(false)
        }

        override fun onEnded(event: DragAndDropEvent) {
            onDragStateChanged(false)
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            onDragStateChanged(false)
            return try {
                val paths = extractFilePaths(event)
                if (paths.isNotEmpty()) {
                    onFilesDropped(paths)
                    true
                } else {
                    false
                }
            } catch (e: Throwable) {
                false
            }
        }
    }
)

/**
 * 从 Compose 官方 [DragAndDropEvent] 中安全提取本地文件的绝对物理路径。
 *
 * 实施三级容灾提取通道：
 * 1. Compose 官方 [DragData.FilesList] 高层抽象；
 * 2. AWT 标准 [DataFlavor.javaFileListFlavor]（List<File>）；
 * 3. AWT 备用 URI 列表 (text/uri-list) 与路径字符串解析。
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun extractFilePaths(event: DragAndDropEvent): List<String> {
    // 1. 优先尝试 Compose 官方 DragData.FilesList 抽象
    try {
        val dragData = event.dragData()
        if (dragData is DragData.FilesList) {
            val fileUris = dragData.readFiles()
            val parsedPaths = fileUris.mapNotNull { uriStr ->
                try {
                    val uri = URI.create(uriStr)
                    File(uri).absolutePath
                } catch (_: Throwable) {
                    val clean = uriStr.removePrefix("file://").removePrefix("file:")
                    if (clean.isNotBlank()) clean else null
                }
            }
            if (parsedPaths.isNotEmpty()) {
                return parsedPaths
            }
        }
    } catch (_: Throwable) {
        // 官方 dragData 解析失败时走 Transferable 兜底
    }

    // 2. 兜底尝试通过 AWT 原生 Transferable 提取文件列表
    try {
        val transferable = event.awtTransferable
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val rawFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor)
            val files = (rawFiles as? List<*>)?.filterIsInstance<File>() ?: emptyList()
            val paths = files.map { it.absolutePath }
            if (paths.isNotEmpty()) {
                return paths
            }
        }
    } catch (_: Throwable) {
        // 忽略异常，继续尝试备用 flavor
    }

    // 3. 第三重容灾：尝试解析 text/uri-list 或纯文本中的本地文件路径
    try {
        val transferable = event.awtTransferable
        for (flavor in transferable.transferDataFlavors) {
            if (flavor.mimeType.contains("uri-list", ignoreCase = true) ||
                flavor.isFlavorTextType
            ) {
                val data = transferable.getTransferData(flavor)?.toString() ?: continue
                val lines = data.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
                val parsed = lines.mapNotNull { line ->
                    try {
                        if (line.startsWith("file:", ignoreCase = true)) {
                            File(URI.create(line)).absolutePath
                        } else if (File(line).exists()) {
                            File(line).absolutePath
                        } else null
                    } catch (_: Throwable) {
                        val clean = line.removePrefix("file://").removePrefix("file:")
                        if (File(clean).exists()) clean else null
                    }
                }
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }
        }
    } catch (_: Throwable) {
        // 忽略
    }

    return emptyList()
}
