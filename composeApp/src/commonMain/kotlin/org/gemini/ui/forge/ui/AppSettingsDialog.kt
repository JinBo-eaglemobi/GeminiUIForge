package org.gemini.ui.forge.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.viewmodel.ThemeMode
import org.gemini.ui.forge.viewmodel.PromptLanguage
import org.gemini.ui.forge.domain.ShortcutAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    currentTheme: ThemeMode,
    currentLanguage: String,
    currentApiKey: String,
    currentStorageDir: String,
    currentMaxRetries: Int = 3,
    currentPromptLang: PromptLanguage = PromptLanguage.AUTO,
    shortcuts: Map<ShortcutAction, String>,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit = {},
    onPromptLangSelected: (PromptLanguage) -> Unit = {},
    onShortcutSaved: (ShortcutAction, String) -> Unit = { _, _ -> }
) {
    var selectedCategory by remember { mutableStateOf(SettingCategory.GENERAL) }
    var leftWeight by remember { mutableStateOf(0.3f) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(Res.string.settings_app_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null) }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Navigation
                        Box(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
                            val leftScrollState = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(leftScrollState).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SettingCategory.entries.forEach { category ->
                                    val isSelected = selectedCategory == category
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedCategory = category },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = category.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(category.labelRes),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(leftScrollState)
                            )
                        }

                        // Draggable Divider
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                .pointerHoverIcon(org.gemini.ui.forge.ResizeHorizontalIcon)
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        val deltaWeight = delta / totalWidthPx
                                        leftWeight = (leftWeight + deltaWeight).coerceIn(0.2f, 0.45f)
                                    }
                                )
                        )

                        // Right Content
                        Box(modifier = Modifier.weight(1f - leftWeight).fillMaxHeight()) {
                            val rightScrollState = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (selectedCategory) {
                                    SettingCategory.GENERAL -> GeneralSettings(
                                        currentTheme, currentLanguage, currentStorageDir,
                                        onThemeSelected, onLanguageSelected, onStorageDirSaved
                                    )
                                    SettingCategory.AI -> AISettings(
                                        currentApiKey, currentMaxRetries, currentPromptLang,
                                        onApiKeySaved, onMaxRetriesSaved, onPromptLangSelected
                                    )
                                    SettingCategory.SHORTCUTS -> ShortcutSettings(shortcuts, onShortcutSaved)
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(rightScrollState)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettings(
    currentTheme: ThemeMode,
    currentLanguage: String,
    currentStorageDir: String,
    onThemeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit
) {
    SettingSectionTitle(stringResource(Res.string.settings_category_general))
    
    // Theme Dropdown
    var themeExpanded by remember { mutableStateOf(false) }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
        ThemeMode.LIGHT to stringResource(Res.string.theme_light),
        ThemeMode.DARK to stringResource(Res.string.theme_dark)
    )
    val currentThemeLabel = themeOptions.find { it.first == currentTheme }?.second ?: ""

    ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = !themeExpanded }) {
        OutlinedTextField(
            value = currentThemeLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_appearance_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
            themeOptions.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onThemeSelected(mode); themeExpanded = false }
                )
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
        OutlinedTextField(
            value = displayLangLabel,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
            langOptions.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onLanguageSelected(code); langExpanded = false }
                )
            }
        }
    }

    // Storage
    if (org.gemini.ui.forge.getPlatform().name != "Web with Kotlin/JS") {
        var pathInput by remember { mutableStateOf(currentStorageDir) }
        val dirPicker = org.gemini.ui.forge.utils.rememberDirectoryPicker(stringResource(Res.string.settings_storage_dir_title)) { path ->
            if (path != null) { pathInput = path; onStorageDirSaved(path) }
        }
        OutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it; onStorageDirSaved(it) },
            label = { Text(stringResource(Res.string.settings_storage_dir_title)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { dirPicker() }) { Icon(Icons.Default.Folder, null) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AISettings(
    currentApiKey: String,
    currentMaxRetries: Int,
    currentPromptLang: PromptLanguage,
    onApiKeySaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit,
    onPromptLangSelected: (PromptLanguage) -> Unit
) {
    SettingSectionTitle(stringResource(Res.string.settings_category_ai))
    
    var keyInput by remember { mutableStateOf(currentApiKey) }
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it; onApiKeySaved(it) },
        label = { Text(stringResource(Res.string.settings_gemini_key_title)) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(stringResource(Res.string.settings_gemini_key_placeholder)) }
    )
    
    var retriesExpanded by remember { mutableStateOf(false) }
    val retryOptions = listOf(0, 1, 2, 3, 5, 10)
    
    ExposedDropdownMenuBox(expanded = retriesExpanded, onExpandedChange = { retriesExpanded = !retriesExpanded }) {
        OutlinedTextField(
            value = currentMaxRetries.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_max_retries_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(retriesExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = retriesExpanded, onDismissRequest = { retriesExpanded = false }) {
            retryOptions.forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = { onMaxRetriesSaved(count); retriesExpanded = false }
                )
            }
        }
    }
    
    var promptLangExpanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(expanded = promptLangExpanded, onExpandedChange = { promptLangExpanded = !promptLangExpanded }) {
        OutlinedTextField(
            value = currentPromptLang.displayName,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_prompt_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptLangExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = promptLangExpanded, onDismissRequest = { promptLangExpanded = false }) {
            PromptLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayName) },
                    onClick = { onPromptLangSelected(lang); promptLangExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun ShortcutSettings(
    shortcuts: Map<ShortcutAction, String>,
    onShortcutSaved: (ShortcutAction, String) -> Unit
) {
    SettingSectionTitle(stringResource(Res.string.settings_shortcuts_title))
    
    shortcuts.forEach { (action, currentKey) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelStr = when(action) {
                ShortcutAction.UNDO -> stringResource(Res.string.shortcut_undo)
                ShortcutAction.REDO -> stringResource(Res.string.shortcut_redo)
                ShortcutAction.SAVE -> stringResource(Res.string.shortcut_save)
                ShortcutAction.RENAME -> stringResource(Res.string.shortcut_rename)
                ShortcutAction.DELETE -> stringResource(Res.string.shortcut_delete)
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(labelStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(action.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            
            var editingKey by remember(currentKey) { mutableStateOf(currentKey) }
            
            OutlinedTextField(
                value = editingKey,
                onValueChange = { 
                    editingKey = it
                    onShortcutSaved(action, it)
                },
                modifier = Modifier.width(160.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true
            )
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}



@Composable
fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
