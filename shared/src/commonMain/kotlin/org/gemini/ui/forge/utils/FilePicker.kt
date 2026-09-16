package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path

/**
 * 跨平台文件/目录选择器启动函数。
 * 基于官方 FileKit 0.16.0 原生系统级对话框驱动。
 *
 * @param title 选择器对话框的标题。
 * @param isFolder 是否为文件夹选择模式。如果为 true 则选择文件夹；为 false 则选择物理文件。
 * @param extensions 可选参数，在选择文件模式下指定允许选取的文件后缀名列表（例如 listOf("png", "jpg")）。
 * @param initialPath 初始目录或文件路径（可选）。
 * @param onResult 选取结果回调，返回文件或文件夹的绝对路径（取消或失败时返回 null）。
 */
@Composable
fun rememberFilePicker(
    title: String = "选择文件",
    isFolder: Boolean = false,
    extensions: List<String> = emptyList(),
    initialPath: String? = null,
    onResult: (String?) -> Unit
): () -> Unit {
    val fileType = if (extensions.isEmpty()) {
        FileKitType.File()
    } else {
        FileKitType.File(extensions = extensions)
    }

    val fileLauncher = rememberFilePickerLauncher(
        type = fileType,
        mode = FileKitMode.Single,
        onResult = { file: PlatformFile? ->
            onResult(file?.path)
        }
    )

    val dirLauncher = rememberDirectoryPickerLauncher(
        onResult = { directory: PlatformFile? ->
            onResult(directory?.path)
        }
    )

    return {
        if (isFolder) {
            dirLauncher.launch()
        } else {
            fileLauncher.launch()
        }
    }
}
