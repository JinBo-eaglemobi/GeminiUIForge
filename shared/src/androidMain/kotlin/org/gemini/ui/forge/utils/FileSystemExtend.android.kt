package org.gemini.ui.forge.utils

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 端递归扫描目录实现（与 JVM 共用 java.io.File 体系，广度优先队列遍历）。
 */
actual suspend fun listFilesRecursively(dirPath: String, maxFiles: Int): List<String> = withContext(Dispatchers.IO) {
    val root = File(dirPath)
    if (!root.exists() || !root.isDirectory) return@withContext emptyList()

    val result = mutableListOf<String>()
    val queue = ArrayDeque<File>()
    queue.add(root)

    while (queue.isNotEmpty() && result.size < maxFiles) {
        val current = queue.removeFirst()
        val children = try {
            current.listFiles() ?: continue
        } catch (e: Exception) {
            continue // 无权限或 IO 异常的子目录直接跳过，不中断整体扫描
        }
        for (child in children) {
            if (result.size >= maxFiles) break
            if (child.isDirectory) {
                queue.add(child)
            } else if (child.isFile) {
                result.add(child.absolutePath)
            }
        }
    }
    result
}
