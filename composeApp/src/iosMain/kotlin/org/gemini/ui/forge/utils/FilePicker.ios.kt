package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.*
import platform.PhotosUI.*
import platform.Foundation.*
import platform.UniformTypeIdentifiers.*
import kotlinx.cinterop.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify
import org.gemini.ui.forge.data.TemplateFile

@Composable
actual fun rememberFilePicker(
    title: String,
    isFolder: Boolean,
    extensions: List<String>,
    initialPath: String?,
    onResult: (String?) -> Unit
): () -> Unit {
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                onResult(url?.path)
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onResult(null)
            }
        }
    }

    return {
        val picker = if (isFolder) {
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        } else {
            val contentTypes = if (extensions.isNotEmpty()) {
                extensions.mapNotNull { ext ->
                    when (ext) {
                        "js" -> UTType.typeWithFilenameExtension("js")
                        "json" -> UTTypeJSON
                        else -> UTTypeData
                    }
                }
            } else listOf(UTTypeData)
            UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = false)
        }
        if (initialPath != null) {
            picker.directoryURL = NSURL.fileURLWithPath(initialPath)
        }
        picker.delegate = delegate
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
    }
}
