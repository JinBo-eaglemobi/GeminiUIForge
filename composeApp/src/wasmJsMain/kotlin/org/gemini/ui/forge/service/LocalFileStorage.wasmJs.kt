package org.gemini.ui.forge.service

import kotlinx.browser.localStorage

actual class LocalFileStorage {
    actual fun saveToFile(fileName: String, content: String): String {
        localStorage.setItem(fileName, content)
        return fileName
    }

    actual fun saveBytesToFile(fileName: String, bytes: ByteArray): String {
        val b64 = kotlin.io.encoding.Base64.Default.encode(bytes)
        localStorage.setItem(fileName, "bytes:$b64")
        return fileName
    }

    actual fun readFromFile(fileName: String): String? {
        return localStorage.getItem(fileName)
    }

    actual fun readBytesFromFile(fileName: String): ByteArray? {
        val str = localStorage.getItem(fileName)
        if (str != null && str.startsWith("bytes:")) {
            return kotlin.io.encoding.Base64.Default.decode(str.substringAfter("bytes:"))
        }
        return null
    }

    actual fun listFiles(): List<String> {
        val keys = mutableListOf<String>()
        for (i in 0 until localStorage.length) {
            localStorage.key(i)?.let { keys.add(it) }
        }
        return keys.filter { it.endsWith(".json") }
    }

    actual fun listDirectories(): List<String> {
        val dirs = mutableSetOf<String>()
        for (i in 0 until localStorage.length) {
            val key = localStorage.key(i) ?: continue
            if (key.contains("/")) {
                dirs.add(key.substringBefore("/"))
            }
        }
        return dirs.toList()
    }

    actual fun getFilePath(fileName: String): String {
        return fileName
    }

    actual fun deleteFile(fileName: String): Boolean {
        if (localStorage.getItem(fileName) != null) {
            localStorage.removeItem(fileName)
            return true
        }
        return false
    }

    actual fun deleteDirectory(dirName: String): Boolean {
        val keysToRemove = mutableListOf<String>()
        for (i in 0 until localStorage.length) {
            val key = localStorage.key(i) ?: continue
            if (key.startsWith("$dirName/")) keysToRemove.add(key)
        }
        keysToRemove.forEach { localStorage.removeItem(it) }
        return true
    }
}
