package org.gemini.ui.forge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert

@Composable
fun AppTopBar(
    currentScreen: AppScreen,
    onNavigateHome: () -> Unit,
    onLanguageChangeRequested: (String) -> Unit,
    currentTheme: ThemeMode,
    onThemeChangeRequested: (ThemeMode) -> Unit,
    onGenerateTemplateClicked: () -> Unit = {},
    currentApiKey: String = "",
    onApiKeyChanged: (String) -> Unit = {}
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
            currentApiKey = currentApiKey,
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
            }
        )
    }
}

@Composable
fun AppSettingsDialog(
    currentTheme: ThemeMode,
    currentApiKey: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey) }

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
                
                Column(modifier = Modifier.padding(16.dp)) {
                    // API Key Settings
                    Text(
                        text = "Gemini API 密钥",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { 
                            apiKeyInput = it 
                            onApiKeySaved(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(Res.string.settings_language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onLanguageSelected("en") }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(Res.string.language_english), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onLanguageSelected("zh") }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(Res.string.language_chinese), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(Res.string.settings_theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ThemeOptionRow(
                        label = stringResource(Res.string.theme_system),
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { onThemeSelected(ThemeMode.SYSTEM) }
                    )
                    ThemeOptionRow(
                        label = stringResource(Res.string.theme_light),
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onThemeSelected(ThemeMode.LIGHT) }
                    )
                    ThemeOptionRow(
                        label = stringResource(Res.string.theme_dark),
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onThemeSelected(ThemeMode.DARK) }
                    )
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