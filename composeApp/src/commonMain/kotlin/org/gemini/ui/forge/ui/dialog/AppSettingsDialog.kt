package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import geminiuiforge.composeapp.generated.resources.*
import org.gemini.ui.forge.ProjectConfig
import org.gemini.ui.forge.ResizeHorizontalIcon
import org.gemini.ui.forge.getPlatform
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.utils.rememberDirectoryPicker

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
    envStatus: FullEnvironmentStatus,
    initialCategory: SettingCategory = SettingCategory.GENERAL,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit = {},
    onPromptLangSelected: (PromptLanguage) -> Unit = {},
    onShortcutSaved: (ShortcutAction, String) -> Unit = { _, _ -> },
    onCheckEnv: () -> Unit = {},
    onInstallEnvItem: (String) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onStartUpdate: (UpdateInfo) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var leftWeight by remember { mutableStateOf(0.3f) }

    LaunchedEffect(Unit) {
        org.gemini.ui.forge.utils.AppLogger.d("AppSettingsDialog", "Current Project Version: ${ProjectConfig.VERSION}")
    }

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
                    Text(
                        text = stringResource(Res.string.settings_app_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Navigation
                        Box(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
                            val leftScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(leftScrollState).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                                .pointerHoverIcon(ResizeHorizontalIcon)
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
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (selectedCategory) {
                                    SettingCategory.GENERAL -> GeneralSettings(
                                        currentTheme, currentLanguage, currentStorageDir,
                                        onThemeSelected, onLanguageSelected, onStorageDirSaved
                                    )

                                    SettingCategory.AI -> AISettings(
                                        currentApiKey, currentMaxRetries, currentPromptLang,
                                        onApiKeySaved, onMaxRetriesSaved, onPromptLangSelected
                                    )

                                    SettingCategory.ENVIRONMENT -> EnvironmentSettings(
                                        envStatus, onCheckEnv, onInstallEnvItem
                                    )

                                    SettingCategory.SHORTCUTS -> ShortcutSettings(shortcuts, onShortcutSaved)
                                    SettingCategory.ABOUT -> AboutSection(updateStatus, onCheckUpdate, onStartUpdate)
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
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = AppShapes.medium
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
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = AppShapes.medium
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
    if (getPlatform().name != "Web with Kotlin/JS") {
        var pathInput by remember { mutableStateOf(currentStorageDir) }
        val dirPicker = rememberDirectoryPicker(stringResource(Res.string.settings_storage_dir_title)) { path ->
            if (path != null) {
                pathInput = path; onStorageDirSaved(path)
            }
        }
        OutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it; onStorageDirSaved(it) },
            label = { Text(stringResource(Res.string.settings_storage_dir_title)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { dirPicker() }) { Icon(Icons.Default.Folder, null) } },
            shape = AppShapes.medium
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
        placeholder = { Text(stringResource(Res.string.settings_gemini_key_placeholder)) },
        shape = AppShapes.medium
    )

    var retriesExpanded by remember { mutableStateOf(false) }
    val retryOptions = listOf(0, 1, 2, 3, 5, 10)

    ExposedDropdownMenuBox(expanded = retriesExpanded, onExpandedChange = { retriesExpanded = !retriesExpanded }) {
        OutlinedTextField(
            value = currentMaxRetries.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_max_retries_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(retriesExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = AppShapes.medium
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

    ExposedDropdownMenuBox(
        expanded = promptLangExpanded,
        onExpandedChange = { promptLangExpanded = !promptLangExpanded }) {
        OutlinedTextField(
            value = currentPromptLang.displayName,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_prompt_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptLangExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = AppShapes.medium
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
private fun EnvironmentSettings(
    status: FullEnvironmentStatus,
    onCheck: () -> Unit,
    onInstall: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingSectionTitle(stringResource(Res.string.env_python_check_title))
        TextButton(onClick = onCheck, enabled = !status.isChecking) {
            if (status.isChecking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.env_action_check))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        status.items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (item.isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(item.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (item.isInstalled) "${stringResource(Res.string.env_status_installed)}: ${item.version ?: "Unknown"}" else stringResource(
                                Res.string.env_status_missing
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isInstalled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!item.isInstalled && item.name != "python") {
                        Button(
                            onClick = { onInstall(item.name) },
                            enabled = !item.isInstalling,
                            shape = AppShapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (item.isInstalling) CircularProgressIndicator(
                                Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            else Text(
                                stringResource(Res.string.env_action_install),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (item.isInstalling && item.installLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).background(Color.Black).padding(8.dp)
                    ) {
                        val logScroll = rememberScrollState()
                        Text(
                            text = item.installLogs.joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00FF00),
                            modifier = Modifier.verticalScroll(logScroll)
                        )
                        LaunchedEffect(item.installLogs.size) { logScroll.animateScrollTo(logScroll.maxValue) }
                    }
                }
            }
        }
    }

    if (status.items.any { it.name == "python" && !it.isInstalled }) {
        Text(
            text = stringResource(Res.string.env_error_missing_python),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
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
            val labelStr = when (action) {
                ShortcutAction.UNDO -> stringResource(Res.string.shortcut_undo)
                ShortcutAction.REDO -> stringResource(Res.string.shortcut_redo)
                ShortcutAction.SAVE -> stringResource(Res.string.shortcut_save)
                ShortcutAction.RENAME -> stringResource(Res.string.shortcut_rename)
                ShortcutAction.DELETE -> stringResource(Res.string.shortcut_delete)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(labelStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    action.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
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
                singleLine = true,
                shape = AppShapes.medium
            )
        }
    }
}

@Composable
private fun AboutSection(
    updateStatus: UpdateStatus,
    onCheckUpdate: () -> Unit,
    onStartUpdate: (UpdateInfo) -> Unit
) {
    SettingSectionTitle(stringResource(Res.string.settings_category_about))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        // 调试：获取原始模板和参数
        val appName = stringResource(Res.string.app_name)
        val rawVersionTemplate = stringResource(Res.string.about_version)
        val currentProjectVersion = ProjectConfig.VERSION
        
        // 显式执行替换逻辑，确保 {0} 被识别并填充
        val substitutedVersion = rawVersionTemplate.replace("{0}", currentProjectVersion)

        LaunchedEffect(Unit) {
            org.gemini.ui.forge.utils.AppLogger.d("AboutSection_Debug", "--- Version Debug Start ---")
            org.gemini.ui.forge.utils.AppLogger.d("AboutSection_Debug", "Raw Template: '$rawVersionTemplate'")
            org.gemini.ui.forge.utils.AppLogger.d("AboutSection_Debug", "Substituted Result: '$substitutedVersion'")
            org.gemini.ui.forge.utils.AppLogger.d("AboutSection_Debug", "--- Version Debug End ---")
        }

        Text(
            appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            substitutedVersion,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        // 更新检测区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = AppShapes.medium
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (updateStatus) {
                    is UpdateStatus.Idle -> {
                        Button(onClick = onCheckUpdate, shape = AppShapes.medium) {
                            Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.update_check_action))
                        }
                    }

                    is UpdateStatus.Checking -> {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(
                            stringResource(Res.string.update_checking),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    is UpdateStatus.Available -> {
                        Text(
                            stringResource(Res.string.update_available_title, updateStatus.info.version),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            updateStatus.info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(onClick = { onStartUpdate(updateStatus.info) }, shape = AppShapes.medium) {
                            Text(stringResource(Res.string.update_action_now))
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                        Text(
                            stringResource(Res.string.update_downloading, (updateStatus.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(Res.string.update_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    is UpdateStatus.UpToDate -> {
                        Text(
                            stringResource(Res.string.update_latest),
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onCheckUpdate, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                stringResource(Res.string.update_check_action),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    is UpdateStatus.Error -> {
                        Text(updateStatus.message, color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = onCheckUpdate,
                            modifier = Modifier.padding(top = 8.dp),
                            shape = AppShapes.medium
                        ) { Text(stringResource(Res.string.update_check_action)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = AppShapes.medium
        ) {
            Text(
                text = stringResource(Res.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.about_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
