package org.gemini.ui.forge.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import org.gemini.ui.forge.utils.LocalFileStorage

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            onResult(uris.map { it.toString() })
        }
    }
    return {
        // Only pick images
        launcher.launch("image/*")
    }
}

@Composable
actual fun rememberFilePicker(
    title: String,
    isFolder: Boolean,
    extensions: List<String>,
    onResult: (String?) -> Unit
): () -> Unit {
    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    return {
        if (isFolder) {
            dirLauncher.launch(null)
        } else {
            val mimeType = if (extensions.isNotEmpty()) {
                when (extensions.first()) {
                    "js" -> "application/javascript"
                    "json" -> "application/json"
                    else -> "*/*"
                }
            } else "*/*"
            fileLauncher.launch(mimeType)
        }
    }
}

@Composable
actual fun org.gemini.ui.forge.data.TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    // Android 系统文件选择器暂不支持指定起始本地目录，回退至标准选择器
    return rememberImagePicker(onResult)
}
