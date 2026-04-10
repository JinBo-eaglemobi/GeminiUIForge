package org.gemini.ui.forge.model.app
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.settings_category_ai
import geminiuiforge.composeapp.generated.resources.settings_category_general
import geminiuiforge.composeapp.generated.resources.settings_category_shortcuts

enum class SettingCategory(val labelRes: org.jetbrains.compose.resources.StringResource, val icon: ImageVector) {
    GENERAL(Res.string.settings_category_general, Icons.Default.Settings),
    AI(Res.string.settings_category_ai, Icons.Default.AutoAwesome),
    SHORTCUTS(Res.string.settings_category_shortcuts, Icons.Default.Keyboard)
}
