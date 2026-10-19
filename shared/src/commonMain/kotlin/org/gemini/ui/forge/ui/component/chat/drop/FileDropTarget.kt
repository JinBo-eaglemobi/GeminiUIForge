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
