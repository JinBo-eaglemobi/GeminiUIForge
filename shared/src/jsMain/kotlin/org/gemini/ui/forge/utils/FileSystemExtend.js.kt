package org.gemini.ui.forge.utils

/**
 * Web (JS) 端递归扫描目录实现。
 * 浏览器沙箱无外部磁盘目录概念（OPFS 不支持任意绝对路径遍历），返回空列表。
 * 待适配：后续可对接 File System Access API 的 showDirectoryPicker 实现浏览器内目录扫描。
 */
actual suspend fun listFilesRecursively(dirPath: String, maxFiles: Int): List<String> {
    return emptyList()
}
