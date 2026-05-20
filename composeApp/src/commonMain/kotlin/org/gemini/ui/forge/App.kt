package org.gemini.ui.forge
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.focusable
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.delay
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.model.app.*
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.ui.feature.HomeScreen
import org.gemini.ui.forge.ui.component.AppTopBar
import org.gemini.ui.forge.ui.theme.AppTheme
import org.gemini.ui.forge.service.*
import kotlin.time.Duration.Companion.milliseconds
import org.gemini.ui.forge.ui.dialog.AppSettingsDialog
import org.gemini.ui.forge.ui.dialog.LogViewerDialog
import org.gemini.ui.forge.ui.dialog.HelpDialog
import org.gemini.ui.forge.manager.*
import org.gemini.ui.forge.utils.*
import org.gemini.ui.forge.viewmodel.AppEnvViewModel
import org.gemini.ui.forge.viewmodel.AppSettingsViewModel
import org.gemini.ui.forge.viewmodel.AppUpdateViewModel
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.feature.ProjectWorkspaceScreen
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.ui.theme.AppSpacing

private var originalSystemLanguage: String? = null

@Composable
fun App(typography: Typography? = null) {
    if (originalSystemLanguage == null) {
        originalSystemLanguage = androidx.compose.ui.text.intl.Locale.current.language
    }

    var languageKey by remember { mutableStateOf(0) }
    val templateRepo = remember { TemplateRepository() }
    val focusRequester = remember { FocusRequester() }
    val tooltipState = remember { GlobalTooltipState() }

    var templatesList by remember { mutableStateOf(emptyList<Pair<String, ProjectState>>()) }

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalGlobalTooltip provides tooltipState
    ) {
        key(languageKey) {
            val configManager = remember { ConfigManager() }
            // 全局基础 ViewModel
            val appViewModel: AppViewModel = viewModel {
                val cloudAssetManager = CloudAssetManager(configManager)
                AppViewModel(
                    templateRepo = templateRepo,
                    cloudAssetManager = cloudAssetManager,
                    aiService = AIGenerationService(cloudAssetManager, configManager)
                )
            }
            val settingsViewModel: AppSettingsViewModel = viewModel {
                AppSettingsViewModel(
                    templateRepo = templateRepo,
                    configManager = configManager
                )
            }
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

            LaunchedEffect(updateStatus) {
                when (updateStatus) {
                    is UpdateStatus.Available -> {
                        val info = (updateStatus as UpdateStatus.Available).info
                        Toast.show(
                            message = "发现新版本 v${info.version}！",
                            type = org.gemini.ui.forge.ui.component.ToastType.INFO,
                            durationMillis = 10000L, // 显示 10 秒
                            actionLabel = "立即更新"
                        ) {
                            updateViewModel.performUpdate(info)
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        val progress = (updateStatus as UpdateStatus.Downloading).progress
                        if (progress == 0f) {
                            Toast.show(
                                message = "开始后台下载更新...",
                                type = org.gemini.ui.forge.ui.component.ToastType.SUCCESS
                            )
                        }
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Toast.show(
                            message = "下载完成，准备重启安装！",
                            type = org.gemini.ui.forge.ui.component.ToastType.SUCCESS,
                            durationMillis = 5000L
                        )
                    }

                    is UpdateStatus.Error -> {
                        val errorMsg = (updateStatus as UpdateStatus.Error).message
                        Toast.show(
                            message = "更新失败: $errorMsg",
                            type = org.gemini.ui.forge.ui.component.ToastType.ERROR,
                            durationMillis = 8000L
                        )
                    }

                    else -> {}
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
                layoutMode = globalState.layoutMode
            ) {
                val coroutineScope = rememberCoroutineScope()
                val toastData by Toast.toastData.collectAsState()
                var showExitConfirmDialog by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier.fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        try {
                                            focusRequester.requestFocus()
                                        } catch (e: Exception) {
                                            // 忽略焦点请求异常
                                        }
                                    }
                                }
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            AppLogger.d("App", "⌨️ 捕获到按键: ${event.key}, type: ${event.type}")
                            // 全局快捷键处理
                            globalState.shortcuts.forEach { (action, shortcut) ->
                                if (ShortcutUtils.isMatch(event, shortcut)) {
                                    appViewModel.dispatchShortcutEvent(action)
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                ) {
                    if (showExitConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitConfirmDialog = false },
                            title = { Text("提醒") },
                            text = { Text("项目有未保存的修改，是否保存后退出？") },
                            confirmButton = {
                                Button(onClick = {
                                    appViewModel.dispatchSaveEvent()
                                    showExitConfirmDialog = false
                                    appViewModel.navigateTo(AppScreen.HOME)
                                }) {
                                    Text("保存并退出")
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = {
                                        appViewModel.setDirty(false)
                                        showExitConfirmDialog = false
                                        appViewModel.navigateTo(AppScreen.HOME)
                                    }) {
                                        Text("不保存退出")
                                    }
                                    TextButton(onClick = { showExitConfirmDialog = false }) {
                                        Text("取消")
                                    }
                                }
                            }
                        )
                    }

                    if (showCloudAssetDialog) {
                        org.gemini.ui.forge.ui.dialog.CloudAssetDialog(
                            cloudAssetManager = appViewModel.cloudAssetManager,
                            onDismiss = { showCloudAssetDialog = false }
                        )
                    }

                    if (showHelpDialog) {
                        HelpDialog(onDismiss = { showHelpDialog = false })
                    }

                    val pipPackages by envViewModel.pipPackages.collectAsState()
                    val isPipLoading by envViewModel.isPipLoading.collectAsState()
                    val pipLogs by envViewModel.pipLogs.collectAsState()
                    val isPipActionInProgress by envViewModel.isPipActionInProgress.collectAsState()
                    val searchResult by envViewModel.searchResult.collectAsState()
                    val isSearching by envViewModel.isSearching.collectAsState()
                    val topMarketPackages by envViewModel.topMarketPackages.collectAsState()
                    val isMarketLoading by envViewModel.isMarketLoading.collectAsState()
                    val marketPage by envViewModel.marketPage.collectAsState()

                    if (showSettingsDialog) {
                        AppSettingsDialog(
                            currentTheme = globalState.themeMode,
                            currentLayoutMode = globalState.layoutMode,
                            currentLanguage = globalState.languageCode,
                            currentApiKey = globalState.apiKey,
                            currentStorageDir = globalState.templateStorageDir,
                            currentMaxRetries = globalState.maxRetries,
                            currentImageGenCount = globalState.imageGenCount,
                            currentPromptLang = globalState.promptLangPref,
                            shortcuts = globalState.shortcuts,
                            envStatus = envStatus,
                            pipPackages = pipPackages,
                            isPipLoading = isPipLoading,
                            pipLogs = pipLogs,
                            isPipActionInProgress = isPipActionInProgress,
                            searchResult = searchResult,
                            isSearching = isSearching,
                            topMarketPackages = topMarketPackages,
                            isMarketLoading = isMarketLoading,
                            marketPage = marketPage,
                            initialCategory = settingsInitialCategory,
                            updateStatus = updateStatus,
                            configManager = configManager,
                            onDismiss = { showSettingsDialog = false },
                            onLanguageSelected = {
                                settingsViewModel.saveLanguage(it);
                                appViewModel.setLanguage(it); languageKey++
                            },
                            onLayoutModeSelected = {
                                settingsViewModel.saveLayoutMode(it)
                                appViewModel.setLayoutMode(it)
                            },
                            onThemeSelected = { appViewModel.setThemeMode(it) },
                            onApiKeySaved = {
                                settingsViewModel.saveApiKey(it);
                                appViewModel.updateApiKey(it)
                            },
                            onStorageDirSaved = { path ->
                                coroutineScope.launch {
                                    if (settingsViewModel.updateStorageDir(path)) appViewModel.updateStorageDirState(
                                        path
                                    )
                                }
                            },
                            onMaxRetriesSaved = {
                                settingsViewModel.saveMaxRetries(it);
                                appViewModel.updateMaxRetriesState(it)
                            },
                            onImageGenCountSaved = {
                                settingsViewModel.saveImageGenCount(it);
                                appViewModel.updateImageGenCountState(it)
                            },
                            onPromptLangSelected = {
                                settingsViewModel.savePromptLanguagePref(it);
                                appViewModel.setPromptLanguagePref(it)
                            },
                            onShortcutSaved = { action, key ->
                                settingsViewModel.saveShortcut(action, key);
                                appViewModel.updateShortcutState(action, key)
                            },
                            onCheckEnv = { envViewModel.checkEnvironment() },
                            onInstallEnvItem = { envViewModel.installEnvironmentItem(it) },
                            onUninstallEnvItem = { envViewModel.uninstallEnvironmentItem(it) },
                            onBatchInstallPip = { envViewModel.batchInstallPipPackages(it) },
                            onBatchUninstallPip = { envViewModel.batchUninstallPipPackages(it) },
                            onOpenPackageUrl = { envViewModel.openPackageHome(it) },
                            onSearchPipPackage = { envViewModel.searchPipPackage(it) },
                            onClearSearchResult = { envViewModel.clearSearchResult() },
                            onLoadMarketPage = { envViewModel.loadMarketPage(it) },
                            onCheckUpdate = { updateViewModel.checkForUpdates() },
                            onStartUpdate = { updateViewModel.performUpdate(it) }
                        )
                    }


                    val statusMessage by AppLogger.statusMessage.collectAsState()
                    val showLogViewer by AppLogger.showLogViewer.collectAsState()
                    val memoryLogs by AppLogger.memoryLogs.collectAsState()

                    if (showLogViewer) {
                        LogViewerDialog(
                            onDismiss = { AppLogger.toggleLogViewer(false) }
                        )
                    }

                    Scaffold(

                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AppTopBar(
                                currentScreen = globalState.currentScreen,
                                onNavigateHome = {
                                    if (appState.isDirty) {
                                        showExitConfirmDialog = true
                                    } else {
                                        appViewModel.navigateTo(AppScreen.HOME)
                                    }
                                },
                                onGenerateTemplateClicked = { appViewModel.navigateTo(AppScreen.TEMPLATE_GENERATOR) },
                                onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                                onSaveClicked = {
                                    appViewModel.dispatchSaveEvent()
                                },
                                onSettingsClicked = {
                                    settingsInitialCategory = SettingCategory.GENERAL;
                                    showSettingsDialog = true
                                },
                                onHelpClicked = { showHelpDialog = true }
                            )
                        },
                        bottomBar = {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { AppLogger.toggleLogViewer(true) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "查看日志", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier.padding(innerPadding).fillMaxSize()
                        ) {
                            when (globalState.currentScreen) {
                                AppScreen.HOME -> {
                                    HomeScreen(
                                        modules = availableModules,
                                        onOpenWorkspace = { moduleId ->
                                            availableModules.find { it.id == moduleId }?.let {
                                                appViewModel.loadProject(
                                                    it.nameStr ?: moduleId,
                                                    it.projectState!!
                                                )
                                            }
                                            appViewModel.navigateTo(AppScreen.PROJECT_WORKSPACE)
                                        },
                                        onDeleteModule = { moduleId ->
                                            coroutineScope.launch {
                                                templateRepo.deleteTemplate(moduleId); templatesList =
                                                templateRepo.getTemplates()
                                            }
                                        }
                                    )
                                }

                                AppScreen.PROJECT_WORKSPACE -> {
                                    ProjectWorkspaceScreen(
                                        initialProject = appState.project,
                                        initialProjectName = appState.projectName,
                                        templateRepo = templateRepo,
                                        cloudAssetManager = appViewModel.cloudAssetManager,
                                        configManager = configManager,
                                        effectiveApiKey = globalState.effectiveApiKey,
                                        initialPromptLang = globalState.promptLangPref,
                                        saveEvent = appViewModel.saveEvent,
                                        shortcutEvent = appViewModel.shortcutEvent,
                                        onSaveRequest = { name, project ->
                                            appViewModel.saveProject(name, project)
                                        },
                                        onDirtyChanged = { appViewModel.setDirty(it) }
                                    )
                                }

                                AppScreen.TEMPLATE_GENERATOR -> {
                                    TemplateGeneratorScreen(
                                        onTemplateSaved = { name, ps ->
                                            appViewModel.loadProject(name, ps);
                                            appViewModel.navigateTo(
                                                AppScreen.PROJECT_WORKSPACE
                                            )
                                        },
                                        globalState = globalState,
                                        cloudAssetManager = appViewModel.cloudAssetManager,
                                        configManager = configManager,
                                        templateRepo = templateRepo,
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    // 在所有 UI 的最上层挂载全局 Toast 容器
                    org.gemini.ui.forge.ui.component.AppToastContainer(
                        toastData = toastData,
                        onDismiss = { Toast.hide() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // 挂载全局提示宿主 (Tooltip)
                    GlobalTooltipHost()
                }
            }
        }
    }
}
