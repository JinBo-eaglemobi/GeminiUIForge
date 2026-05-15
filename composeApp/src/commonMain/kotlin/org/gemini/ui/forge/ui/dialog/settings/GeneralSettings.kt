package org.gemini.ui.forge.ui.dialog.settings


import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.getPlatform
import org.gemini.ui.forge.model.app.LayoutMode
import org.gemini.ui.forge.model.app.ThemeMode
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberDirectoryPicker
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.skiko.KotlinBackend
import org.jetbrains.skiko.hostOs
import org.jetbrains.skiko.kotlinBackend


/**
 * 通用设置区块
 *
 * 提供应用主题、布局模式、界面语言以及本地存储目录的配置选项。
 *
 * @param currentTheme 当前选择的主题模式
 * @param currentLayoutMode 当前选择的布局模式
 * @param currentLanguage 当前选择的界面语言
 * @param currentStorageDir 当前配置的存储目录
 * @param onThemeSelected 选择主题模式回调
 * @param onLayoutModeSelected 选择布局模式回调
 * @param onLanguageSelected 选择界面语言回调
 * @param onStorageDirSaved 保存存储目录回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettings(
    currentTheme: ThemeMode,
    currentLayoutMode: LayoutMode,
    currentLanguage: String,
    currentStorageDir: String,
    currentJvmXmx: String = "2G",
    onThemeSelected: (ThemeMode) -> Unit,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit,
    onJvmXmxSaved: (String) -> Unit = {}
) {
    val isCompact = LocalMinimumInteractiveComponentSize.current == 0.dp
    val coroutineScope = rememberCoroutineScope()

    SettingSectionTitle(stringResource(Res.string.settings_category_general))
    
    // --- JVM Memory (Native specific) ---
    val isDesktop = hostOs.isWindows || hostOs.isMacOS || hostOs.isLinux
    if (kotlinBackend == KotlinBackend.JVM && isDesktop) {
        var showRestartDialog by remember { mutableStateOf(false) }
        var memoryExpanded by remember { mutableStateOf(false) }
        val memoryOptions = listOf("1G", "2G", "4G", "8G", "12G", "16G")

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text(stringResource(Res.string.dialog_restart_confirm_title)) },
                text = { Text(stringResource(Res.string.dialog_restart_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = { 
                        coroutineScope.launch {
                            getPlatform().applyUpdateAndRestart("")
                        }
                    }) {
                        Text(stringResource(Res.string.action_restart_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) {
                        Text(stringResource(Res.string.action_restart_later))
                    }
                }
            )
        }

        ExposedDropdownMenuBox(expanded = memoryExpanded, onExpandedChange = { memoryExpanded = !memoryExpanded }) {
            SelectAllOutlinedTextField(
                value = currentJvmXmx,
                onValueChange = {}, readOnly = true,
                label = { Text(stringResource(Res.string.settings_jvm_xmx)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(memoryExpanded) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = AppShapes.medium
            )
            ExposedDropdownMenu(expanded = memoryExpanded, onDismissRequest = { memoryExpanded = false }) {
                memoryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            if (currentJvmXmx != option) {
                                onJvmXmxSaved(option)
                                showRestartDialog = true
                            }
                            memoryExpanded = false
                        },
                        contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding,
                        modifier = if (isCompact) Modifier.height(32.dp) else Modifier
                    )
                }
            }
        }
    }

    // Theme Dropdown
    var themeExpanded by remember { mutableStateOf(false) }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
        ThemeMode.LIGHT to stringResource(Res.string.theme_light),
        ThemeMode.DARK to stringResource(Res.string.theme_dark)
    )
    val currentThemeLabel = themeOptions.find { it.first == currentTheme }?.second ?: ""

    ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = !themeExpanded }) {
        SelectAllOutlinedTextField(
            value = currentThemeLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_appearance_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
            themeOptions.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onThemeSelected(mode); themeExpanded = false }
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }

    // LayoutMode Dropdown
    var layoutExpanded by remember { mutableStateOf(false) }
    val layoutOptions = listOf(
        LayoutMode.AUTO to stringResource(Res.string.layout_mode_auto),
        LayoutMode.TOUCH to stringResource(Res.string.layout_mode_touch),
        LayoutMode.COMPACT to stringResource(Res.string.layout_mode_compact)
    )
    val currentLayoutLabel = layoutOptions.find { it.first == currentLayoutMode }?.second ?: ""

    ExposedDropdownMenuBox(expanded = layoutExpanded, onExpandedChange = { layoutExpanded = !layoutExpanded }) {
        SelectAllOutlinedTextField(
            value = currentLayoutLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_layout_mode)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(layoutExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = layoutExpanded, onDismissRequest = { layoutExpanded = false }) {
            layoutOptions.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onLayoutModeSelected(mode); layoutExpanded = false }
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }

    // Language Dropdown
    var langExpanded by remember { mutableStateOf(false) }
    val langOptions = listOf(
        "auto" to stringResource(Res.string.settings_language_auto),
        "zh" to stringResource(Res.string.language_chinese),
        "en" to stringResource(Res.string.language_english)
    )
    val displayLangLabel = langOptions.find { it.first == currentLanguage }?.second ?: currentLanguage

    ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = !langExpanded }) {
        SelectAllOutlinedTextField(
            value = displayLangLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
            langOptions.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onLanguageSelected(code); langExpanded = false }
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }

    // Storage
    if (getPlatform().name != "Web with Kotlin/JS") {
        var pathInput by remember { mutableStateOf(currentStorageDir) }
        val dirPicker = rememberDirectoryPicker(stringResource(Res.string.settings_storage_dir_title)) { path ->
            if (path != null) {
                pathInput = path; onStorageDirSaved(path)
            }
        }
        SelectAllOutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it; onStorageDirSaved(it) },
            label = { Text(stringResource(Res.string.settings_storage_dir_title)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingIcon = { IconButton(onClick = { dirPicker() }) { Icon(Icons.Default.Folder, null) } },
            shape = AppShapes.medium
        )
    }
}