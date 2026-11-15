package org.gemini.ui.forge.ui.uistudio.model

import org.gemini.ui.forge.state.ui.ProjectState

/**
 * AI 对话单轮次事务检查点快照（零拷贝不可变引用）。
 *
 * 用于在对话中途报错点击「重新生成」或点击「撤回」时，在 10ms 内将工程所有视图页面、
 * 磁盘配置文件 (template.json 与 pages 目录下的独立页面 JSON)、素材库账本 (asset_library.json)
 * 以及物理图片的增/删/改/重命名/移动操作 100% 原子还原至提问前的干净状态。
 */
data class TurnCheckpoint(
    val userMessageId: String,
    val projectId: String,
    val projectSnapshot: ProjectState,
    val assetLibrarySnapshot: UIAssetLibrary,
    val currentViewId: String?,
    val openViewIds: List<String>
)
