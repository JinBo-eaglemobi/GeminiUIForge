package org.gemini.ui.forge.ui.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.PaddingValues
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.component.SelectAllOutlinedTextField
import androidx.compose.foundation.gestures.*
import org.gemini.ui.forge.ui.common.VerticalScrollbarAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    currentTheme: ThemeMode,
    currentLayoutMode: org.gemini.ui.forge.model.app.LayoutMode,
    currentLanguage: String,
    currentApiKey: String,
    currentStorageDir: String,
    currentMaxRetries: Int = 3,
    currentImageGenCount: Int = 4,
    currentPromptLang: PromptLanguage = PromptLanguage.AUTO,
    shortcuts: Map<ShortcutAction, String>,
    envStatus: FullEnvironmentStatus,
    pipPackages: List<PipPackageInfo> = emptyList(),
    isPipLoading: Boolean = false,
    pipLogs: List<String> = emptyList(),
    isPipActionInProgress: Boolean = false,
    searchResult: PipPackageInfo? = null,
    isSearching: Boolean = false,
    topMarketPackages: List<PipPackageInfo> = emptyList(),
    isMarketLoading: Boolean = false,
    marketPage: Int = 0,
    initialCategory: SettingCategory = SettingCategory.GENERAL,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onLayoutModeSelected: (org.gemini.ui.forge.model.app.LayoutMode) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onApiKeySaved: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit = {},
    onImageGenCountSaved: (Int) -> Unit = {},
    onPromptLangSelected: (PromptLanguage) -> Unit = {},
    onShortcutSaved: (ShortcutAction, String) -> Unit = { _, _ -> },
    onCheckEnv: () -> Unit = {},
    onInstallEnvItem: (String) -> Unit = {},
    onUninstallEnvItem: (String) -> Unit = {},
    onBatchInstallPip: (List<String>) -> Unit = {},
    onBatchUninstallPip: (List<String>) -> Unit = {},
    onOpenPackageUrl: (String) -> Unit = {},
    onSearchPipPackage: (String) -> Unit = {},
    onClearSearchResult: () -> Unit = {},
    onLoadMarketPage: (Int) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onStartUpdate: (UpdateInfo) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var leftWeight by remember { mutableStateOf(0.3f) }

    LaunchedEffect(Unit) {
        org.gemini.ui.forge.utils.AppLogger.d("AppSettingsDialog", "Current Project Version: ${ProjectConfig.VERSION}")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false // 允许自定义超出默认系统宽度的尺寸
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(horizontal = LocalAppSpacing.current.medium, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.settings_app_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(LocalAppSpacing.current.extraLarge)) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Navigation
                        Box(modifier = Modifier.weight(leftWeight).fillMaxHeight()) {
                            val leftScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(leftScrollState).padding(LocalAppSpacing.current.small),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.extraSmall)
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
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                scrollState = leftScrollState
                            )
                        }

                        // Draggable Divider
                        Box(
                            modifier = Modifier
                                .width(LocalAppSpacing.current.extraSmall)
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
                                modifier = Modifier.fillMaxSize().verticalScroll(rightScrollState).padding(LocalAppSpacing.current.medium),
                                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.medium)
                            ) {
                                when (selectedCategory) {
                                    SettingCategory.GENERAL -> GeneralSettings(
                                        currentTheme, currentLayoutMode, currentLanguage, currentStorageDir,
                                        onThemeSelected, onLayoutModeSelected, onLanguageSelected, onStorageDirSaved
                                    )

                                    SettingCategory.AI -> AISettings(
                                        currentApiKey, currentMaxRetries, currentImageGenCount, currentPromptLang,
                                        onApiKeySaved, onMaxRetriesSaved, onImageGenCountSaved, onPromptLangSelected
                                    )

                                    SettingCategory.ENVIRONMENT -> EnvironmentSettings(
                                        status = envStatus,
                                        pipPackages = pipPackages,
                                        isPipLoading = isPipLoading,
                                        pipLogs = pipLogs,
                                        isPipActionInProgress = isPipActionInProgress,
                                        searchResult = searchResult,
                                        isSearching = isSearching,
                                        topMarketPackages = topMarketPackages,
                                        isMarketLoading = isMarketLoading,
                                        marketPage = marketPage,
                                        onCheck = onCheckEnv,
                                        onInstall = onInstallEnvItem,
                                        onUninstall = onUninstallEnvItem,
                                        onBatchInstallPip = onBatchInstallPip,
                                        onBatchUninstallPip = onBatchUninstallPip,
                                        onOpenPackageUrl = onOpenPackageUrl,
                                        onSearchPipPackage = onSearchPipPackage,
                                        onClearSearchResult = onClearSearchResult,
                                        onLoadMarketPage = onLoadMarketPage
                                    )

                                    SettingCategory.SHORTCUTS -> ShortcutSettings(shortcuts, onShortcutSaved)
                                    SettingCategory.ABOUT -> AboutSection(updateStatus, onCheckUpdate, onStartUpdate)
                                }
                            }
                            VerticalScrollbarAdapter(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                scrollState = rightScrollState
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
    currentLayoutMode: org.gemini.ui.forge.model.app.LayoutMode,
    currentLanguage: String,
    currentStorageDir: String,
    onThemeSelected: (ThemeMode) -> Unit,
    onLayoutModeSelected: (org.gemini.ui.forge.model.app.LayoutMode) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onStorageDirSaved: (String) -> Unit
) {
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

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
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }

    // LayoutMode Dropdown
    var layoutExpanded by remember { mutableStateOf(false) }
    val layoutOptions = listOf(
        org.gemini.ui.forge.model.app.LayoutMode.AUTO to stringResource(Res.string.layout_mode_auto),
        org.gemini.ui.forge.model.app.LayoutMode.TOUCH to stringResource(Res.string.layout_mode_touch),
        org.gemini.ui.forge.model.app.LayoutMode.COMPACT to stringResource(Res.string.layout_mode_compact)
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
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
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
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AISettings(
    currentApiKey: String,
    currentMaxRetries: Int,
    currentImageGenCount: Int,
    currentPromptLang: PromptLanguage,
    onApiKeySaved: (String) -> Unit,
    onMaxRetriesSaved: (Int) -> Unit,
    onImageGenCountSaved: (Int) -> Unit,
    onPromptLangSelected: (PromptLanguage) -> Unit
) {
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

    SettingSectionTitle(stringResource(Res.string.settings_category_ai))

    var keyInput by remember { mutableStateOf(currentApiKey) }
    SelectAllOutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it; onApiKeySaved(it) },
        label = { Text(stringResource(Res.string.settings_gemini_key_title)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(stringResource(Res.string.settings_gemini_key_placeholder)) },
        shape = AppShapes.medium
    )

    var retriesExpanded by remember { mutableStateOf(false) }
    val retryOptions = listOf(0, 1, 2, 3, 5, 10)

    ExposedDropdownMenuBox(expanded = retriesExpanded, onExpandedChange = { retriesExpanded = !retriesExpanded }) {
        SelectAllOutlinedTextField(
            value = currentMaxRetries.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_max_retries_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(retriesExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

    var genCountExpanded by remember { mutableStateOf(false) }
    val genCountOptions = listOf(1, 2, 4, 8, 12, 16)

    ExposedDropdownMenuBox(expanded = genCountExpanded, onExpandedChange = { genCountExpanded = !genCountExpanded }) {
        SelectAllOutlinedTextField(
            value = currentImageGenCount.toString(),
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_image_gen_count_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genCountExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = genCountExpanded, onDismissRequest = { genCountExpanded = false }) {
            genCountOptions.forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = { onImageGenCountSaved(count); genCountExpanded = false }
                )
            }
        }
    }

    var promptLangExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = promptLangExpanded,
        onExpandedChange = { promptLangExpanded = !promptLangExpanded }) {
        SelectAllOutlinedTextField(
            value = currentPromptLang.displayName,
            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(Res.string.settings_prompt_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptLangExpanded) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = AppShapes.medium
        )
        ExposedDropdownMenu(expanded = promptLangExpanded, onDismissRequest = { promptLangExpanded = false }) {
            PromptLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.displayName) },
                    onClick = { onPromptLangSelected(lang); promptLangExpanded = false }
                , contentPadding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding, modifier = if (isCompact) Modifier.height(32.dp) else Modifier)
            }
        }
    }
}

@Composable
private fun EnvironmentSettings(
    status: FullEnvironmentStatus,
    pipPackages: List<PipPackageInfo>,
    isPipLoading: Boolean,
    pipLogs: List<String>,
    isPipActionInProgress: Boolean,
    searchResult: PipPackageInfo?,
    isSearching: Boolean,
    topMarketPackages: List<PipPackageInfo>,
    isMarketLoading: Boolean,
    marketPage: Int,
    onCheck: () -> Unit,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onBatchInstallPip: (List<String>) -> Unit,
    onBatchUninstallPip: (List<String>) -> Unit,
    onOpenPackageUrl: (String) -> Unit,
    onSearchPipPackage: (String) -> Unit,
    onClearSearchResult: () -> Unit,
    onLoadMarketPage: (Int) -> Unit
) {
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

    var envTab by remember { mutableStateOf(0) }

    // Segmented Button 风格的 Tab
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShapes.medium)
            .padding(LocalAppSpacing.current.extraSmall),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("核心依赖", "本地扩展管理", "云端探索市场").forEachIndexed { index, title ->
            val isSelected = envTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { 
                        envTab = index
                        if (index == 2 && topMarketPackages.isEmpty()) onLoadMarketPage(0)
                    }
                    .padding(vertical = LocalAppSpacing.current.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(LocalAppSpacing.current.medium))

    if (envTab == 0) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle(stringResource(Res.string.env_python_check_title))
            TextButton(onClick = onCheck, enabled = !status.isChecking) {
                if (status.isChecking) CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.medium), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, Modifier.size(LocalAppSpacing.current.medium))
                Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                Text(stringResource(Res.string.env_action_check))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
            status.items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = AppShapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (item.isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(LocalAppSpacing.current.large)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(item.labelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.isOutdated) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = AppShapes.small) {
                                        Text("有更新", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = LocalAppSpacing.current.extraSmall, vertical = 2.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                            val verStr = if (item.isInstalled) "${stringResource(Res.string.env_status_installed)}: ${item.version ?: "Unknown"}" else stringResource(Res.string.env_status_missing)
                            Text(
                                text = verStr + if (item.isOutdated) " ➜ ${item.latestVersion}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isInstalled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }
                        
                        if (item.isOutdated && !item.isInstalling) {
                            Button(
                                onClick = { onInstall(item.name) },
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = LocalAppSpacing.current.extraSmall),
                                modifier = Modifier.height(LocalAppSpacing.current.extraLarge).padding(end = LocalAppSpacing.current.small)
                            ) {
                                Text("更新", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (!item.isInstalled) {
                            Button(
                                onClick = { onInstall(item.name) },
                                enabled = !item.isInstalling,
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = LocalAppSpacing.current.extraSmall),
                                modifier = Modifier.height(LocalAppSpacing.current.extraLarge)
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
                        } else {
                            OutlinedButton(
                                onClick = { onUninstall(item.name) },
                                enabled = !item.isInstalling,
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = LocalAppSpacing.current.extraSmall),
                                modifier = Modifier.height(LocalAppSpacing.current.extraLarge),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (item.isInstalling) CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    strokeWidth = 2.dp
                                )
                                else Text(
                                    stringResource(Res.string.env_action_uninstall),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    if (item.isInstalling && item.installLogs.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(max = 120.dp).background(Color.Black).padding(LocalAppSpacing.current.small)
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = AppShapes.medium
            ) {
                Column(Modifier.padding(LocalAppSpacing.current.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(LocalAppSpacing.current.small))
                        Text(
                            stringResource(Res.string.env_error_missing_python),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onInstall("python") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = AppShapes.medium
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(LocalAppSpacing.current.small))
                        Text(stringResource(Res.string.env_action_install))
                    }
                }
            }
        }
    } else if (envTab == 1) {
        // Pip Package Manager Tab - 纯本地管理
        var selectedPackages by remember { mutableStateOf(setOf<String>()) }
        
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle("Python 本地包管理")
            Row(horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small)) {
                if (selectedPackages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            onBatchUninstallPip(selectedPackages.toList())
                            selectedPackages = emptySet()
                        },
                        enabled = !isPipActionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("卸载已选 (${selectedPackages.size})", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onBatchInstallPip(selectedPackages.toList())
                            selectedPackages = emptySet()
                        },
                        enabled = !isPipActionInProgress
                    ) {
                        Text("更新已选 (${selectedPackages.size})", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onCheck, enabled = !isPipLoading && !isPipActionInProgress) {
                    if (isPipLoading) CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.medium), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, Modifier.size(LocalAppSpacing.current.medium))
                    Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                    Text("刷新")
                }
            }
        }

        if (isPipActionInProgress && pipLogs.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(max = 120.dp).background(Color.Black).padding(LocalAppSpacing.current.small)
            ) {
                val logScroll = rememberScrollState()
                Text(
                    text = pipLogs.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FF00),
                    modifier = Modifier.verticalScroll(logScroll)
                )
                LaunchedEffect(pipLogs.size) { logScroll.animateScrollTo(logScroll.maxValue) }
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
        }

        if (pipPackages.isEmpty() && !isPipLoading) {
            Text("没有检测到 Python 环境或没有安装包。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val installed = pipPackages.filter { it.isInstalled }

            if (installed.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦 已安装的包 (${installed.size})", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = LocalAppSpacing.current.extraSmall).weight(1f))
                    TextButton(onClick = {
                        selectedPackages = if (selectedPackages.containsAll(installed.map { it.name })) emptySet()
                        else selectedPackages + installed.map { it.name }
                    }) {
                        Text(if (selectedPackages.containsAll(installed.map { it.name })) "全不选" else "全选", fontSize = 12.sp)
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.extraSmall)) {
                    installed.forEach { pkg ->
                        val isSelected = selectedPackages.contains(pkg.name)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                selectedPackages = if (isSelected) selectedPackages - pkg.name else selectedPackages + pkg.name
                            },
                            shape = AppShapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = LocalAppSpacing.current.small, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    selectedPackages = if (checked) selectedPackages + pkg.name else selectedPackages - pkg.name
                                })
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(pkg.name, fontWeight = FontWeight.Medium)
                                        if (pkg.isOutdated) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = AppShapes.small) {
                                                Text("有更新", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = LocalAppSpacing.current.extraSmall, vertical = 2.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    }
                                    Text("版本: ${pkg.version}${if (pkg.isOutdated) " ➜ ${pkg.latestVersion}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                if (pkg.isOutdated) {
                                    IconButton(onClick = { onOpenPackageUrl(pkg.name) }, modifier = Modifier.size(LocalAppSpacing.current.extraLarge)) {
                                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "更新详情", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.width(LocalAppSpacing.current.extraSmall))
                                    Button(
                                        onClick = { onBatchInstallPip(listOf(pkg.name)) },
                                        enabled = !isPipActionInProgress,
                                        shape = AppShapes.medium,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) { Text("更新", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Tab 2: 云端探索市场 - 网格视图
        var searchQuery by remember { mutableStateOf("") }
        var selectedPackages by remember { mutableStateOf(setOf<String>()) }
        
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingSectionTitle("探索 PyPI 扩展市场")
            if (selectedPackages.isNotEmpty()) {
                Button(
                    onClick = {
                        onBatchInstallPip(selectedPackages.toList())
                        selectedPackages = emptySet()
                    },
                    enabled = !isPipActionInProgress
                ) {
                    Text("安装已选 (${selectedPackages.size})", fontSize = 12.sp)
                }
            }
        }

        // 搜索框
        SelectAllOutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索 PyPI 全网扩展包...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(bottom = LocalAppSpacing.current.small),
            singleLine = true,
            shape = AppShapes.medium,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; onClearSearchResult() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = { onSearchPipPackage(searchQuery) }) {
                        if (isSearching) CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.medium), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                    }
                }
            }
        )

        if (searchResult != null) {
            Text("🔍 搜索结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = LocalAppSpacing.current.extraSmall))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(bottom = LocalAppSpacing.current.small),
                shape = AppShapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(searchResult.name, fontWeight = FontWeight.Bold)
                        Text(searchResult.description, style = MaterialTheme.typography.labelSmall)
                        if (!searchResult.latestVersion.isNullOrEmpty()) {
                            Text("最新版本: ${searchResult.latestVersion}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = { onBatchInstallPip(listOf(searchResult.name)) },
                        enabled = !isPipActionInProgress,
                        shape = AppShapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = LocalAppSpacing.current.extraSmall),
                        modifier = Modifier.height(LocalAppSpacing.current.extraLarge)
                    ) { Text("安装") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = LocalAppSpacing.current.small))
        }

        if (isPipActionInProgress && pipLogs.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(max = 120.dp).background(Color.Black).padding(LocalAppSpacing.current.small)
            ) {
                val logScroll = rememberScrollState()
                Text(
                    text = pipLogs.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FF00),
                    modifier = Modifier.verticalScroll(logScroll)
                )
                LaunchedEffect(pipLogs.size) { logScroll.animateScrollTo(logScroll.maxValue) }
            }
            Spacer(Modifier.height(LocalAppSpacing.current.small))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("🔥 热门排行榜", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marketPage > 0) {
                    TextButton(onClick = { onLoadMarketPage(marketPage - 1) }, enabled = !isMarketLoading) { Text("上一页", fontSize = 12.sp) }
                }
                Text("第 ${marketPage + 1} 页", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onLoadMarketPage(marketPage + 1) }, enabled = !isMarketLoading) { Text("下一页", fontSize = 12.sp) }
            }
        }

        if (isMarketLoading) {
            Box(Modifier.fillMaxWidth().padding(LocalAppSpacing.current.extraLarge), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                verticalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small),
                horizontalArrangement = Arrangement.spacedBy(LocalAppSpacing.current.small),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 300.dp, max = 600.dp) // 使用定高避免 Scroll 嵌套 Crash
            ) {
                items(topMarketPackages) { pkg ->
                    val isSelected = selectedPackages.contains(pkg.name)
                    val isAlreadyInstalled = pipPackages.any { it.name.lowercase() == pkg.name.lowercase() && it.isInstalled }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            if (!isAlreadyInstalled) selectedPackages = if (isSelected) selectedPackages - pkg.name else selectedPackages + pkg.name
                        },
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAlreadyInstalled) {
                                    Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                } else {
                                    Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                        selectedPackages = if (checked) selectedPackages + pkg.name else selectedPackages - pkg.name
                                    }, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(LocalAppSpacing.current.small))
                                Text(pkg.name, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                                
                                IconButton(onClick = { onOpenPackageUrl(pkg.name) }, modifier = Modifier.size(LocalAppSpacing.current.large)) {
                                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "详情", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(LocalAppSpacing.current.medium))
                                }
                            }
                            Spacer(Modifier.height(LocalAppSpacing.current.extraSmall))
                            Text(pkg.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.heightIn(min = LocalAppSpacing.current.extraLarge))
                            Spacer(Modifier.height(LocalAppSpacing.current.small))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                                if (isAlreadyInstalled) {
                                    Text("已安装", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                } else {
                                    Button(
                                        onClick = { onBatchInstallPip(listOf(pkg.name)) },
                                        enabled = !isPipActionInProgress,
                                        shape = AppShapes.medium,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(LocalAppSpacing.current.large)
                                    ) { Text("安装", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutSettings(
    shortcuts: Map<ShortcutAction, String>,
    onShortcutSaved: (ShortcutAction, String) -> Unit
) {
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

    SettingSectionTitle(stringResource(Res.string.settings_shortcuts_title))

    shortcuts.forEach { (action, currentKey) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(vertical = LocalAppSpacing.current.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelStr = when (action) {
                ShortcutAction.UNDO -> stringResource(Res.string.shortcut_undo)
                ShortcutAction.REDO -> stringResource(Res.string.shortcut_redo)
                ShortcutAction.SAVE -> stringResource(Res.string.shortcut_save)
                ShortcutAction.RENAME -> stringResource(Res.string.shortcut_rename)
                ShortcutAction.DELETE -> stringResource(Res.string.shortcut_delete)
                ShortcutAction.COPY -> stringResource(Res.string.shortcut_copy)
                ShortcutAction.PASTE -> stringResource(Res.string.shortcut_paste)
                ShortcutAction.CUT -> stringResource(Res.string.shortcut_cut)
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

            SelectAllOutlinedTextField(
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
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

    SettingSectionTitle(stringResource(Res.string.settings_category_about))

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                modifier = Modifier.size(LocalAppSpacing.current.extraLarge),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        // 获取应用名称和系统版本
        val appName = stringResource(Res.string.app_name)
        val currentProjectVersion = ProjectConfig.VERSION

        Text(
            appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(Res.string.about_version, currentProjectVersion),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(LocalAppSpacing.current.medium))

        // 更新检测区域
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = AppShapes.medium
        ) {
            Column(Modifier.padding(LocalAppSpacing.current.medium), horizontalAlignment = Alignment.CenterHorizontally) {
                when (updateStatus) {
                    is UpdateStatus.Idle -> {
                        Button(onClick = onCheckUpdate, shape = AppShapes.medium) {
                            Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(LocalAppSpacing.current.small))
                            Text(stringResource(Res.string.update_check_action))
                        }
                    }

                    is UpdateStatus.Checking -> {
                        CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.large))
                        Text(
                            stringResource(Res.string.update_checking),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
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
                            modifier = Modifier.padding(vertical = LocalAppSpacing.current.small)
                        )
                        Button(onClick = { onStartUpdate(updateStatus.info) }, shape = AppShapes.medium) {
                            Text(stringResource(Res.string.update_action_now))
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(LocalAppSpacing.current.small).clip(CircleShape)
                        )
                        Text(
                            stringResource(Res.string.update_downloading, (updateStatus.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        CircularProgressIndicator(Modifier.size(LocalAppSpacing.current.large), color = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(Res.string.update_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small)
                        )
                    }

                    is UpdateStatus.UpToDate -> {
                        Text(
                            stringResource(Res.string.update_latest),
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onCheckUpdate, modifier = Modifier.padding(top = LocalAppSpacing.current.extraSmall)) {
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
                            modifier = Modifier.padding(top = LocalAppSpacing.current.small),
                            shape = AppShapes.medium
                        ) { Text(stringResource(Res.string.update_check_action)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(LocalAppSpacing.current.medium))
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
        Spacer(Modifier.height(LocalAppSpacing.current.medium))
        Text(
            stringResource(Res.string.about_copyright),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    val isCompact = androidx.compose.material3.LocalMinimumInteractiveComponentSize.current == 0.dp

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = LocalAppSpacing.current.small)
    )
}
