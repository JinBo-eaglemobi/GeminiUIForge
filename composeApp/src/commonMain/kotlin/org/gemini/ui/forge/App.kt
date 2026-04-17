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
import org.gemini.ui.forge.state.*
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
        // 全局基础 ViewModel
        val appViewModel: AppViewModel = viewModel { AppViewModel(templateRepo = templateRepo, cloudAssetManager = CloudAssetManager(ConfigManager()), aiService = AIGenerationService(CloudAssetManager(ConfigManager()))) }
        val settingsViewModel: AppSettingsViewModel = viewModel { AppSettingsViewModel(templateRepo = templateRepo) }
        val updateViewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel(templateRepo = templateRepo) }
        val envViewModel: AppEnvViewModel = viewModel { AppEnvViewModel() }
        
        val appState by appViewModel.state.collectAsState()
        val updateStatus by updateViewModel.status.collectAsState()
        val envStatus by envViewModel.status.collectAsState()
        
        val globalState = appState.globalState

        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            try { focusRequester.requestFocus() } catch (e: Exception) { }
            appViewModel.syncInitialSettings(settingsViewModel.getConfigManager())
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

        AppTheme(
            themeMode = globalState.themeMode,
            typography = typography ?: androidx.compose.material3.MaterialTheme.typography
        ) {
            val coroutineScope = rememberCoroutineScope()

            if (showCloudAssetDialog) {
                org.gemini.ui.forge.ui.dialog.CloudAssetDialog(
                    cloudAssetManager = appViewModel.cloudAssetManager,
                    onDismiss = { showCloudAssetDialog = false }
                )
            }

            if (showHelpDialog) { HelpDialog(onDismiss = { showHelpDialog = false }) }

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
                        settingsViewModel.saveLanguage(it); appViewModel.setLanguage(it); languageKey++
                    },
                    onThemeSelected = { appViewModel.setThemeMode(it) },
                    onApiKeySaved = { 
                        settingsViewModel.saveApiKey(it); appViewModel.updateApiKey(it)
                    },
                    onStorageDirSaved = { path ->
                        coroutineScope.launch { if (settingsViewModel.updateStorageDir(path)) appViewModel.updateStorageDirState(path) }
                    },
                    onMaxRetriesSaved = { 
                        settingsViewModel.saveMaxRetries(it); appViewModel.updateMaxRetriesState(it)
                    },
                    onPromptLangSelected = { 
                        settingsViewModel.savePromptLanguagePref(it); appViewModel.setPromptLanguagePref(it)
                    },
                    onShortcutSaved = { action, key -> 
                        settingsViewModel.saveShortcut(action, key); appViewModel.updateShortcutState(action, key)
                    },
                    onCheckEnv = { envViewModel.checkEnvironment() },
                    onInstallEnvItem = { envViewModel.installEnvironmentItem(it) },
                    onCheckUpdate = { updateViewModel.checkForUpdates() },
                    onStartUpdate = { updateViewModel.performUpdate(it) }
                )
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AppTopBar(
                        currentScreen = globalState.currentScreen,
                        onNavigateHome = { appViewModel.navigateTo(AppScreen.HOME) },
                        onGenerateTemplateClicked = { appViewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                        onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                        onSaveClicked = {
                            coroutineScope.launch { templateRepo.saveTemplate(appState.projectName, appState.project) }
                        },
                        onSettingsClicked = {
                            settingsInitialCategory = SettingCategory.GENERAL; showSettingsDialog = true
                        },
                        onHelpClicked = { showHelpDialog = true }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize().focusRequester(focusRequester).focusable()) {
                    when (globalState.currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                modules = availableModules,
                                onEditLayout = { moduleId ->
                                    availableModules.find { it.id == moduleId }?.let { appViewModel.loadProject(it.nameStr ?: moduleId, it.projectState!!) }
                                    appViewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                                },
                                onGenerateUI = { moduleId ->
                                    availableModules.find { it.id == moduleId }?.let { appViewModel.loadProject(it.nameStr ?: moduleId, it.projectState!!) }
                                    appViewModel.navigateTo(AppScreen.TEMPLATE_ASSET_GEN)
                                },
                                onDeleteModule = { moduleId -> coroutineScope.launch { templateRepo.deleteTemplate(moduleId); templatesList = templateRepo.getTemplates() } }
                            )
                        }

                        AppScreen.TEMPLATE_EDITOR -> {
                            val editorViewModel: TemplateEditorViewModel = viewModel(key = appState.projectName) { 
                                TemplateEditorViewModel(appState.project, appState.projectName, templateRepo, appViewModel.cloudAssetManager, AIGenerationService(appViewModel.cloudAssetManager))
                            }
                            val editorState by editorViewModel.state.collectAsState()
                            LaunchedEffect(editorState.project) { appViewModel.updateProject(editorState.project) }

                            TemplateEditorScreen(
                                state = editorState,
                                currentEditingPromptLang = globalState.promptLangPref,
                                onPageSelected = { editorViewModel.onPageSelected(it) },
                                onBlockClicked = { editorViewModel.onBlockClicked(it) },
                                onBlockBoundsChanged = { id, l, t, r, b -> editorViewModel.updateBlockBounds(id, l, t, r, b) },
                                onBlockTypeChanged = { _, _ -> }, 
                                onPromptChanged = { id, prompt -> editorViewModel.onUserPromptChanged(id, prompt, globalState.promptLangPref) },
                                onOptimizePrompt = { id, _ -> editorViewModel.optimizePrompt(id, globalState.effectiveApiKey, globalState.promptLangPref) },
                                onRefineArea = { id, bounds, instr, _, _, complete -> editorViewModel.onRefineArea(id, bounds, instr, globalState.effectiveApiKey, complete) },
                                onRefineCustomArea = { _, _, _, _, complete -> complete(false) }, 
                                onSwitchEditingLanguage = { appViewModel.switchEditingLanguage(it) },
                                onBlockDoubleClicked = { editorViewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { editorViewModel.exitGroupEditMode() },
                                onAddBlock = { editorViewModel.addBlock(it) },
                                onAddCustomBlock = { _, _, _, _ -> },
                                onDeleteBlock = { editorViewModel.deleteBlock(it) },
                                onMoveBlock = { d, t, p -> editorViewModel.moveBlock(d, t, p) },
                                onBlockDragged = { id, dx, dy -> editorViewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { old, new -> editorViewModel.renameBlock(old, new) },        
                                onCancelAITask = { editorViewModel.cancelAITask() },
                                onCloseAITaskDialog = { editorViewModel.closeAITaskDialog() },
                                onSaveTemplate = { coroutineScope.launch { templateRepo.saveTemplate(appState.projectName, editorState.project) } }
                            )
                        }

                        AppScreen.TEMPLATE_ASSET_GEN -> {
                            val genViewModel: TemplateAssetGenViewModel = viewModel(key = appState.projectName) {
                                TemplateAssetGenViewModel(appState.project, appState.projectName, templateRepo, appViewModel.cloudAssetManager, AIGenerationService(appViewModel.cloudAssetManager))
                            }
                            val genState by genViewModel.state.collectAsState()
                            LaunchedEffect(genState.project) { appViewModel.updateProject(genState.project) }

                            TemplateAssetGenScreen(
                                state = genState,
                                currentEditingPromptLang = globalState.promptLangPref,
                                onPageSelected = { if (!genState.isGenerating) genViewModel.onPageSelected(it) },
                                onBlockClicked = { genViewModel.onBlockClicked(it) },
                                onBlockDoubleClicked = { genViewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { genViewModel.exitGroupEditMode() },
                                onPromptChanged = { /* 局部逻辑实现 */ },
                                onSwitchEditingLanguage = { appViewModel.switchEditingLanguage(it) },
                                onGenerateRequested = { genViewModel.onRequestGeneration(globalState.effectiveApiKey, globalState.promptLangPref) },
                                onImageSelected = { uri, _ -> genViewModel.onImageSelected(uri) },
                                onDeleteImages = { genViewModel.deleteImages(it) },
                                onClearHistoricalCandidates = { genViewModel.clearCandidates() },
                                onClearSelectedImage = { genViewModel.clearSelectedImage(it) },
                                onLoadHistoricalImages = { genViewModel.loadHistoricalImages(it) },
                                onMoveBlock = { d, t, p -> genViewModel.moveBlock(d, t, p) },
                                onBlockDragged = { id, dx, dy -> genViewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { old, new -> genViewModel.renameBlock(old, new) },        
                                onAddCustomBlock = { id, type, w, h -> genViewModel.addCustomBlock(id, type, w, h) },
                                onToggleTransparent = { genViewModel.setGenerateTransparent(it) },
                                onTogglePrioritizeCloud = { genViewModel.setPrioritizeCloudRemoval(it) },
                                onCancelGeneration = { genViewModel.cancelGeneration() },
                                onToggleGenerationLog = { genViewModel.toggleGenerationLogVisibility() },
                                onCloseAITaskDialog = { genViewModel.closeAITaskDialog() },
                                onToggleVisualMode = { genViewModel.toggleVisualMode() },
                                onToggleVisibility = { id, vis -> genViewModel.toggleBlockVisibility(id, vis) },
                                onToggleAllVisibility = { genViewModel.toggleAllBlocksVisibility(it) }
                            )
                        }
                        AppScreen.TEMPLATE_GENERATOR -> {
                            TemplateGeneratorScreen(
                                onNavigateBack = { appViewModel.navigateTo(AppScreen.HOME) },
                                onTemplateSaved = { name, ps -> appViewModel.loadProject(name, ps); appViewModel.navigateTo(AppScreen.TEMPLATE_EDITOR) },
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
