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
        UIBlockType.REEL -> Res.string.block_reel
        UIBlockType.SPIN_BUTTON -> Res.string.block_spin_button
        UIBlockType.WIN_DISPLAY -> Res.string.block_win_display
        UIBlockType.BACKGROUND -> Res.string.block_background
        UIBlockType.SYMBOL -> Res.string.block_symbol
        UIBlockType.BUTTON -> Res.string.block_button
        UIBlockType.VIEW -> Res.string.block_view
        UIBlockType.HEADER -> Res.string.block_header
        UIBlockType.FOOTER -> Res.string.block_footer
        UIBlockType.TEXT -> Res.string.block_text
        UIBlockType.IMAGE -> Res.string.block_image
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
        UIBlockType.REEL -> Icons.Default.ViewColumn
        UIBlockType.SPIN_BUTTON -> Icons.Default.PlayArrow
        UIBlockType.WIN_DISPLAY -> Icons.Default.Star
        UIBlockType.BACKGROUND -> Icons.Default.Image
        UIBlockType.SYMBOL -> Icons.Default.AddCircle
        UIBlockType.BUTTON -> Icons.Default.TouchApp
        UIBlockType.VIEW -> Icons.Default.WebAsset
        UIBlockType.HEADER -> Icons.Default.KeyboardArrowUp
        UIBlockType.FOOTER -> Icons.Default.KeyboardArrowDown
        UIBlockType.TEXT -> Icons.Default.Edit
        UIBlockType.IMAGE -> Icons.Default.Image
        UIBlockType.COMBO_BOX -> Icons.AutoMirrored.Filled.List
        UIBlockType.PROGRESS_BAR -> Icons.Default.LinearScale
        UIBlockType.POPUP_MENU -> Icons.AutoMirrored.Filled.MenuOpen
        UIBlockType.LOADER -> Icons.Default.CloudDownload
        UIBlockType.SCROLL_BAR -> Icons.Default.FormatLineSpacing
        UIBlockType.SLIDER -> Icons.Default.Tune
        UIBlockType.INPUT -> Icons.AutoMirrored.Filled.Input
    }
}
