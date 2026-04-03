package org.gemini.ui.forge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.viewmodel.ThemeMode
import org.gemini.ui.forge.viewmodel.AppScreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert

@Composable
fun AppTopBar(
    currentScreen: AppScreen,
    onNavigateHome: () -> Unit,
    onLanguageChangeRequested: (String) -> Unit,
    currentTheme: ThemeMode,
    currentLanguage: String = "zh",
    onThemeChangeRequested: (ThemeMode) -> Unit,
    onGenerateTemplateClicked: () -> Unit = {},
    currentApiKey: String = "",
    onApiKeyChanged: (String) -> Unit = {},
    currentStorageDir: String = "",
    onStorageDirChanged: (String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().height(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentScreen != AppScreen.HOME) {
                    IconButton(onClick = onNavigateHome, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_file)) },
                        onClick = { menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_generate_template)) },
                        onClick = { 
                            menuExpanded = false 
                            onGenerateTemplateClicked()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_settings)) },
                        onClick = {
                            menuExpanded = false
                            showSettingsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_help)) },
                        onClick = { menuExpanded = false }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        AppSettingsDialog(
            currentTheme = currentTheme,
            currentLanguage = currentLanguage,
            currentApiKey = currentApiKey,
            currentStorageDir = currentStorageDir,
            onDismiss = { showSettingsDialog = false },
            onLanguageSelected = { 
                onLanguageChangeRequested(it)
                showSettingsDialog = false
            },
            onThemeSelected = {
                onThemeChangeRequested(it)
            },
            onApiKeySaved = {
                onApiKeyChanged(it)
            },
            onStorageDirSaved = {
                onStorageDirChanged(it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    currentTheme: ThemeMode,
    currentLanguage: String,
    currentApiKey: String,
    currentStorageDir: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey) }
    var storageDirInput by remember { mutableStateOf(currentStorageDir) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                HorizontalDivider()
                
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                    // API Key Settings
                    Text(
                        text = stringResource(Res.string.settings_gemini_key),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { 
                            apiKeyInput = it 
                            onApiKeySaved(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text(stringResource(Res.string.settings_gemini_key_hint), style = MaterialTheme.typography.bodyMedium) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Storage Dir Settings (JS excluded)
                    if (org.gemini.ui.forge.getPlatform().name != "Web with Kotlin/JS") {
                        val dirPickerTitle = stringResource(Res.string.settings_storage_dir)
                        val dirPicker = org.gemini.ui.forge.utils.rememberDirectoryPicker(title = dirPickerTitle) { path ->
                            if (path != null) {
                                storageDirInput = path
                                onStorageDirSaved(path)
                            }
                        }

                        Text(
                            text = dirPickerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = storageDirInput,
                            onValueChange = { 
                                storageDirInput = it 
                                onStorageDirSaved(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text(stringResource(Res.string.settings_storage_dir_hint), style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { dirPicker() },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Select Directory",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Language Dropdown Settings
                    var langExpanded by remember { mutableStateOf(false) }
                    var langDropdownWidth by remember { mutableStateOf(0) }
                    val langOptions = listOf(
                        "en" to stringResource(Res.string.language_english),
                        "zh" to stringResource(Res.string.language_chinese)
                    )
                    val displayLangLabel = langOptions.find { it.first == currentLanguage }?.second ?: ""

                    Text(
                        text = stringResource(Res.string.settings_language),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { langDropdownWidth = it.size.width }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(48.dp).clickable { langExpanded = true }
                                .pointerHoverIcon(PointerIcon.Default),
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayLangLabel, style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.width(with(LocalDensity.current) { langDropdownWidth.toDp() })
                        ) {
                            langOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                            if (code == currentLanguage) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onLanguageSelected(code)
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Theme Dropdown Settings
                    var themeExpanded by remember { mutableStateOf(false) }
                    var themeDropdownWidth by remember { mutableStateOf(0) }
                    val themeOptions = listOf(
                        ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                        ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                        ThemeMode.DARK to stringResource(Res.string.theme_dark)
                    )
                    val currentThemeLabel = themeOptions.find { it.first == currentTheme }?.second ?: ""

                    Text(
                        text = stringResource(Res.string.settings_theme),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { themeDropdownWidth = it.size.width }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(48.dp).clickable { themeExpanded = true }
                                .pointerHoverIcon(PointerIcon.Default),
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(currentThemeLabel, style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                            modifier = Modifier.width(with(LocalDensity.current) { themeDropdownWidth.toDp() })
                        ) {
                            themeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                            if (mode == currentTheme) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onThemeSelected(mode)
                                        themeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
