package org.gemini.ui.forge.utils

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array

actual class LocalFileStorage {

    /**
     * OPFS 的基础路径处理。
     * 因为 OPFS 不支持直接带斜杠的多级目录（比如 "dir/file.txt" ），
     * 需要我们递归创建并获取正确的 fileHandle 或 directoryHandle。
     */
    private suspend fun getTargetDirectoryHandle(path: String, createIfNotExists: Boolean = true): dynamic {
        try {
            val navigatorStorage = window.navigator.asDynamic().storage ?: return null
            var currentHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            
            val parts = path.split("/").filter { it.isNotEmpty() }
            for (part in parts) {
                val options = if (createIfNotExists) js("{ create: true }") else js("{ create: false }")
                currentHandle = currentHandle.getDirectoryHandle(part, options).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            }
            return currentHandle
        } catch (e: Throwable) {
            // 当 createIfNotExists 为 false 且目录不存在时，抛出 NotFoundError 是正常的预期行为。
            return null
        }
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
        val files = mutableListOf<String>()
        try {
            val navigatorStorage = window.navigator.asDynamic().storage ?: return emptyList()
            val rootHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            val iterator = rootHandle.values().unsafeCast<dynamic>()
            
            while (true) {
                val nextResult = iterator.next().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                if (nextResult.done.unsafeCast<Boolean?>() ?: true) break
                val value = nextResult.value
                if (value != null && value.kind == "file") {
                    files.add(value.name.unsafeCast<String>())
                }
            }
        } catch (e: Exception) {}
        return files
    }

    suspend fun listFilesRecursive(dirPath: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val dirHandle = getTargetDirectoryHandle(dirPath, createIfNotExists = false) ?: return emptyList()
            val iterator = dirHandle.values().unsafeCast<dynamic>()
            while (true) {
                val nextResult = iterator.next().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                if (nextResult.done.unsafeCast<Boolean?>() ?: true) break
                val value = nextResult.value
                if (value != null) {
                    val name = value.name.unsafeCast<String>()
                    val fullPath = if (dirPath.isEmpty()) name else "$dirPath/$name"
                    if (value.kind == "file") {
                        files.add(fullPath)
                    } else if (value.kind == "directory") {
                        files.addAll(listFilesRecursive(fullPath))
                    }
                }
            }
        } catch (e: Exception) {}
        return files
    }

    actual suspend fun listDirectories(parentDir: String?): List<String> {
        val dirs = mutableListOf<String>()
        try {
            val dirHandle = if (parentDir == null) {
                val navigatorStorage = window.navigator.asDynamic().storage ?: return emptyList()
                navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            } else {
                getTargetDirectoryHandle(parentDir, createIfNotExists = false) ?: return emptyList()
            }
            
            val iterator = dirHandle.values().unsafeCast<dynamic>()
            while (true) {
                val nextResult = iterator.next().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                if (nextResult.done.unsafeCast<Boolean?>() ?: true) break
                val value = nextResult.value
                if (value != null && value.kind == "directory") {
                    dirs.add(value.name.unsafeCast<String>())
                }
            }
        } catch (e: Exception) {}
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

    actual suspend fun exists(fileName: String): Boolean {
        return try {
            val (dirPath, name) = getParentPathAndName(fileName)
            val dirHandle = getTargetDirectoryHandle(dirPath) ?: return false
            dirHandle.getFileHandle(name, js("{ create: false }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun getFilePath(fileName: String): String {
        return fileName
    }
}