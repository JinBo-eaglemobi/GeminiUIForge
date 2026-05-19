package org.gemini.ui.forge.state.ui

import kotlinx.serialization.Serializable
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.model.ui.UIPage

/**
 * 顶级数据模型：应用模板工程 (ProjectState)
 * 整个模板所有数据序列化落盘的最外层容器结构，通常保存为 `<模板名>.json`。
 * @property projectId 模板的项目 ID 或名称
 * @property globalTheme 整个项目的宏观主题风格（大模型分析得出）
 * @property referenceImages 参与模板分析的所有原始参考图的本地归档路径列表
 * @property pages 该项目内部包含的不同游戏/展示页面集合
 */
@Serializable
data class ProjectState(
    val projectId: String = "default_project",
    val globalTheme: String = "classic casino",
    /** 整个生成模块的风格定义 (持久化保存) */
    val globalStyle: String = "",
    /** 生成模块选中的参考图本地路径 (持久化保存) */
    val styleReferenceUri: TemplateFile? = null,
    val referenceImages: List<TemplateFile> = emptyList(), // 多参考图路径列表
    val pages: List<UIPage> = emptyList(),
    val createdAt: Long = 0L
    )

    /**
    * 模板解析后处理逻辑。
    * 针对特定的复杂组件（如 REEL）进行二次结构化调整。
    */
    fun ProjectState.postProcess(): ProjectState {
    return copy(pages = pages.map { it.postProcess() })
    }

    fun UIPage.postProcess(): UIPage {
    return copy(blocks = blocks.map { it.postProcess() })
    }

    fun org.gemini.ui.forge.model.ui.UIBlock.postProcess(): org.gemini.ui.forge.model.ui.UIBlock {
    // 递归处理子级
    val processedChildren = children.map { it.postProcess() }

    return if (type == org.gemini.ui.forge.model.ui.UIBlockType.REEL) {
        // 如果是转轴且包含子级，则将其子级直接作为 items 并入属性中，然后清空子级
        if (processedChildren.isNotEmpty()) {
            val currentProps = properties as? org.gemini.ui.forge.model.ui.BlockProperties.ReelProperties 
                ?: org.gemini.ui.forge.model.ui.BlockProperties.ReelProperties()
            
            // 直接使用 processedChildren 作为新的 items
            val updatedProps = currentProps.copy(items = currentProps.items + processedChildren)
            copy(properties = updatedProps, children = emptyList())
        } else {
            // 没有子级说明已经解析过，或者是一个空的 REEL，无需覆盖原有属性
            copy(children = processedChildren)
        }
    } else {
        copy(children = processedChildren)
    }
    }