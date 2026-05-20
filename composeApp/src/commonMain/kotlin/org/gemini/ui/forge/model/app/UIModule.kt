package org.gemini.ui.forge.model.app
import org.gemini.ui.forge.state.ui.ProjectState
import org.jetbrains.compose.resources.StringResource

/**
 * 表示应用中一个 UI 模块（或项目模板）的数据实体。
 * 
 * 该类作为首页和项目管理层的核心数据结构，用于封装从本地或内置资源中读取的项目基本信息，
 * 并包含进入编辑工作区前所需的元数据和底层序列化状态。
 *
 * @property id 模块的唯一标识符，通常对应文件夹名称或模板的标识符。
 * @property nameRes 模块的显示名称（基于内置 Compose Resources 的字符串引用），优先使用。
 * @property nameStr 模块的显示名称（普通字符串格式），常用于表示从本地用户目录加载的自定义项目。
 * @property projectState 该模块关联的核心布局状态数据结构。包含具体的页面、UIBlock 层级、画布尺寸等核心属性。
 */
data class UIModule(
    val id: String,
    val nameRes: StringResource? = null,
    val nameStr: String? = null,
    val projectState: ProjectState? = null,
)
