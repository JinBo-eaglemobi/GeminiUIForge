package org.gemini.ui.forge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.focusable
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.gemini.ui.forge.service.*
import kotlin.time.Duration.Companion.milliseconds
import org.gemini.ui.forge.ui.dialog.AppSettingsDialog
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
        val appViewModel: AppViewModel = viewModel {
            AppViewModel(
                templateRepo = templateRepo,
                cloudAssetManager = CloudAssetManager(ConfigManager()),
                aiService = AIGenerationService(CloudAssetManager(ConfigManager()))
            )
        }
        val settingsViewModel: AppSettingsViewModel = viewModel { AppSettingsViewModel(templateRepo = templateRepo) }
        val updateViewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel(templateRepo = templateRepo) }
        val envViewModel: AppEnvViewModel = viewModel { AppEnvViewModel() }

        val appState by appViewModel.state.collectAsState()
        val updateStatus by updateViewModel.status.collectAsState()
        val envStatus by envViewModel.status.collectAsState()

        val globalState = appState.globalState

        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
            }
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
                        settingsViewModel.saveLanguage(it); appViewModel.setLanguage(it); languageKey++
                    },
                    onThemeSelected = { appViewModel.setThemeMode(it) },
                    onApiKeySaved = {
                        settingsViewModel.saveApiKey(it); appViewModel.updateApiKey(it)
                    },
                    onStorageDirSaved = { path ->
                        coroutineScope.launch {
                            if (settingsViewModel.updateStorageDir(path)) appViewModel.updateStorageDirState(
                                path
                            )
                        }
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
                Box(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().focusRequester(focusRequester).focusable()
                ) {
                    when (globalState.currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                modules = availableModules,
                                onEditLayout = { moduleId ->
                                    availableModules.find { it.id == moduleId }
                                        ?.let { appViewModel.loadProject(it.nameStr ?: moduleId, it.projectState!!) }
                                    appViewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                                },
                                onGenerateUI = { moduleId ->
                                    availableModules.find { it.id == moduleId }
                                        ?.let { appViewModel.loadProject(it.nameStr ?: moduleId, it.projectState!!) }
                                    appViewModel.navigateTo(AppScreen.TEMPLATE_ASSET_GEN)
                                },
                                onDeleteModule = { moduleId ->
                                    coroutineScope.launch {
                                        templateRepo.deleteTemplate(
                                            moduleId
                                        ); templatesList = templateRepo.getTemplates()
                                    }
                                }
                            )
                        }

                        AppScreen.TEMPLATE_EDITOR -> {
                            TemplateEditorScreen(
                                initialProject = appState.project,
                                initialProjectName = appState.projectName,
                                templateRepo = templateRepo,
                                cloudAssetManager = appViewModel.cloudAssetManager,
                                aiService = AIGenerationService(appViewModel.cloudAssetManager),
                                effectiveApiKey = globalState.effectiveApiKey,
                                currentEditingPromptLang = globalState.promptLangPref,
                                onSwitchEditingLanguage = { appViewModel.switchEditingLanguage(it) },
                                onProjectUpdated = { appViewModel.updateProject(it) },
                                onSaveTemplate = {
                                    coroutineScope.launch {
                                        templateRepo.saveTemplate(
                                            appState.projectName,
                                            appState.project
                                        )
                                    }
                                }
                            )
                        }

                        AppScreen.TEMPLATE_ASSET_GEN -> {
                            TemplateAssetGenScreen(
                                initialProject = appState.project,
                                initialProjectName = appState.projectName,
                                templateRepo = templateRepo,
                                cloudAssetManager = appViewModel.cloudAssetManager,
                                aiService = AIGenerationService(appViewModel.cloudAssetManager),
                                effectiveApiKey = globalState.effectiveApiKey,
                                currentEditingPromptLang = globalState.promptLangPref,
                                onSwitchEditingLanguage = { appViewModel.switchEditingLanguage(it) },
                                onProjectUpdated = { appViewModel.updateProject(it) }
                            )
                        }

                        AppScreen.TEMPLATE_GENERATOR -> {
                            TemplateGeneratorScreen(
                                onNavigateBack = { appViewModel.navigateTo(AppScreen.HOME) },
                                onTemplateSaved = { name, ps ->
                                    appViewModel.loadProject(
                                        name,
                                        ps
                                    ); appViewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                                },
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
