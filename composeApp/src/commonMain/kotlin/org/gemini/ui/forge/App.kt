package org.gemini.ui.forge


import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.manager.CloudAssetManager
import org.gemini.ui.forge.manager.ConfigManager
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.app.SettingCategory
import org.gemini.ui.forge.model.app.UpdateStatus
import org.gemini.ui.forge.service.AIGenerationService
import org.gemini.ui.forge.service.CompilerService
import org.gemini.ui.forge.ui.component.*
import org.gemini.ui.forge.ui.dialog.*
import org.gemini.ui.forge.ui.feature.HomeScreen
import org.gemini.ui.forge.ui.feature.ProjectWorkspaceScreen
import org.gemini.ui.forge.ui.feature.TemplateGeneratorScreen
import org.gemini.ui.forge.ui.theme.AppSpacing
import org.gemini.ui.forge.ui.theme.AppTheme
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.ShortcutUtils
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.viewmodel.AppEnvViewModel
import org.gemini.ui.forge.viewmodel.AppSettingsViewModel
import org.gemini.ui.forge.viewmodel.AppUpdateViewModel
import org.gemini.ui.forge.viewmodel.AppViewModel
import kotlin.time.Duration.Companion.milliseconds

private var originalSystemLanguage: String? = null

@Composable
fun App(typography: Typography? = null) {
    if (originalSystemLanguage == null) {
        originalSystemLanguage = Locale.current.language
    }

    var languageKey by remember { mutableStateOf(0) }
    val storage = remember { LocalFileStorage() }
    val templateRepo = remember { TemplateRepository(storage) }
    val focusRequester = remember { FocusRequester() }
    val tooltipState = remember { GlobalTooltipState() }

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
                    aiService = AIGenerationService(storage, cloudAssetManager, configManager)
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
                    val sysLang = originalSystemLanguage ?: Locale.current.language
                    if (sysLang.lowercase().startsWith("zh")) "zh" else "en"
                } else globalState.languageCode
                setAppLanguage(effectiveLang)
            }

            LaunchedEffect(updateStatus) {
                when (updateStatus) {
                    is UpdateStatus.Available -> {
                        val info = (updateStatus as UpdateStatus.Available).info
                        Toast.show(
                            message = "发现新版本 v${info.version}！",
                            type = ToastType.INFO,
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
                                type = ToastType.SUCCESS
                            )
                        }
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Toast.show(
                            message = "下载完成，准备重启安装！",
                            type = ToastType.SUCCESS,
                            durationMillis = 5000L
                        )
                    }

                    is UpdateStatus.Error -> {
                        val errorMsg = (updateStatus as UpdateStatus.Error).message
                        Toast.show(
                            message = "更新失败: $errorMsg",
                            type = ToastType.ERROR,
                            durationMillis = 8000L
                        )
                    }

                    else -> {}
                }
            }

            var showCloudAssetDialog by remember { mutableStateOf(false) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showCompileDialog by remember { mutableStateOf(false) }
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
                        .onKeyEvent { event ->
                            AppLogger.d("App", "⌨️ 捕获到按键: ${event.key}, type: ${event.type}")
                            // 全局快捷键处理
                            globalState.shortcuts.forEach { (action, shortcut) ->
                                if (ShortcutUtils.isMatch(event, shortcut)) {
                                    appViewModel.dispatchShortcutEvent(action)
                                    return@onKeyEvent true
                                }
                            }
                            false
                        }
                ) {
                    if (showCompileDialog) {
                        CompileConfigDialog(
                            initialConfig = globalState.compileConfig,
                            onDismiss = { showCompileDialog = false },
                            onConfirm = { config ->
                                if (config.rootDir.isBlank()) {
                                    Toast.show("请先配置运行环境根目录 (rootDir)", ToastType.ERROR)
                                    return@CompileConfigDialog
                                }
                                if (config.outputDir.isBlank()) {
                                    Toast.show("请先配置资源保存目录 (outputDir)", ToastType.ERROR)
                                    return@CompileConfigDialog
                                }
                                settingsViewModel.saveCompileConfig(config)
                                appViewModel.updateCompileConfig(config)
                                showCompileDialog = false
                                
                                // 执行编译导出逻辑
                                coroutineScope.launch {
                                    val wsConfig = templateRepo.loadWorkspaceConfig(appState.projectName)
                                    val compilerService = CompilerService()
                                    val success = compilerService.compileProject(
                                        projectName = appState.projectName,
                                        projectState = appState.project,
                                        rootDir = config.rootDir,
                                        outputDir = config.outputDir,
                                        resourceConfigPath = wsConfig?.resourceConfigPath,
                                        obfuscateAssets = config.obfuscateAssets
                                    )
                                    if (success) {
                                        Toast.show("编译导出成功", ToastType.SUCCESS)
                                    } else {
                                        Toast.show("编译导出失败，请查看日志", ToastType.ERROR)
                                    }
                                }
                            }
                        )
                    }



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
                        CloudAssetDialog(
                            cloudAssetManager = appViewModel.cloudAssetManager,
                            onDismiss = { showCloudAssetDialog = false }
                        )
                    }

                    if (showHelpDialog) {
                        HelpDialog(onDismiss = { showHelpDialog = false })
                    }

                    if (showSettingsDialog) {
                        AppSettingsDialog(
                            globalState = globalState,
                            appViewModel = appViewModel,
                            settingsViewModel = settingsViewModel,
                            envViewModel = envViewModel,
                            updateViewModel = updateViewModel,
                            initialCategory = settingsInitialCategory,
                            onDismiss = { showSettingsDialog = false },
                            onLanguageChanged = {
                                languageKey++
                            }
                        )
                    }


                    val statusMessage by AppLogger.statusMessage.collectAsState()
                    val showLogViewer by AppLogger.showLogViewer.collectAsState()

                    if (showLogViewer) {
                        LogViewerDialog(
                            onDismiss = { AppLogger.toggleLogViewer(false) }
                        )
                    }

                    Scaffold(

                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AppTopBar(
                                viewModel = appViewModel,
                                globalState = globalState,
                                onNavigateHome = {
                                    if (appState.isDirty) {
                                        showExitConfirmDialog = true
                                    } else {
                                        appViewModel.navigateTo(AppScreen.HOME)
                                    }
                                },
                                onCloudAssetManagerClicked = { showCloudAssetDialog = true },
                                onCompileClicked = { showCompileDialog = true },
                                onSettingsClicked = {
                                    settingsInitialCategory = SettingCategory.GENERAL
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
                                        appViewModel = appViewModel,
                                        templateRepo = templateRepo
                                    )
                                }

                                AppScreen.PROJECT_WORKSPACE -> {
                                    ProjectWorkspaceScreen(
                                        appViewModel = appViewModel,
                                        appState = appState,
                                        globalState = globalState,
                                        templateRepo = templateRepo,
                                        configManager = configManager
                                    )
                                }

                                AppScreen.TEMPLATE_GENERATOR -> {
                                    TemplateGeneratorScreen(
                                        appViewModel = appViewModel,
                                        globalState = globalState,
                                        templateRepo = templateRepo
                                    )
                                }
                            }
                        }
                    }

                    // 在所有 UI 的最上层挂载全局 Toast 容器
                    AppToastContainer(
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
