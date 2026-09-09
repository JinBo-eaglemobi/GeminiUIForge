package org.gemini.ui.forge.service

import org.gemini.ui.forge.model.gameproject.ResourceTreeNode

/**
 * 资源树服务的 JS (Web) 占位实现（待适配：遵循桌面版优先规范）。
 */
class JsResourceTreeService : ResourceTreeService {

    override suspend fun buildTree(rootDir: String): ResourceTreeNode? = null

    override suspend fun listHtmlFiles(projectPath: String): List<String> = emptyList()
}

/**
 * JS 平台资源树服务工厂实现。
 */
actual fun createResourceTreeService(): ResourceTreeService = JsResourceTreeService()
