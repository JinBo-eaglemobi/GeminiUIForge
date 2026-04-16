package org.gemini.ui.forge.state

import org.gemini.ui.forge.model.app.AppGlobalState
import org.gemini.ui.forge.model.app.PromptLanguage
import org.gemini.ui.forge.model.app.ReferenceDisplayMode
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.ui.UIBlock
import org.gemini.ui.forge.model.ui.UIPage

/**
 * 编辑器界面的全局 UI 状态模型
 * 包含项目数据、选中状态、AI 生成配置及界面显示控制等
 */
data class EditorState(
    /** 全局应用配置状态（如 API Key、主题设置等） */
    val globalState: AppGlobalState = AppGlobalState(),
    
    /** 当前编辑的项目完整数据状态 */
    val project: ProjectState = ProjectState(),
    
    /** 当前项目名称 */
    val projectName: String = "Untitled",
    
    /** 当前选中的页面 ID */
    val selectedPageId: String? = null,
    
    /** 当前选中的 UI 模块（UIBlock）ID */
    val selectedBlockId: String? = null,
    
    /** 当前正在编辑/显示的提示词语言偏好 */
    val currentEditingPromptLang: PromptLanguage = PromptLanguage.ZH,
    
    /** AI 是否正在执行生成任务（如生图、优化提示词等） */
    val isGenerating: Boolean = false,
    
    /** 是否显示 AI 任务进度弹窗 */
    val showAITaskDialog: Boolean = false,
    
    /** AI 生成过程中的实时日志流列表 */
    val generationLogs: List<String> = emptyList(),
    
    /** AI 任务进度弹窗中的日志面板是否可见 */
    val isGenerationLogVisible: Boolean = true,
    
    /** 生成图片时是否自动执行背景去除（透明背景） */
    val isGenerateTransparent: Boolean = false,
    
    /** 是否开启“纯净视觉模式”（隐藏所有辅助线、坐标信息和占位符） */
    val isVisualMode: Boolean = false,
    
    /** 背景去除任务是否优先使用云端服务（若关闭则使用本地 rembg 脚本） */
    val isPrioritizeCloudRemoval: Boolean = false,
    
    /** AI 生成的候选图片资源列表 */
    val generatedCandidates: List<String> = emptyList(),
    
    /** 视觉对照参考图的显示模式（隐藏、分屏、叠加） */
    val referenceMode: ReferenceDisplayMode = ReferenceDisplayMode.HIDDEN,
    
    /** 参考图叠加模式下的透明度 (0.0 - 1.0) */
    val referenceOpacity: Float = 0.4f,

    /** 当前处于孤立编辑模式下的组（Group）ID。为 null 时表示处于全局编辑模式 */
    val editingGroupId: String? = null,

    /** 针对现有模块进行 AI 重塑/润色时的默认指令模板 */
    val defaultRefineInstructionUpdate: String = "",
    
    /** 针对新选取区域进行 AI 生成时的默认指令模板 */
    val defaultRefineInstructionNew: String = ""
) {
    /** 获取当前选中的页面对象 */
    val currentPage: UIPage?
        get() = project.pages.find { it.id == selectedPageId }
        
    /** 获取当前选中的 UI 模块对象（支持在层级树中进行深度搜索） */
    val selectedBlock: UIBlock?
        get() = currentPage?.blocks?.let { findBlockById(it, selectedBlockId) }

    /** 内部递归搜索函数：根据 ID 在嵌套的 Block 列表中查找目标对象 */
    private fun findBlockById(blocks: List<UIBlock>, id: String?): UIBlock? {
        if (id == null) return null
        for (block in blocks) {
            if (block.id == id) return block
            val found = findBlockById(block.children, id)
            if (found != null) return found
        }
        return null
    }

    /** 根据当前选中的语言偏好，获取当前模块应显示的提示词内容 */
    val currentPromptText: String
        get() = selectedBlock?.let { block ->
            when (currentEditingPromptLang) {
                PromptLanguage.EN -> block.userPromptEn
                PromptLanguage.ZH -> block.userPromptZh
                else -> block.userPromptZh
            }
        } ?: ""
}
