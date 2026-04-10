package org.gemini.ui.forge.model.app
import org.gemini.ui.forge.model.ui.ProjectState

data class UIModule(
    val id: String,
    val nameRes: org.jetbrains.compose.resources.StringResource? = null,
    val nameStr: String? = null,
    val projectState: ProjectState? = null,
    val absolutePath: String? = null
)
