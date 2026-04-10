package org.gemini.ui.forge.ui

import org.gemini.ui.forge.domain.ProjectState

data class UIModule(
    val id: String,
    val nameRes: org.jetbrains.compose.resources.StringResource? = null,
    val nameStr: String? = null,
    val projectState: ProjectState? = null,
    val absolutePath: String? = null
)
