package org.gemini.ui.forge.service

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array

actual class LocalFileStorage {

    /**
     * OPFS 的基础路径处理。
     * 因为 OPFS 不支持直接带斜杠的多级目录（比如 "dir/file.txt" ），
     * 需要我们递归创建并获取正确的 fileHandle 或 directoryHandle。
     */
    private suspend fun getTargetDirectoryHandle(path: String): dynamic {
        val navigatorStorage = window.navigator.asDynamic().storage ?: return null
        var currentHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
        
        val parts = path.split("/").filter { it.isNotEmpty() }
        for (part in parts) {
            currentHandle = currentHandle.getDirectoryHandle(part, js("{ create: true }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
        }
        return currentHandle
    }

    private fun getParentPathAndName(fullPath: String): Pair<String, String> {
        val parts = fullPath.split("/")
        val fileName = parts.last()
        val dirPath = parts.dropLast(1).joinToString("/")
        return dirPath to fileName
    }

    actual suspend fun updateDataDir(newPath: String): Boolean {
        // OPFS 运行在浏览器沙盒中，不支持修改根路径，这里直接返回 false 即可
        return false
    }

    actual suspend fun getDataDir(): String = "opfs://templates"

    actual suspend fun saveToFile(fileName: String, content: String): String {
        try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return ""
            
            val fileHandle = dirHandle.getFileHandle(name, js("{ create: true }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val writable = fileHandle.createWritable().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            writable.write(content).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            writable.close().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            return fileName
        } catch (e: Exception) {
            return ""
        }
    }

    actual suspend fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return ""
            
            val fileHandle = dirHandle.getFileHandle(name, js("{ create: true }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val writable = fileHandle.createWritable().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            
            // 将 Kotlin ByteArray 转换为 JS Int8Array
            val int8Array = org.khronos.webgl.Int8Array(bytes.toTypedArray())
            writable.write(int8Array).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            writable.close().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            
            return fileName
        } catch (e: Exception) {
            return ""
        }
    }

    actual suspend fun readFromFile(fileName: String): String? {
        try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return null
            
            val fileHandle = dirHandle.getFileHandle(name, js("{ create: false }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val file = fileHandle.getFile().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val text = file.text().unsafeCast<kotlin.js.Promise<String>>().await()
            return text
        } catch (e: Exception) {
            return null
        }
    }

    actual suspend fun readBytesFromFile(fileName: String): ByteArray? {
        try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return null
            
            val fileHandle = dirHandle.getFileHandle(name, js("{ create: false }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val file = fileHandle.getFile().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            
            // ArrayBuffer to ByteArray
            val arrayBuffer = file.arrayBuffer().unsafeCast<kotlin.js.Promise<org.khronos.webgl.ArrayBuffer>>().await()
            val int8Array = org.khronos.webgl.Int8Array(arrayBuffer)
            val bytes = ByteArray(int8Array.length)
            val dynArray = int8Array.asDynamic()
            for (i in 0 until int8Array.length) {
                bytes[i] = dynArray[i].unsafeCast<Byte>()
            }
            return bytes
        } catch (e: Exception) {
            return null
        }
    }

    actual suspend fun listFiles(): List<String> {
        // 由于我们的模板通常存储在类似 "Name/template.json" 结构中，
        // 这里只是为了满足接口，简单获取根目录的 .json。通常在 OPFS 中我们较少使用。
        return emptyList()
    }

    actual suspend fun listDirectories(): List<String> {
        val dirs = mutableListOf<String>()
        try {
            val navigatorStorage = window.navigator.asDynamic().storage ?: return emptyList()
            val rootHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            
            val iterator = rootHandle.values().unsafeCast<dynamic>()
            
            while (true) {
                val nextResult = iterator.next().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                val done = nextResult.done.unsafeCast<Boolean?>() ?: true
                if (done) break
                
                val value = nextResult.value
                if (value != null && value.kind == "directory") {
                    dirs.add(value.name.unsafeCast<String>())
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return dirs
    }

    actual suspend fun deleteFile(fileName: String): Boolean {
        try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return false
            dirHandle.removeEntry(name).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    actual suspend fun deleteDirectory(dirName: String): Boolean {
        try {
            val navigatorStorage = window.navigator.asDynamic().storage ?: return false
            val rootHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            rootHandle.removeEntry(dirName, js("{ recursive: true }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    actual suspend fun getFilePath(fileName: String): String {
        return fileName
    }
}