package org.gemini.ui.forge.ui

import org.gemini.ui.forge.domain.UIBlockType
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.block_background
import geminiuiforge.composeapp.generated.resources.block_button
import geminiuiforge.composeapp.generated.resources.block_decoration
import geminiuiforge.composeapp.generated.resources.block_footer
import geminiuiforge.composeapp.generated.resources.block_header
import geminiuiforge.composeapp.generated.resources.block_icon
import geminiuiforge.composeapp.generated.resources.block_panel
import geminiuiforge.composeapp.generated.resources.block_reel
import geminiuiforge.composeapp.generated.resources.block_spin_button
import geminiuiforge.composeapp.generated.resources.block_symbol
import geminiuiforge.composeapp.generated.resources.block_text_area
import geminiuiforge.composeapp.generated.resources.block_win_display
import geminiuiforge.composeapp.generated.resources.block_group
import org.jetbrains.compose.resources.StringResource

fun UIBlockType.getDisplayNameRes(): StringResource {
    return when (this) {
        UIBlockType.REEL -> Res.string.block_reel
        UIBlockType.SPIN_BUTTON -> Res.string.block_spin_button
        UIBlockType.WIN_DISPLAY -> Res.string.block_win_display
        UIBlockType.BACKGROUND -> Res.string.block_background
        UIBlockType.SYMBOL -> Res.string.block_symbol
        UIBlockType.BUTTON -> Res.string.block_button
        UIBlockType.PANEL -> Res.string.block_panel
        UIBlockType.HEADER -> Res.string.block_header
        UIBlockType.FOOTER -> Res.string.block_footer
        UIBlockType.TEXT_AREA -> Res.string.block_text_area
        UIBlockType.ICON -> Res.string.block_icon
        UIBlockType.DECORATION -> Res.string.block_decoration
        UIBlockType.GROUP -> Res.string.block_group
    }
}