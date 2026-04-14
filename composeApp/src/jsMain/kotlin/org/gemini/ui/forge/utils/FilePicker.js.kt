package org.gemini.ui.forge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    return remember {
        {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/*"
            input.multiple = true
            input.onchange = {
                val files = input.files
                if (files != null) {
                    val result = mutableListOf<String>()
                    var processedCount = 0
                    val totalFiles = files.length
                    
                    if (totalFiles == 0) {
                        onResult(emptyList())
                    } else {
                        for (i in 0 until totalFiles) {
                            val file = files.item(i)
                            if (file != null) {
                                val reader = org.w3c.files.FileReader()
                                reader.onload = { event ->
                                    // 使用 asDynamic() 绕过 Kotlin 的类型检查，直接访问 JS 的 result 属性并调用 toString()
                                    val target = event.target.asDynamic()
                                    val dataUrl = target.result?.toString() ?: ""
                                    
                                    console.log("File read success. Data URL length: " + dataUrl.length)
                                    // 打印前几十个字符帮助调试
                                    console.log("Data URL prefix: " + dataUrl.take(30))
                                    
                                    result.add(dataUrl)
                                    processedCount++
                                    if (processedCount == totalFiles) {
                                        onResult(result)
                                    }
                                }
                                reader.onerror = {
                                    console.log("FileReader error occurred")
                                    processedCount++
                                    if (processedCount == totalFiles) {
                                        onResult(result)
                                    }
                                }
                                reader.readAsDataURL(file)
                            } else {
                                processedCount++
                                if (processedCount == totalFiles) {
                                    onResult(result)
                                }
                            }
                        }
                    }
                }
            }
            input.click()
        }
    }
}

@Composable
actual fun rememberDirectoryPicker(title: String, onResult: (String?) -> Unit): () -> Unit {
    return remember {
        {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.asDynamic().webkitdirectory = true
            input.onchange = {
                val files = input.files
                if (files != null && files.length > 0) {
                    // 对于 Web，我们拿不到真实的绝对路径，
                    // 通常返回第一个文件的路径前缀或特定的标识符，
                    // 这里我们为了兼容 LocalFileStorage，返回一个虚拟前缀。
                    val file = files.item(0)
                    if (file != null) {
                        val fullPath = file.asDynamic().webkitRelativePath as String
                        val dirName = fullPath.split("/").firstOrNull() ?: ""
                        onResult(dirName)
                    } else {
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }
            input.click()
        }
    }
}
