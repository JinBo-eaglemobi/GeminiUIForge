package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.ResourceTreeNode

/**
 * 项目资源树服务接口。
 * 负责扫描本地游戏项目目录，构建资源树并发现 build 目录下的 HTML 入口文件。
 */
interface ResourceTreeService {

    /**
     * 扫描项目根目录构建资源树。
     * @param rootDir 项目根目录绝对路径
     * @return 根节点；目录不存在时返回 null
     */
    suspend fun buildTree(rootDir: String): ResourceTreeNode?

    /**
     * 扫描项目 build 目录下的 HTML 入口文件（仅根层，不含子目录）。
     * @param projectPath 项目根目录绝对路径
     * @return HTML 文件绝对路径列表（按文件名排序）
     */
    suspend fun listHtmlFiles(projectPath: String): List<String>
}

/**
 * 创建当前平台的资源树服务实现。
 */
expect fun createResourceTreeService(): ResourceTreeService
