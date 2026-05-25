package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable

/**
 * A cross-platform composable that remembers an image picker launcher.
 * Invoking the returned function launches the platform-specific file picker dialog.
 */
@Composable
expect fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit

/**
 * 支持指定初始目录的图片选择器 (扩展于 TemplateFile)
 */
@Composable
expect fun org.gemini.ui.forge.data.TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit

/**
 * 跨平台组件：返回一个打开文件/文件夹选择器的启动函数。
 * 执行该函数将唤起各平台原生的选择弹窗。
 * @param title 选择器对话框的标题。
 * @param isFolder 是否为文件夹选择模式。如果为 true 则选择文件夹；为 false 则选择物理文件。
 * @param extensions 可选参数，在选择文件模式下指定允许选取的文件后缀名列表（例如 listOf("js", "ts")）。
 */
@Composable
expect fun rememberFilePicker(
    title: String,
    isFolder: Boolean = false,
    extensions: List<String> = emptyList(),
    onResult: (String?) -> Unit
): () -> Unit

