package org.gemini.ui.forge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import geminiuiforge.composeapp.generated.resources.*

import androidx.compose.foundation.focusable
import androidx.compose.material3.Typography
import kotlinx.coroutines.launch

import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.gemini.ui.forge.model.ui.ProjectState
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.app.ShortcutAction
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.state.EditorViewModel
import org.gemini.ui.forge.ui.feature.home.HomeScreen
import org.gemini.ui.forge.ui.feature.assetgen.TemplateAssetGenScreen
import org.gemini.ui.forge.ui.feature.editor.TemplateEditorScreen
import org.gemini.ui.forge.ui.feature.analysis.TemplateGeneratorScreen
import org.gemini.ui.forge.ui.component.AppTopBar
import org.gemini.ui.forge.ui.theme.AppTheme
import org.gemini.ui.forge.utils.*
import org.gemini.ui.forge.service.*

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
        val viewModel: EditorViewModel = viewModel { EditorViewModel(templateRepo = templateRepo) }
        val state by viewModel.state.collectAsState()
        val globalState = state.globalState

        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus error
            }
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
                // 使用 TemplateRepository 的机制查找物理路径并传递给 module 以启用删除按钮显示
                val path = "TODO: Fix sync path" // Fix this later if getFilePath needs to be async or remove it
                add(UIModule(id = name, nameStr = name, projectState = projectState, absolutePath = path))
            }
        }

        var showCloudAssetDialog by remember { mutableStateOf(false) }

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

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AppTopBar(
                        currentScreen = globalState.currentScreen,
                        onNavigateHome = { viewModel.navigateTo(AppScreen.HOME) },
                        onLanguageChangeRequested = { languageCode ->
                            val effectiveLang = if (languageCode == "auto") {
                                val sysLang =
                                    originalSystemLanguage ?: androidx.compose.ui.text.intl.Locale.current.language
                                if (sysLang.lowercase().startsWith("zh")) "zh" else "en"
                            } else languageCode
                            setAppLanguage(effectiveLang)
                            viewModel.setLanguage(languageCode)
                            languageKey++
                        },
                        currentTheme = globalState.themeMode,
                        currentLanguage = globalState.languageCode,
                        onThemeChangeRequested = { viewModel.setThemeMode(it) },
                        onGenerateTemplateClicked = { viewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                        onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                        onSaveClicked = {
                            if (globalState.currentScreen == AppScreen.TEMPLATE_EDITOR || globalState.currentScreen == AppScreen.TEMPLATE_ASSET_GEN) {
                                coroutineScope.launch {
                                    templateRepo.saveTemplate(state.projectName, state.project)
                                }
                            }
                        },
                        currentApiKey = globalState.apiKey,
                        onApiKeyChanged = { viewModel.saveApiKey(it) },
                        currentStorageDir = globalState.templateStorageDir,
                        onStorageDirChanged = { viewModel.updateStorageDir(it) },
                        currentMaxRetries = globalState.maxRetries,
                        onMaxRetriesSaved = { viewModel.setMaxRetries(it) },
                        currentPromptLang = globalState.promptLangPref,
                        onPromptLangChanged = { viewModel.setPromptLanguagePref(it) },
                        shortcuts = globalState.shortcuts,
                        onShortcutSaved = { action, key -> viewModel.saveShortcut(action, key) }
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
                                    matches(ShortcutAction.UNDO) -> {
                                        viewModel.undo(); true
                                    }

                                    matches(ShortcutAction.REDO) || (isCtrl && isShift && keyCode == Key.Z) -> {
                                        viewModel.redo(); true
                                    }

                                    matches(ShortcutAction.SAVE) -> {
                                        if (globalState.currentScreen == AppScreen.TEMPLATE_EDITOR || globalState.currentScreen == AppScreen.TEMPLATE_ASSET_GEN) {
                                            coroutineScope.launch {
                                                templateRepo.saveTemplate(
                                                    state.projectName,
                                                    state.project
                                                )
                                            }
                                        }; true
                                    }

                                    matches(ShortcutAction.DELETE) -> {
                                        state.selectedBlockId?.let { viewModel.deleteBlock(it) }; true
                                    }

                                    else -> false
                                }
                            } else false
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                ) {
                    // 内容分发
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
                                    viewModel.navigateTo(AppScreen.TEMPLATE_ASSET_GEN)
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
                            TemplateEditorScreen(
                                state = state,
                                onPageSelected = { viewModel.onPageSelected(it) },
                                onBlockClicked = { viewModel.onBlockClicked(it) },
                                onBlockBoundsChanged = { id, l, t, r, b ->
                                    viewModel.updateBlockBounds(
                                        id,
                                        l,
                                        t,
                                        r,
                                        b
                                    )
                                },
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
                                onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { viewModel.exitGroupEditMode() },
                                onAddBlock = { type -> viewModel.addBlock(type) },
                                onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) },
                                onDeleteBlock = { id -> viewModel.deleteBlock(id) },
                                onMoveBlock = { draggedId, targetId, dropPos ->
                                    viewModel.moveBlock(
                                        draggedId,
                                        targetId,
                                        dropPos
                                    )
                                },
                                onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { oldId, newId -> viewModel.renameBlock(oldId, newId) },
                                onSaveTemplate = {
                                    coroutineScope.launch {
                                        templateRepo.saveTemplate(state.projectName, state.project)
                                    }
                                }
                            )
                        }

                        AppScreen.TEMPLATE_ASSET_GEN -> {
                            TemplateAssetGenScreen(
                                state = state,
                                onPageSelected = { viewModel.onPageSelected(it) },
                                onBlockClicked = { viewModel.onBlockClicked(it) },
                                onBlockDoubleClicked = { viewModel.onBlockDoubleClicked(it) },
                                onExitGroupEdit = { viewModel.exitGroupEditMode() },
                                onPromptChanged = { viewModel.onUserPromptChanged(it) },
                                onSwitchEditingLanguage = { viewModel.switchEditingLanguage(it) },
                                onGenerateRequested = { viewModel.onRequestGeneration(globalState.effectiveApiKey) },
                                onImageSelected = { viewModel.onImageSelected(it) },
                                onDeleteImages = { viewModel.deleteImages(it) }, // 新增
                                onClearHistoricalCandidates = { viewModel.clearCandidates() },                                onClearSelectedImage = { viewModel.clearSelectedImage(it) },
                                onLoadHistoricalImages = { viewModel.loadBlockHistoricalImages(it) },
                                onMoveBlock = { draggedId, targetId, dropPos -> viewModel.moveBlock(draggedId, targetId, dropPos) },
                                onBlockDragged = { id, dx, dy -> viewModel.moveBlockBy(id, dx, dy) },
                                onRenameBlock = { oldId, newId -> viewModel.renameBlock(oldId, newId) },
                                onAddCustomBlock = { id, type, w, h -> viewModel.addCustomBlock(id, type, w, h) }
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
            }
        }
    }
}
