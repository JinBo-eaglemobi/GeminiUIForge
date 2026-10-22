package org.gemini.ui.forge.ui.component.chat.drop

import androidx.compose.ui.Modifier

/**
 * 跨平台文件拖放监听修饰符。
 *
 * @param onDragStateChanged 当外部文件拖入/离开悬停区域时的布尔值变化通知
 * @param onFilesDropped 当外部文件成功释放（Drop）时回调其绝对物理路径列表
 */
expect fun Modifier.onFileDrop(
    onDragStateChanged: (isHovering: Boolean) -> Unit,
    onFilesDropped: (filePaths: List<String>) -> Unit
): Modifier

/**
 * 宿主窗口级原生文件拖放捕获挂载器（多端自适应）
 *
 * 在桌面端（JVM/Windows），Compose 的 Dialog / Window 在没有注册原生 AWT DropTarget 时，
 * 操作系统文件管理器（Explorer）拖拽经过时会直接显示禁止图标 🚫。
 * 通过在 Composable 内部挂载 [AttachWindowDropTarget]，自动为宿主 AWT 窗口注册 DropTarget，
 * 向操作系统握手声明 acceptDrag(ACTION_COPY)，彻底消除禁止图标并直接捕获物理文件路径。
 */
@androidx.compose.runtime.Composable
expect fun AttachWindowDropTarget(
    onDragHoverChanged: (Boolean) -> Unit = {},
    onFilesDropped: (List<String>) -> Unit
)

/**
 * 原生文件拖放投放区域容器组件（多端自适应）
 *
 * 在桌面端（JVM）通过内嵌原生 AWT 物理组件承载层，
 * 为该区域建立操作系统级 OLE 拖放物理通道，彻底消除 Windows Explorer 拖入时的禁止图标（🚫），
 * 支持文件拖入悬停高亮与松开后物理绝对路径提取。
 */
@androidx.compose.runtime.Composable
expect fun FileDropZone(
    modifier: Modifier = Modifier,
    onDragStateChanged: (isHovering: Boolean) -> Unit = {},
    onFilesDropped: (filePaths: List<String>) -> Unit,
    content: @androidx.compose.runtime.Composable () -> Unit
)
