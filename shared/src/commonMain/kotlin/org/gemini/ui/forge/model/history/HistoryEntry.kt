package org.gemini.ui.forge.model.history

import org.gemini.ui.forge.state.ui.ProjectState

/**
 * 历史记录条目，记录单次快照及描述
 */
data class HistoryEntry(
    val id: String,
    val label: String,
    val timestamp: Long,
    val projectState: ProjectState
)
