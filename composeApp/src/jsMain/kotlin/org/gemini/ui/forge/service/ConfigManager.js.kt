package org.gemini.ui.forge.service

import kotlinx.browser.window
import kotlinx.coroutines.await

actual class ConfigManager {

    /**
     * JS 平台下利用 OPFS (Origin Private File System) 进行文件写入。
     * OPFS 是浏览器提供的高性能本地沙盒文件系统。
     * 
     * 由于 Kotlin/JS 标准库尚未完全封装 OPFS API，
     * 这里通过 `asDynamic()` 绕过类型检查，直接调用浏览器的原生 JS API。
     */
    private suspend fun writeToOpfs(keyName: String, keyValue: String) {
        try {
            val navigatorStorage = window.navigator.asDynamic().storage
            if (navigatorStorage != null) {
                // 获取 OPFS 根目录句柄
                val rootHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                // 创建或获取对应 key 命名的 .txt 文件
                val fileHandle = rootHandle.getFileHandle(keyName + ".txt", js("{ create: true }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
                // 创建可写流并写入数据
                val writable = fileHandle.createWritable().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                writable.write(keyValue).unsafeCast<kotlin.js.Promise<dynamic>>().await()
                writable.close().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            }
        } catch (e: Exception) {
            println("OPFS Write Error Exception: ${e.message}")
        }
    }

    /**
     * JS 平台下利用 OPFS 读取存储的文件内容。
     */
    private suspend fun readFromOpfs(keyName: String): String? {
        return try {
            val navigatorStorage = window.navigator.asDynamic().storage
            if (navigatorStorage != null) {
                // 获取根目录和对应文件句柄（不创建新文件）
                val rootHandle = navigatorStorage.getDirectory().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                val fileHandle = rootHandle.getFileHandle(keyName + ".txt", js("{ create: false }")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
                val file = fileHandle.getFile().unsafeCast<kotlin.js.Promise<dynamic>>().await()
                // 读取文本内容
                val text = file.text().unsafeCast<kotlin.js.Promise<String>>().await()
                text
            } else {
                null
            }
        } catch (e: Exception) {
            null // 如果文件不存在或无权限，返回 null
        }
    }

    actual suspend fun saveKey(keyName: String, keyValue: String) {
        writeToOpfs(keyName, keyValue)
    }

    actual suspend fun loadKey(keyName: String): String? {
        return readFromOpfs(keyName)
    }

    actual suspend fun loadGlobalGeminiKey(): String? {
        return null
    }
}
