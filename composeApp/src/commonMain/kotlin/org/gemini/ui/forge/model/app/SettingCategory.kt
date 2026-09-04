package org.gemini.ui.forge.model.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import geminiuiforge.composeapp.generated.resources.*

/**
 * 应用程序设置分类枚举。
 */
enum class SettingCategory(val labelRes: org.jetbrains.compose.resources.StringResource, val icon: ImageVector) {
    GENERAL(Res.string.settings_category_general, Icons.Default.Settings),
    AI(Res.string.settings_category_ai, Icons.Default.AutoAwesome),
    PROMPTS(Res.string.settings_category_prompts, Icons.Default.Description),
    ENVIRONMENT(Res.string.settings_category_environment, Icons.Default.Dns),
    SHORTCUTS(Res.string.settings_category_shortcuts, Icons.Default.Keyboard),
    ABOUT(Res.string.settings_category_about, Icons.Default.Info)
}
