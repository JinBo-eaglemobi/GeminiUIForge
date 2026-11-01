package org.gemini.ui.forge.ui.uistudio.model

import kotlinx.serialization.Serializable

/**
 * 外部素材导入重名冲突挂起状态：等待用户在 AppImportConflictDialog 中选择统一处理策略。
 */
@Serializable
data class ImportConflictState(
    val conflicts: List<ImportConflictFile>,
    val targetFolderId: String?,
    val projectSanitizedName: String
)
