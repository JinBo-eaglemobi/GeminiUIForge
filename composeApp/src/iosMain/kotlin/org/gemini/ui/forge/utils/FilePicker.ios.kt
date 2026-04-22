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
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                val results = mutableListOf<String>()
                val dispatchGroup = dispatch_group_create()

                didFinishPicking.forEach { result ->
                    val pickerResult = result as PHPickerResult
                    val itemProvider = pickerResult.itemProvider
                    val uiImageClass = UIImage as Any as NSItemProviderReadingProtocol
                    if (itemProvider.canLoadObjectOfClass(uiImageClass)) {
                        dispatch_group_enter(dispatchGroup)
                        itemProvider.loadObjectOfClass(uiImageClass) { image, error ->
                            if (image != null && image is UIImage) {
                                val data = UIImagePNGRepresentation(image)
                                if (data != null) {
                                    val tempDir = NSTemporaryDirectory()
                                    val fileName = NSUUID().UUIDString() + ".png"
                                    val filePath = tempDir + fileName
                                    data.writeToFile(filePath, true)
                                    results.add(filePath)
                                }
                            }
                            dispatch_group_leave(dispatchGroup)
                        }
                    }
                }

                dispatch_group_notify(dispatchGroup, dispatch_get_main_queue()) {
                    onResult(results)
                }
            }
        }
    }

    return {
        val pickerConfig = PHPickerConfiguration()
        pickerConfig.selectionLimit = 0
        pickerConfig.filter = PHPickerFilter.imagesFilter()
        val picker = PHPickerViewController(pickerConfig)
        picker.delegate = delegate
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return rememberImagePicker(onResult)
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
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
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        picker.delegate = delegate
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
    }
}
