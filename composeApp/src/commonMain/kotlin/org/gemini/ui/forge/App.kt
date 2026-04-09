package org.gemini.ui.forge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gemini.ui.forge.ui.AppTopBar
import org.gemini.ui.forge.ui.EditorScreen
import org.gemini.ui.forge.ui.HomeScreen
import org.gemini.ui.forge.ui.UIModule
import org.gemini.ui.forge.ui.AppTheme
import org.gemini.ui.forge.ui.TemplateGeneratorScreen
import org.gemini.ui.forge.viewmodel.AppScreen
import org.gemini.ui.forge.viewmodel.EditorViewModel
import org.gemini.ui.forge.service.TemplateRepository
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*

import androidx.compose.material3.Typography
import kotlinx.coroutines.launch

@Composable
fun App(typography: Typography? = null) {
    var languageKey by remember { mutableStateOf(0) }
    val templateRepo = remember { TemplateRepository() }
    
    var templatesList by remember { mutableStateOf(emptyList<Pair<String, org.gemini.ui.forge.domain.ProjectState>>()) }

    key(languageKey) {
        val viewModel: EditorViewModel = viewModel { EditorViewModel(templateRepo = templateRepo) }
        val state by viewModel.state.collectAsState()
        val globalState = state.globalState
        
        LaunchedEffect(globalState.currentScreen) {
            if (globalState.currentScreen == AppScreen.HOME) {
                templatesList = templateRepo.getTemplates()
            }
        }

        val availableModules = buildList {
            templatesList.forEach { (name, projectState) ->
                // 使用 TemplateRepository 的机制查找物理路径并传递给 module 以启用删除按钮显示
                val path = "TODO: Fix sync path" // Fix this later if getFilePath needs to be async or remove it
                add(UIModule(id = name, nameStr = name, projectState = projectState, absolutePath = path))
            }
        }

        var showCloudAssetDialog by remember { mutableStateOf(false) }

        AppTheme(themeMode = globalState.themeMode, typography = typography ?: androidx.compose.material3.MaterialTheme.typography) {
            val coroutineScope = rememberCoroutineScope()

            if (showCloudAssetDialog) {
                org.gemini.ui.forge.ui.CloudAssetDialog(
                    cloudAssetManager = viewModel.cloudAssetManager,
                    onDismiss = { showCloudAssetDialog = false }
                )
            }

            Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppTopBar(
                    currentScreen = globalState.currentScreen,
                    onNavigateHome = { viewModel.navigateTo(AppScreen.HOME) },
                    onLanguageChangeRequested = { languageCode ->
                        setAppLanguage(languageCode)
                        viewModel.setLanguage(languageCode)
                        languageKey++
                    },
                    currentTheme = globalState.themeMode,
                    currentLanguage = globalState.languageCode,
                    onThemeChangeRequested = { viewModel.setThemeMode(it) },
                    onGenerateTemplateClicked = { viewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                    onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                    currentApiKey = globalState.apiKey,
                    onApiKeyChanged = { viewModel.saveApiKey(it) },
                    currentStorageDir = globalState.templateStorageDir,
                    onStorageDirChanged = { viewModel.updateStorageDir(it) },
                    currentMaxRetries = globalState.maxRetries,
                    onMaxRetriesSaved = { viewModel.setMaxRetries(it) },
                    currentPromptLang = globalState.promptLangPref,
                    onPromptLangChanged = { viewModel.setPromptLanguagePref(it) }
                )
            }
            ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (globalState.currentScreen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            modules = availableModules,
                            onEditLayout = { moduleId ->
                                val module = availableModules.find { it.id == moduleId }
                                if (module?.projectState != null) {
                                    viewModel.loadProject(module.nameStr ?: moduleId, module.projectState)
                                }
                                viewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                            },
                            onGenerateUI = { moduleId ->
                                val module = availableModules.find { it.id == moduleId }
                                if (module?.projectState != null) {
                                    viewModel.loadProject(module.nameStr ?: moduleId, module.projectState)
                                }
                                viewModel.navigateTo(AppScreen.EDITOR)
                            },
                            onDeleteModule = { moduleId ->
                                coroutineScope.launch {
                                    templateRepo.deleteTemplate(moduleId)
                                    templatesList = templateRepo.getTemplates()
                                }
                            }
                        )
                    }
                    AppScreen.TEMPLATE_EDITOR -> {
                        org.gemini.ui.forge.ui.TemplateEditorScreen(
                            state = state,
                            onPageSelected = { viewModel.onPageSelected(it) },
                            onBlockClicked = { viewModel.onBlockClicked(it) },
                            onBlockBoundsChanged = { id, l, t, r, b -> viewModel.updateBlockBounds(id, l, t, r, b) },
                            onBlockTypeChanged = { id, type -> viewModel.updateBlockType(id, type) },
                            onPromptChanged = { id, prompt -> 
                                viewModel.onBlockClicked(id)
                                viewModel.onUserPromptChanged(prompt) 
                            },
                            onOptimizePrompt = { id, onComplete ->
                                viewModel.optimizePrompt(id, globalState.effectiveApiKey, onComplete)
                            },
                            onRefineArea = { id, bounds, instruction, onLog, onChunk, onComplete ->
                                viewModel.onRefineArea(id, bounds, instruction, onLog, onChunk, onComplete)
                            },
                            onRefineCustomArea = { bounds, instruction, onLog, onChunk, onComplete ->
                                viewModel.onRefineCustomArea(bounds, instruction, onLog, onChunk, onComplete)
                            },
                            onSwitchEditingLanguage = { viewModel.switchEditingLanguage(it) },
                            onAddBlock = { type -> viewModel.addBlock(type) },
                            onDeleteBlock = { id -> viewModel.deleteBlock(id) },
                            onSaveTemplate = {
                                coroutineScope.launch {
                                    templateRepo.saveTemplate(state.projectName, state.project)
                                }
                            }
                        )
                    }
                    AppScreen.EDITOR -> {
                        EditorScreen(
                            state = state,
                            onPageSelected = { viewModel.onPageSelected(it) },
                            onBlockClicked = { viewModel.onBlockClicked(it) },
                            onPromptChanged = { viewModel.onUserPromptChanged(it) },
                            onSwitchEditingLanguage = { viewModel.switchEditingLanguage(it) },
                            onGenerateRequested = { viewModel.onRequestGeneration(globalState.effectiveApiKey) },
                            onImageSelected = { viewModel.onImageSelected(it) }
                        )
                    }
                    AppScreen.TEMPLATE_GENERATOR -> {
                        TemplateGeneratorScreen(
                            onNavigateBack = { viewModel.navigateTo(AppScreen.HOME) },
                            onTemplateSaved = { name, projectState ->
                                viewModel.loadProject(name, projectState)
                                viewModel.navigateTo(AppScreen.TEMPLATE_EDITOR)
                            },
                            templateRepo = templateRepo,
                            apiKey = globalState.effectiveApiKey,
                            maxRetries = globalState.maxRetries
                        )
                    }
                }
            }
            }        }
    }
}
