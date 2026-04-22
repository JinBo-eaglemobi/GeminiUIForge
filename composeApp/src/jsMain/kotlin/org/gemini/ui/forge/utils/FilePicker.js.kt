package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.service.LocalFileStorage
import org.gemini.ui.forge.getCurrentTimeMillis
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/png, image/jpeg, image/webp"
        input.multiple = true
        
        input.onchange = {
            val files = input.files
            if (files != null && files.length > 0) {
                MainScope().launch {
                    val resultPaths = mutableListOf<String>()
                    val storage = LocalFileStorage()
                    for (i in 0 until files.length) {
                        val file = files.item(i)
                        if (file != null) {
                            val bytes = readFileAsByteArray(file)
                            val timestamp = getCurrentTimeMillis()
                            val opfsPath = "imports/img_${timestamp}_${file.name}"
                            storage.saveBytesToFile(opfsPath, bytes)
                            resultPaths.add(opfsPath)
                        }
                    }
                    onResult(resultPaths)
                }
            }
            null
        }
        input.click()
    }
}

@Composable
actual fun TemplateFile.rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return rememberImagePicker(onResult)
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    // Directory picking in browser via <input webkitdirectory>
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.setAttribute("webkitdirectory", "true")
        
        input.onchange = {
            val files = input.files
            if (files != null && files.length > 0) {
                // Just return a dummy path or the first file's webkitRelativePath
                val path = files.item(0)?.asDynamic()?.webkitRelativePath?.toString()?.substringBefore("/")
                onResult(path)
            } else {
                onResult(null)
            }
            null
        }
        input.click()
    }
}

private suspend fun readFileAsByteArray(file: org.w3c.files.File): ByteArray = suspendCoroutine { cont ->
    val reader = FileReader()
    reader.onload = {
        val arrayBuffer = reader.result as org.khronos.webgl.ArrayBuffer
        val int8Array = Int8Array(arrayBuffer)
        val dynArray = int8Array.asDynamic()
        val bytes = ByteArray(int8Array.length) { index -> dynArray[index].unsafeCast<Byte>() }
        cont.resume(bytes)
    }
    reader.readAsArrayBuffer(file)
}
