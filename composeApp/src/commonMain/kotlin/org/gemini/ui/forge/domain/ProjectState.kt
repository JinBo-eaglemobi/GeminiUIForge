package org.gemini.ui.forge.domain

import kotlinx.serialization.Serializable

/**
 * 顶级数据模型：应用模板工程 (ProjectState)
 * 整个模板所有数据序列化落盘的最外层容器结构，通常保存为 `<模板名>.json`。
 * @property projectId 模板的项目 ID 或名称
 * @property globalTheme 整个项目的宏观主题风格（大模型分析得出）
 * @property coverImage 作为主页封面展示的示例海报图的相对文件路径
 * @property referenceImages 参与模板分析的所有原始参考图的本地归档路径列表
 * @property pages 该项目内部包含的不同游戏/展示页面集合
 */
@Serializable
data class ProjectState(
    val projectId: String = "default_project",
    val globalTheme: String = "classic casino",
    val coverImage: String? = null,
    val referenceImages: List<String> = emptyList(), // 新增：多参考图路径列表
    val pages: List<UIPage> = emptyList(),
    val createdAt: Long = 0L
)
