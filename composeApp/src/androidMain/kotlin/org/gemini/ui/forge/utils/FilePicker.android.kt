package org.gemini.ui.forge.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import org.gemini.ui.forge.service.LocalFileStorage

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
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        // Note: Android SAF URIs don't convert directly to standard java.io.File paths without ContentResolver
        // Since LocalFileStorage uses java.io.File, changing dir this way is complex on Android.
        // We will pass the URI string back, but it likely won't work perfectly for java.io.File.
        // Usually on Android we fallback to returning null or we can try.
        if (uri != null) {
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    return {
        launcher.launch(null)
    }
}

@Composable
actual fun org.gemini.ui.forge.data.TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    // Android 系统文件选择器暂不支持指定起始本地目录，回退至标准选择器
    return rememberImagePicker(onResult)
}
