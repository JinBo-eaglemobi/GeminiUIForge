package org.gemini.ui.forge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*

import androidx.compose.foundation.focusable
import kotlinx.coroutines.launch

import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.state.AppViewModel
import org.gemini.ui.forge.state.AppSettingsViewModel
import org.gemini.ui.forge.state.AppUpdateViewModel
import org.gemini.ui.forge.state.AppEnvViewModel
import org.gemini.ui.forge.ui.feature.home.HomeScreen
import org.gemini.ui.forge.ui.feature.assetgen.TemplateAssetGenScreen
import org.gemini.ui.forge.ui.feature.editor.TemplateEditorScreen
import org.gemini.ui.forge.ui.feature.analysis.TemplateGeneratorScreen
import org.gemini.ui.forge.ui.component.AppTopBar
import org.gemini.ui.forge.ui.theme.AppTheme
import org.gemini.ui.forge.utils.*
import org.gemini.ui.forge.service.*
import kotlin.time.Duration.Companion.milliseconds
import org.gemini.ui.forge.dialog.AppSettingsDialog
import org.gemini.ui.forge.ui.dialog.HelpDialog

private var originalSystemLanguage: String? = null

@Composable
fun App(typography: Typography? = null) {
    if (originalSystemLanguage == null) {
        originalSystemLanguage = androidx.compose.ui.text.intl.Locale.current.language
    }

    var languageKey by remember { mutableStateOf(0) }
    val templateRepo = remember { TemplateRepository() }
    val focusRequester = remember { FocusRequester() }

    var templatesList by remember { mutableStateOf(emptyList<Pair<String, ProjectState>>()) }

    key(languageKey) {
        // 四层 ViewModel 架构实现
        val viewModel: AppViewModel = viewModel { AppViewModel(templateRepo = templateRepo, cloudAssetManager = CloudAssetManager(ConfigManager()), aiService = AIGenerationService(CloudAssetManager(ConfigManager()))) }
        val settingsViewModel: AppSettingsViewModel = viewModel { AppSettingsViewModel(templateRepo = templateRepo) }
        val updateViewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel(templateRepo = templateRepo) }
        val envViewModel: AppEnvViewModel = viewModel { AppEnvViewModel() }
        
        val state by viewModel.state.collectAsState()
        val updateStatus by updateViewModel.status.collectAsState()
        val envStatus by envViewModel.status.collectAsState()
        
        val globalState = state.globalState

        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            try { focusRequester.requestFocus() } catch (e: Exception) { }
            // 1. 同步初始配置
            viewModel.syncInitialSettings(settingsViewModel.getConfigManager())
            // 2. 检查更新
            updateViewModel.checkForUpdates()
        }

        LaunchedEffect(globalState.languageCode) {
            val effectiveLang = if (globalState.languageCode == "auto") {
                val sysLang = originalSystemLanguage ?: androidx.compose.ui.text.intl.Locale.current.language
                if (sysLang.lowercase().startsWith("zh")) "zh" else "en"
            } else globalState.languageCode
            setAppLanguage(effectiveLang)
        }

        LaunchedEffect(globalState.currentScreen) {
            if (globalState.currentScreen == AppScreen.HOME) {
                templatesList = templateRepo.getTemplates()
            }
        }

        val availableModules = buildList {
            templatesList.forEach { (name, projectState) ->
                add(UIModule(id = name, nameStr = name, projectState = projectState, absolutePath = ""))
            }
        }

        var showCloudAssetDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showHelpDialog by remember { mutableStateOf(false) }
        var settingsInitialCategory by remember { mutableStateOf(SettingCategory.GENERAL) }

        val isEnvRequired = state.isGenerateTransparent && globalState.currentScreen == AppScreen.TEMPLATE_ASSET_GEN
        val isEnvReady = envStatus.isAllReady
        var showEnvWarning by remember { mutableStateOf(false) }
        
        LaunchedEffect(isEnvRequired, isEnvReady) {
            if (isEnvRequired && !isEnvReady && !envStatus.isChecking) {
                showEnvWarning = true
            }
        }

        AppTheme(
            themeMode = globalState.themeMode,
            typography = typography ?: androidx.compose.material3.MaterialTheme.typography
        ) {
            val coroutineScope = rememberCoroutineScope()

            if (showCloudAssetDialog) {
                org.gemini.ui.forge.ui.dialog.CloudAssetDialog(
                    cloudAssetManager = viewModel.cloudAssetManager,
                    onDismiss = { showCloudAssetDialog = false }
                )
            }

            if (showHelpDialog) {
                HelpDialog(onDismiss = { showHelpDialog = false })
            }

            if (showSettingsDialog) {
                AppSettingsDialog(
                    currentTheme = globalState.themeMode,
                    currentLanguage = globalState.languageCode,
                    currentApiKey = globalState.apiKey,
                    currentStorageDir = globalState.templateStorageDir,
                    currentMaxRetries = globalState.maxRetries,
                    currentPromptLang = globalState.promptLangPref,
                    shortcuts = globalState.shortcuts,
                    envStatus = envStatus,
                    initialCategory = settingsInitialCategory,
                    updateStatus = updateStatus,
                    onDismiss = { showSettingsDialog = false },
                    onLanguageSelected = { 
                        settingsViewModel.saveLanguage(it)
                        viewModel.setLanguage(it)
                        languageKey++
                    },
                    onThemeSelected = { 
                        viewModel.setThemeMode(it)
                    },
                    onApiKeySaved = { 
                        settingsViewModel.saveApiKey(it)
                        viewModel.updateApiKey(it)
                    },
                    onStorageDirSaved = { path ->
                        coroutineScope.launch {
                            if (settingsViewModel.updateStorageDir(path)) {
                                viewModel.updateStorageDirState(path)
                            }
                        }
                    },
                    onMaxRetriesSaved = { 
                        settingsViewModel.saveMaxRetries(it)
                        viewModel.updateMaxRetriesState(it)
                    },
                    onPromptLangSelected = { 
                        settingsViewModel.savePromptLanguagePref(it)
                        viewModel.setPromptLanguagePref(it)
                    },
                    onShortcutSaved = { action, key -> 
                        settingsViewModel.saveShortcut(action, key)
                        viewModel.updateShortcutState(action, key)
                    },
                    onCheckEnv = { envViewModel.checkEnvironment() },
                    onInstallEnvItem = { envViewModel.installEnvironmentItem(it) },
                    onCheckUpdate = { updateViewModel.checkForUpdates() },
                    onStartUpdate = { updateViewModel.performUpdate(it) }
                )
            }

            if (showEnvWarning) {
                AlertDialog(
                    onDismissRequest = { showEnvWarning = false },
                    title = { Text(stringResource(Res.string.env_dialog_title)) },
                    text = { Text(stringResource(Res.string.env_dialog_message)) },
                    confirmButton = {
                        Button(onClick = { 
                            showEnvWarning = false
                            settingsInitialCategory = SettingCategory.ENVIRONMENT
                            showSettingsDialog = true
                        }) { Text(stringResource(Res.string.action_go_to_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEnvWarning = false }) { Text(stringResource(Res.string.prop_cancel)) }
                    }
                )
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AppTopBar(
                        currentScreen = globalState.currentScreen,
                        onNavigateHome = { 
                            if (state.isGenerating) {
                                AppLogger.e("App", "资源生成中，请勿退出当前界面")
                            } else {
                                viewModel.navigateTo(AppScreen.HOME) 
                            }
                        },
                        onGenerateTemplateClicked = { if (!state.isGenerating) viewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                        onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                        onSaveClicked = {
                            if (globalState.currentScreen == AppScreen.TEMPLATE_EDITOR || globalState.currentScreen == AppScreen.TEMPLATE_ASSET_GEN) {
                                coroutineScope.launch { templateRepo.saveTemplate(state.projectName, state.project) }
                            }
                        },
                        onSettingsClicked = {
                            settingsInitialCategory = SettingCategory.GENERAL
                            showSettingsDialog = true
                        },
                        onHelpClicked = {
                            showHelpDialog = true
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                val isCtrl = event.isCtrlPressed
                                val isShift = event.isShiftPressed
                                val keyCode = event.key
                                val shortcutMap = globalState.shortcuts
                                fun matches(action: ShortcutAction): Boolean {
                                    val chord = shortcutMap[action] ?: action.defaultKey
                                    val parts = chord.uppercase().split("+")
                                    val needsCtrl = "CTRL" in parts
                                    val needsShift = "SHIFT" in parts
                                    val keyName = parts.last()
                                    if (needsCtrl != isCtrl || needsShift != isShift) return false
                                    return when (keyName) {
                                        "Z" -> keyCode == Key.Z
                                        "Y" -> keyCode == Key.Y
                                        "S" -> keyCode == Key.S
                                        "F2" -> keyCode == Key.F2
                                        "DELETE" -> keyCode == Key.Delete
                                        "BACKSPACE" -> keyCode == Key.Backspace
                                        else -> false
                                    }
                                }
                                when {
                                    matches(ShortcutAction.UNDO) -> { viewModel.undo(); true }
                                    matches(ShortcutAction.REDO) || (isCtrl && isShift && keyCode == Key.Z) -> { viewModel.redo(); true }
                                    matches(ShortcutAction.SAVE) -> { if (globalState.currentScreen == AppScreen.TEMPLATE_EDITOR || globalState.currentScreen == AppScreen.TEMPLATE_ASSET_GEN) { coroutineScope.launch { templateRepo.saveTemplate(state.projectName, state.project) } }; true }
                                    matches(ShortcutAction.DELETE) -> { state.selectedBlockId?.let { viewModel.deleteBlock(it) }; true }
                                    else -> false
                                }
                            } else false
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                ) {
                    when (globalState.currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                modules = availableModules,
                                onEditLayout = { moduleId ->
                                    val module = availableModules.find { it.id == moduleId }
                                    if (module?.projectState != null) viewModel.loadProject(module.nameStr ?: moduleId, module.projectState)
                                    viewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                                },
                                onGenerateUI = { moduleId ->
                                    val module = availableModules.find { it.id == moduleId }
                                    if (module?.projectState != null) viewModel.loadProject(module.nameStr ?: moduleId, module.projectState)
                                    viewModel.navigateTo(AppScreen.TEMPLATE_ASSET_GEN)
                                },
                                onDeleteModule = { moduleId -> coroutineScope.launch { templateRepo.deleteTemplate(moduleId); templatesList = templateRepo.getTemplates() } }
                            )
                        }

                        AppScreen.TEMPLATE_EDITOR -> {
                            TemplateEditorScreen(
                                state = state,
                                onPageSelected = { viewModel.onPageSelected(it) },
                                onBlockClicked = { viewModel.onBlockClicked(it) },
                                onBlockBoundsChanged = { id, l, t, r, b -> viewModel.updateBlockBounds(id, l, t, r, b) },
                                onBlockTypeChanged = { id, type -> viewModel.updateBlockType(id, type) },
                                onPromptChanged = { id, prompt -> viewModel.onBlockClicked(id); viewModel.onUserPromptChanged(prompt) },
                                onOptimizePrompt = { id, onComplete -> viewModel.optimizePrompt(id, globalState.effectiveApiKey, onComplete) },
                                onRefineArea = { id, bounds, instruction, onLog, onChunk, onComplete -> viewModel.onRefineArea(id, bounds, instruction, onLog, onChunk, onComplete) },
                                onRefineCustomArea = { bounds, instruction, onLog, onChunk, onComplete -> viewModel.onRefineCustomArea(bounds, instruction, onLog, onChunk, onComplete) },
                                onSwitchEditingLanguage = { viewModel.switchEditingLanguage(it) },
                                onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { viewModel.exitGroupEditMode() },
                                onAddBlock = { type -> viewModel.addBlock(type) },
                                onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) },
                                onDeleteBlock = { id -> viewModel.deleteBlock(id) },
                                onMoveBlock = { d, t, p -> viewModel.moveBlock(d, t, p) },
                                onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { old, new -> viewModel.renameBlock(old, new) },        
                                onToggleVisibility = { id, isVisible -> viewModel.toggleBlockVisibility(id, isVisible) },
                                onToggleAllVisibility = { isVisible -> viewModel.toggleAllBlocksVisibility(isVisible) },
                                onCancelAITask = { viewModel.cancelGeneration() },
                                onToggleAILog = { viewModel.toggleGenerationLogVisibility() },
                                onCloseAITaskDialog = { viewModel.closeAITaskDialog() },
                                onSaveTemplate = { coroutineScope.launch { templateRepo.saveTemplate(state.projectName, state.project) } }
                            )
                        }

                        AppScreen.TEMPLATE_ASSET_GEN -> {
                            TemplateAssetGenScreen(
                                state = state,
                                onPageSelected = { if (!state.isGenerating) viewModel.onPageSelected(it) },
                                onBlockClicked = { viewModel.onBlockClicked(it) },
                                onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { viewModel.exitGroupEditMode() },
                                onPromptChanged = { viewModel.onUserPromptChanged(it) },
                                onSwitchEditingLanguage = { viewModel.switchEditingLanguage(it) },
                                onGenerateRequested = { viewModel.onRequestGeneration(globalState.effectiveApiKey) },
                                onImageSelected = { uri, cropRect ->
                                    if (cropRect != null) state.selectedBlockId?.let { id -> viewModel.performCropAndApply(id, uri, cropRect) }
                                    else viewModel.onImageSelected(uri)
                                },
                                onDeleteImages = { viewModel.deleteImages(it) },
                                onClearHistoricalCandidates = { viewModel.clearCandidates() },
                                onClearSelectedImage = { viewModel.clearSelectedImage(it) },
                                onLoadHistoricalImages = { viewModel.loadBlockHistoricalImages(it) },
                                onMoveBlock = { d, t, p -> viewModel.moveBlock(d, t, p) },
                                onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { old, new -> viewModel.renameBlock(old, new) },        
                                onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) },
                                onToggleTransparent = { viewModel.setGenerateTransparent(it) },
                                onTogglePrioritizeCloud = { viewModel.setPrioritizeCloudRemoval(it) },
                                onCancelGeneration = { viewModel.cancelGeneration() },
                                onToggleGenerationLog = { viewModel.toggleGenerationLogVisibility() },
                                onCloseAITaskDialog = { viewModel.closeAITaskDialog() },
                                isVisualMode = state.isVisualMode,
                                onToggleVisualMode = { viewModel.toggleVisualMode() },
                                onToggleVisibility = { id, vis -> viewModel.toggleBlockVisibility(id, vis) },
                                onToggleAllVisibility = { vis -> viewModel.toggleAllBlocksVisibility(vis) }
                            )
                        }
                        AppScreen.TEMPLATE_GENERATOR -> {
                            TemplateGeneratorScreen(
                                onNavigateBack = { viewModel.navigateTo(AppScreen.HOME) },
                                onTemplateSaved = { name, projectState -> viewModel.loadProject(name, projectState); viewModel.navigateTo(AppScreen.TEMPLATE_EDITOR) },
                                templateRepo = templateRepo,
                                apiKey = globalState.effectiveApiKey,
                                maxRetries = globalState.maxRetries
                            )
                        }
                    }
                }
            }
        }
    }
}
