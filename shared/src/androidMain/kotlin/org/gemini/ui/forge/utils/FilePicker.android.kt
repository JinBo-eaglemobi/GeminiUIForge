package org.gemini.ui.forge.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.net.toUri

private class OpenDocumentWithInitialUri : ActivityResultContract<Pair<List<String>, Uri?>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<List<String>, Uri?>): Intent {
        val mimeTypes = input.first
        val initialUri = input.second
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            if (mimeTypes.isNotEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            }
            if (initialUri != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
    }
}

@Composable
actual fun rememberFilePicker(
    title: String,
    isFolder: Boolean,
    extensions: List<String>,
    initialPath: String?,
    onResult: (String?) -> Unit
): () -> Unit {
    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(OpenDocumentWithInitialUri()) { uri: Uri? ->
        if (uri != null) {
            onResult(uri.toString())
        } else {
            onResult(null)
        }
    }
    return {
        val initialUri = if (!initialPath.isNullOrEmpty()) {
            try {
                initialPath.toUri()
            } catch (_: Exception) {
                null
            }
        } else null

        if (isFolder) {
            dirLauncher.launch(initialUri)
        } else {
            val mimeTypes = if (extensions.isNotEmpty()) {
                extensions.map {
                    when (it) {
                        "js" -> "application/javascript"
                        "json" -> "application/json"
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        else -> "*/*"
                    }
                }
            } else listOf("*/*")
            fileLauncher.launch(Pair(mimeTypes, initialUri))
        }
    }
}
