package org.gemini.ui.forge.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager

/**
 * iOS 端递归扫描目录实现（基于 NSFileManager 广度优先逐层遍历）。
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual suspend fun listFilesRecursively(dirPath: String, maxFiles: Int): List<String> = withContext(Dispatchers.Default) {
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(dirPath)) return@withContext emptyList()

    val result = mutableListOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(dirPath)

    while (queue.isNotEmpty() && result.size < maxFiles) {
        val current = queue.removeFirst()
        val contents = fileManager.contentsOfDirectoryAtPath(current, error = null) as? List<String> ?: continue
        for (name in contents) {
            if (result.size >= maxFiles) break
            val childPath = if (current.endsWith("/")) "$current$name" else "$current/$name"
            // 通过尝试枚举判断是否为目录：能列出内容即视为目录
            val subContents = fileManager.contentsOfDirectoryAtPath(childPath, error = null) as? List<String>
            if (subContents != null) {
                queue.add(childPath)
            } else {
                result.add(childPath)
            }
        }
    }
    result
}
