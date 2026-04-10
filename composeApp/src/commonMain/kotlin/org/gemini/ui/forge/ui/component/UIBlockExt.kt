package org.gemini.ui.forge.ui.component
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.block_background
import geminiuiforge.composeapp.generated.resources.block_button
import geminiuiforge.composeapp.generated.resources.block_footer
import geminiuiforge.composeapp.generated.resources.block_header
import geminiuiforge.composeapp.generated.resources.block_icon
import geminiuiforge.composeapp.generated.resources.block_view
import geminiuiforge.composeapp.generated.resources.block_reel
import geminiuiforge.composeapp.generated.resources.block_spin_button
import geminiuiforge.composeapp.generated.resources.block_symbol
import geminiuiforge.composeapp.generated.resources.block_text_area
import geminiuiforge.composeapp.generated.resources.block_win_display
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
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
        UIBlockType.TEXT_AREA -> Res.string.block_text_area
        UIBlockType.ICON -> Res.string.block_icon
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
        UIBlockType.TEXT_AREA -> Icons.Default.Edit
        UIBlockType.ICON -> Icons.Default.Face
    }
}