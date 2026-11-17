package org.gemini.ui.forge.ui.component

import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import org.gemini.ui.forge.model.ui.UIBlockType

fun UIBlockType.getDisplayNameRes(): StringResource {
    return when (this) {
        UIBlockType.BUTTON -> Res.string.block_button
        UIBlockType.COMPONENT -> Res.string.block_component
        UIBlockType.TEXT -> Res.string.block_text
        UIBlockType.IMAGE -> Res.string.block_image
        UIBlockType.LIST -> Res.string.block_list
        UIBlockType.SPINE -> Res.string.block_spine
        UIBlockType.COMBO_BOX -> Res.string.block_combo_box
        UIBlockType.PROGRESS_BAR -> Res.string.block_progress_bar
        UIBlockType.POPUP_MENU -> Res.string.block_popup_menu
        UIBlockType.LOADER -> Res.string.block_loader
        UIBlockType.SCROLL_BAR -> Res.string.block_scroll_bar
        UIBlockType.SLIDER -> Res.string.block_slider
        UIBlockType.INPUT -> Res.string.block_input
    }
}

fun UIBlockType.getIcon(): ImageVector {
    return when (this) {
        UIBlockType.BUTTON -> Icons.Default.TouchApp
        UIBlockType.COMPONENT -> Icons.Default.WebAsset
        UIBlockType.TEXT -> Icons.Default.Edit
        UIBlockType.IMAGE -> Icons.Default.Image
        UIBlockType.LIST -> Icons.Default.ViewColumn
        UIBlockType.SPINE -> Icons.Default.AutoAwesomeMotion
        UIBlockType.COMBO_BOX -> Icons.AutoMirrored.Filled.List
        UIBlockType.PROGRESS_BAR -> Icons.Default.LinearScale
        UIBlockType.POPUP_MENU -> Icons.AutoMirrored.Filled.MenuOpen
        UIBlockType.LOADER -> Icons.Default.CloudDownload
        UIBlockType.SCROLL_BAR -> Icons.Default.FormatLineSpacing
        UIBlockType.SLIDER -> Icons.Default.Tune
        UIBlockType.INPUT -> Icons.AutoMirrored.Filled.Input
    }
}
