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
                val path = templateRepo.fileStorage.getFilePath("$name.json")
                add(UIModule(id = name, nameStr = name, projectState = projectState, absolutePath = path))
            }
        }

        AppTheme(themeMode = globalState.themeMode, typography = typography ?: androidx.compose.material3.MaterialTheme.typography) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AppTopBar(
                        currentScreen = globalState.currentScreen,
                        onNavigateHome = { viewModel.navigateTo(AppScreen.HOME) },
                        onLanguageChangeRequested = { languageCode ->
                            setAppLanguage(languageCode)
                            languageKey++
                        },
                        currentTheme = globalState.themeMode,
                        onThemeChangeRequested = { viewModel.setThemeMode(it) },
                        onGenerateTemplateClicked = { viewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                        currentApiKey = globalState.apiKey,
                        onApiKeyChanged = { viewModel.saveApiKey(it) }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    when (globalState.currentScreen) {
                        AppScreen.HOME -> {
                            HomeScreen(
                                modules = availableModules,
                                onModuleSelected = { moduleId ->
                                    val module = availableModules.find { it.id == moduleId }
                                    if (module?.projectState != null) {
                                        viewModel.loadProject(module.id, module.projectState)
                                    }
                                    viewModel.navigateTo(AppScreen.EDITOR)
                                },
                                onDeleteModule = { moduleId ->
                                    val module = availableModules.find { it.id == moduleId }
                                    if (module != null) {
                                        templateRepo.deleteTemplate(module.id)
                                        templatesList = templateRepo.getTemplates()
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
                                onGenerateRequested = { viewModel.onRequestGeneration(globalState.apiKey) },
                                onImageSelected = { viewModel.onImageSelected(it) }
                            )
                        }
                        AppScreen.TEMPLATE_GENERATOR -> {
                            TemplateGeneratorScreen(
                                onNavigateBack = { viewModel.navigateTo(AppScreen.HOME) },
                                onTemplateSaved = { name, projectState ->
                                    viewModel.loadProject(name, projectState)
                                    viewModel.navigateTo(AppScreen.EDITOR)
                                },
                                templateRepo = templateRepo,
                                apiKey = globalState.apiKey
                            )
                        }
                    }
                }
            }
        }
    }
}
