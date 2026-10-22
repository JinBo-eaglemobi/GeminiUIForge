package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.net.URI
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
    shouldStartDragAndDrop = { event ->
        org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "👉 shouldStartDragAndDrop 触发: $event")
        true
    },
    target = object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "🟢 onStarted")
            onDragStateChanged(true)
        }

        override fun onEntered(event: DragAndDropEvent) {
            org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "🟢 onEntered")
            onDragStateChanged(true)
        }

        override fun onMoved(event: DragAndDropEvent) {
            onDragStateChanged(true)
        }

        override fun onExited(event: DragAndDropEvent) {
            org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "⚪ onExited")
            onDragStateChanged(false)
        }

        override fun onEnded(event: DragAndDropEvent) {
            org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "⚪ onEnded")
            onDragStateChanged(false)
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "🎯 onDrop 触发")
            onDragStateChanged(false)
            return try {
                val paths = extractFilePaths(event)
                org.gemini.ui.forge.utils.AppLogger.d("FileDrop", "📦 成功解析文件路径列表 (${paths.size} 项): $paths")
                if (paths.isNotEmpty()) {
                    onFilesDropped(paths)
                    true
                } else {
                    false
                }
            } catch (e: Throwable) {
                org.gemini.ui.forge.utils.AppLogger.e("FileDrop", "❌ onDrop 提取异常: ${e.message}", e)
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

/**
 * 宿主窗口级原生文件拖放捕获挂载器（JVM/桌面端实现）
 *
 * 核心机制：
 * 遍历获取当前进程中所有可见的 [java.awt.Window]（涵盖主窗口 [JFrame] 与各级弹窗 [JDialog]），
 * 统一接入接管挂载 [java.awt.dnd.DropTarget]，在 dragEnter 与 dragOver 阶段向 Windows 操作系统
 * 显式发送 [DnDConstants.ACTION_COPY] 响应，从操作系统底层将禁止光标（🚫）消除，
 * 并在 drop 事件触发时安全解析文件物理绝对路径后回调；在 Composable 退出时完整还原原有 DropTarget。
 */
@Composable
actual fun AttachWindowDropTarget(
    onDragHoverChanged: (Boolean) -> Unit,
    onFilesDropped: (List<String>) -> Unit
) {
    val currentHover by rememberUpdatedState(onDragHoverChanged)
    val currentDrop by rememberUpdatedState(onFilesDropped)

    DisposableEffect(Unit) {
        val attachedComponents = mutableListOf<Pair<Component, DropTarget?>>()

        val createDropTarget: () -> DropTarget = {
            object : DropTarget() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    org.gemini.ui.forge.utils.AppLogger.i("FileDropAWT", "👉 AWT Window/Component dragEnter")
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        currentHover(true)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        currentHover(true)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragExit(dte: DropTargetEvent) {
                    org.gemini.ui.forge.utils.AppLogger.i("FileDropAWT", "⚪ AWT Window/Component dragExit")
                    currentHover(false)
                }

                override fun drop(dtde: DropTargetDropEvent) {
                    org.gemini.ui.forge.utils.AppLogger.i("FileDropAWT", "🎯 AWT Window/Component drop 触发！")
                    currentHover(false)
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val t = dtde.transferable
                        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            val fileList = t.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                            val paths = fileList?.filterIsInstance<File>()?.map { it.absolutePath } ?: emptyList()
                            org.gemini.ui.forge.utils.AppLogger.i("FileDropAWT", "📦 AWT Window/Component 提取文件成功: $paths")
                            if (paths.isNotEmpty()) {
                                currentDrop(paths)
                            }
                            dtde.dropComplete(true)
                        } else {
                            dtde.dropComplete(false)
                        }
                    } catch (e: Throwable) {
                        org.gemini.ui.forge.utils.AppLogger.e("FileDropAWT", "❌ AWT Window/Component drop 异常: ${e.message}", e)
                        dtde.dropComplete(false)
                    }
                }
            }
        }

        fun attachRecursive(comp: Component) {
            attachedComponents.add(comp to comp.dropTarget)
            comp.dropTarget = createDropTarget()
            if (comp is Container) {
                for (child in comp.components) {
                    attachRecursive(child)
                }
            }
        }

        // 在 EDT 线程稍后执行以确保新打开的 Dialog 已经完成 peer 创建与组件加载
        SwingUtilities.invokeLater {
            val allWindows = Window.getWindows()
            org.gemini.ui.forge.utils.AppLogger.i("FileDropAWT", "🔍 正在扫描系统窗口: 总数=${allWindows.size}")
            allWindows.forEach { win ->
                attachRecursive(win)
            }
        }

        onDispose {
            attachedComponents.forEach { (comp, origTarget) ->
                try {
                    comp.dropTarget = origTarget
                } catch (_: Throwable) {}
            }
        }
    }
}

/**
 * 原生文件拖放投放区域容器组件（JVM/桌面端实现）
 *
 * 底层基于官方 [SwingPanel] 嵌入一个纯透明的 [JPanel]，在 AWT 组件树中直接注册物理级 [DropTarget]。
 * 无论是主窗口还是各级 Dialog，鼠标拖拽经过该区域时均由操作系统和 AWT 直接接收处理，
 * 绝无任何禁止光标（🚫）与事件丢失。
 */
@Composable
actual fun FileDropZone(
    modifier: Modifier,
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit,
    content: @Composable () -> Unit
) {
    val currentHover by rememberUpdatedState(onDragStateChanged)
    val currentDrop by rememberUpdatedState(onFilesDropped)

    Box(modifier = modifier) {
        // 在底层嵌入真正的物理 SwingPanel (JPanel)，挂载 AWT DropTarget
        SwingPanel(
            modifier = Modifier.matchParentSize(),
            factory = {
                JPanel().apply {
                    isOpaque = false
                    dropTarget = object : DropTarget() {
                        override fun dragEnter(dtde: DropTargetDragEvent) {
                            org.gemini.ui.forge.utils.AppLogger.i("FileDropZone", "👉 SwingPanel dragEnter")
                            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                dtde.acceptDrag(DnDConstants.ACTION_COPY)
                                currentHover(true)
                            } else {
                                dtde.rejectDrag()
                            }
                        }

                        override fun dragOver(dtde: DropTargetDragEvent) {
                            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                dtde.acceptDrag(DnDConstants.ACTION_COPY)
                                currentHover(true)
                            } else {
                                dtde.rejectDrag()
                            }
                        }

                        override fun dragExit(dte: DropTargetEvent) {
                            org.gemini.ui.forge.utils.AppLogger.i("FileDropZone", "⚪ SwingPanel dragExit")
                            currentHover(false)
                        }

                        override fun drop(dtde: DropTargetDropEvent) {
                            org.gemini.ui.forge.utils.AppLogger.i("FileDropZone", "🎯 SwingPanel drop 触发！")
                            currentHover(false)
                            try {
                                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                                val t = dtde.transferable
                                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                    val fileList = t.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                                    val paths = fileList?.filterIsInstance<File>()?.map { it.absolutePath } ?: emptyList()
                                    org.gemini.ui.forge.utils.AppLogger.i("FileDropZone", "📦 SwingPanel 提取文件: $paths")
                                    if (paths.isNotEmpty()) {
                                        currentDrop(paths)
                                    }
                                    dtde.dropComplete(true)
                                } else {
                                    dtde.dropComplete(false)
                                }
                            } catch (e: Throwable) {
                                org.gemini.ui.forge.utils.AppLogger.e("FileDropZone", "❌ SwingPanel drop 异常: ${e.message}", e)
                                dtde.dropComplete(false)
                            }
                        }
                    }
                }
            },
            update = {
                // 保持透明
                it.isOpaque = false
            }
        )

        // 上层纯 Compose 内容视图呈现
        content()
    }
}
