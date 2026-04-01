package org.gemini.ui.forge.utils

/**
 * 跨平台读取本地文件为字节数组
 */
expect fun readLocalFileBytes(filePath: String): ByteArray?
