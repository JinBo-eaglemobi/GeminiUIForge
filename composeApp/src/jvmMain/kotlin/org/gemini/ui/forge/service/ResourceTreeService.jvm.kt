package org.gemini.ui.forge.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gemini.ui.forge.model.gameproject.ResourceTreeNode
import java.io.File

/**
 * 资源树服务的 JVM（桌面端）实现。
 * 递归扫描项目目录构建资源树（带深度上限），并发现 build 目录下的 HTML 入口文件。
 */
class JvmResourceTreeService : ResourceTreeService {

    override suspend fun buildTree(rootDir: String): ResourceTreeNode? = withContext(Dispatchers.IO) {
        val root = File(rootDir)
        if (!root.isDirectory) {
            null
        } else {
            scanNode(root, depth = 0)
        }
    }

    override suspend fun listHtmlFiles(projectPath: String): List<String> = withContext(Dispatchers.IO) {
        val buildDir = File(projectPath, "build")
        if (!buildDir.isDirectory) {
            emptyList()
        } else {
            buildDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.absolutePath }
                ?: emptyList()
        }
    }

    /**
     * 递归扫描单个目录节点。
     * 达到深度上限后不再展开子节点，避免超大目录树拖慢界面。
     */
    private fun scanNode(dir: File, depth: Int): ResourceTreeNode {
        val children: List<ResourceTreeNode> = if (depth >= MAX_DEPTH) {
            emptyList()
        } else {
            dir.listFiles()
                ?.filter { it.name !in IGNORED_NAMES }
                ?.map { file ->
                    if (file.isDirectory) {
                        scanNode(file, depth + 1)
                    } else {
                        ResourceTreeNode(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = false
                        )
                    }
                }
                ?.sortedWith(
                    compareByDescending<ResourceTreeNode> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                ?: emptyList()
        }
        return ResourceTreeNode(
            name = dir.name,
            path = dir.absolutePath,
            isDirectory = true,
            children = children
        )
    }

    private companion object {
        /** 资源树最大扫描深度 */
        const val MAX_DEPTH = 6

        /** 忽略的目录/文件名（版本库元数据与依赖产物） */
        val IGNORED_NAMES = setOf(".git", "node_modules", ".idea", ".gradle")
    }
}

/**
 * 桌面端资源树服务工厂实现。
 */
actual fun createResourceTreeService(): ResourceTreeService = JvmResourceTreeService()
