package org.gemini.ui.forge.model.gameproject

/**
 * 项目资源树节点模型。
 * 由 ResourceTreeService 扫描本地项目目录构建，供工作台左侧资源树展示。
 */
data class ResourceTreeNode(
    /** 节点显示名（文件或文件夹名） */
    val name: String,
    /** 节点绝对路径 */
    val path: String,
    /** 是否为文件夹 */
    val isDirectory: Boolean,
    /** 子节点列表（文件节点为空列表；深度达到上限时为空列表） */
    val children: List<ResourceTreeNode> = emptyList()
)
