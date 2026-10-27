package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path

/**
 * 跨平台多文件选择器启动函数（系统对话框支持 Ctrl/Shift 多选）。
 *
 * Windows 文件对话框在多选模式下会原样返回被选中的文件与文件夹条目（扩展名过滤器仅作提示不拦截目录），
 * 因此单入口即可覆盖三种选取形态：多图片文件、多文件夹、文件与文件夹混合选取。
 *
 * @param title 选择器对话框的标题。
 * @param extensions 允许选取的文件后缀名列表（例如 listOf("png", "jpg")，空列表表示不过滤）。
 * @param onResult 选取结果回调，返回文件/文件夹绝对路径列表（取消或失败时返回空列表）。
 */
@Composable
fun rememberMultiFilePicker(
    title: String = "选择文件",
    extensions: List<String> = emptyList(),
    onResult: (List<String>) -> Unit
): () -> Unit {
    val fileType = if (extensions.isEmpty()) {
        FileKitType.File()
    } else {
        FileKitType.File(extensions = extensions)
    }

    val multiLauncher = rememberFilePickerLauncher(
        type = fileType,
        mode = FileKitMode.Multiple(),
        onResult = { files: List<PlatformFile>? ->
            onResult(files?.map { it.path } ?: emptyList())
        }
    )

    return {
        multiLauncher.launch()
    }
}
