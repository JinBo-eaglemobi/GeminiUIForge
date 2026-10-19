package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

/**
 * 桌面端 (JVM) 平台原生文件拖放监听修饰符实现。
 *
 * 核心机制：
 * 1. 深度对接 Compose 官方 [Modifier.dragAndDropTarget] 体系；
 * 2. 从 [DragAndDropEvent.nativeEvent] 提取 AWT 原生 [DropTargetDropEvent] / [DropTargetDragEvent]；
 * 3. 安全解析 [DataFlavor.javaFileListFlavor]，提取文件物理绝对路径列表；
 * 4. 驱动悬停态（isHovering）与释放文件回调（onFilesDropped）。
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onFileDrop(
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit
): Modifier = this.dragAndDropTarget(
    shouldStartDragAndDrop = { event ->
        val transferable = extractTransferable(event)
        transferable?.isDataFlavorSupported(DataFlavor.javaFileListFlavor) == true
    },
    target = object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            onDragStateChanged(true)
        }

        override fun onEntered(event: DragAndDropEvent) {
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
            val transferable = extractTransferable(event) ?: return false
            return try {
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    val rawFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor)
                    val files = (rawFiles as? List<*>)?.filterIsInstance<File>() ?: emptyList()
                    val paths = files.map { it.absolutePath }
                    if (paths.isNotEmpty()) {
                        onFilesDropped(paths)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
)

@OptIn(ExperimentalComposeUiApi::class)
private fun extractTransferable(event: DragAndDropEvent): Transferable? {
    val native = event.nativeEvent
    return when (native) {
        is DropTargetDropEvent -> native.transferable
        is DropTargetDragEvent -> native.transferable
        else -> null
    }
}
