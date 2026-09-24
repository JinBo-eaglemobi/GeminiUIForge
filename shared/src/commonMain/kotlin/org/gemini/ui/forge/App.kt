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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import org.gemini.ui.forge.ui.dialog.system.*
import org.gemini.ui.forge.ui.dialog.system.settings.*
import org.gemini.ui.forge.ui.dialog.asset.*
import org.gemini.ui.forge.ui.dialog.ai.*
import org.gemini.ui.forge.ui.dialog.layer.*
import org.gemini.ui.forge.ui.feature.HomeScreen
import org.gemini.ui.forge.ui.feature.ProjectWorkspaceScreen
import org.gemini.ui.forge.ui.feature.TemplateGeneratorScreen
import org.gemini.ui.forge.ui.feature.gameproject.GameProjectCreateScreen
import org.gemini.ui.forge.ui.feature.gameproject.GameProjectWorkspaceScreen
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.ui.theme.AppSpacing
import org.gemini.ui.forge.ui.theme.AppTheme
import org.gemini.ui.forge.ui.theme.LocalAppSpacing
import org.gemini.ui.forge.utils.AppLogger
import org.gemini.ui.forge.utils.LocalFileStorage
import org.gemini.ui.forge.utils.ShortcutUtils
import org.gemini.ui.forge.utils.Toast
import org.gemini.ui.forge.service.mcp.McpController
import org.gemini.ui.forge.service.mcp.McpTrafficInspector
import org.gemini.ui.forge.ui.dialog.mcp.McpTrafficInspectorDialog
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Terminal
import org.gemini.ui.forge.viewmodel.AppEnvViewModel
import org.gemini.ui.forge.viewmodel.AppSettingsViewModel
import org.gemini.ui.forge.viewmodel.AppUpdateViewModel
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
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
            // 游戏项目管理模块 ViewModel
            val gameProjectViewModel: GameProjectViewModel = viewModel {
                GameProjectViewModel(storage = storage, configManager = configManager)
            }

            val appState by appViewModel.state.collectAsState()
            val updateStatus by updateViewModel.status.collectAsState()
            val envStatus by envViewModel.status.collectAsState()

            val globalState = appState.globalState

            var showCloudAssetDialog by remember { mutableStateOf(false) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showMcpDialog by remember { mutableStateOf(false) }
            var showMcpTrafficDialog by remember { mutableStateOf(false) }
            var showCompileDialog by remember { mutableStateOf(false) }
            var showHelpDialog by remember { mutableStateOf(false) }
            var showExitConfirmDialog by remember { mutableStateOf(false) }
            var settingsInitialCategory by remember { mutableStateOf(SettingCategory.GENERAL) }

            // 同步当前屏幕与未保存状态到 MCP UI 路线图注册中心
            LaunchedEffect(globalState.currentScreen, appState.projectName, appState.isDirty) {
                val screenType = when (globalState.currentScreen) {
                    AppScreen.HOME -> org.gemini.ui.forge.service.mcp.ScreenType.TEMPLATE_LIST
                    AppScreen.PROJECT_WORKSPACE -> org.gemini.ui.forge.service.mcp.ScreenType.PROJECT_WORKSPACE
                    AppScreen.TEMPLATE_GENERATOR -> org.gemini.ui.forge.service.mcp.ScreenType.VISUAL_CHAT_STUDIO
                    else -> org.gemini.ui.forge.service.mcp.ScreenType.TEMPLATE_LIST
                }
                org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.updateCurrentScreen(screenType, appState.projectName)
                org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.setUnsavedWorkspaceChanges(appState.isDirty)
            }

            // 同步弹窗状态到 MCP 路线图
            LaunchedEffect(showExitConfirmDialog) {
                if (showExitConfirmDialog) {
                    org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.pushDialog(
                        org.gemini.ui.forge.service.mcp.DialogDescriptor(
                            id = "dialog_unsaved_changes",
                            title = "未保存修改警告弹窗",
                            description = "离开工作区前存在未保存脏数据，必须点击保存并退出或不保存退出",
                            dismissActionNodeId = "btn_cancel_leave",
                            confirmActionNodeId = "btn_save_and_leave",
                            hasUnsavedRisk = true
                        )
                    )
                } else {
                    org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.removeDialog("dialog_unsaved_changes")
                }
            }

            // 注册全局 UI 节点动作执行器 (用于 MCP 真实点击与输入流转)
            DisposableEffect(Unit) {
                org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.setActionHandler(object : org.gemini.ui.forge.service.mcp.UiNodeActionHandler {
                    override suspend fun clickNode(nodeId: String): Boolean {
                        return when (nodeId) {
                            "btn_back_home" -> {
                                if (appState.isDirty) {
                                    showExitConfirmDialog = true
                                } else {
                                    appViewModel.navigateTo(AppScreen.HOME)
                                }
                                true
                            }
                            "btn_save_and_leave" -> {
                                if (showExitConfirmDialog) {
                                    appViewModel.dispatchSaveEvent()
                                    showExitConfirmDialog = false
                                    appViewModel.navigateTo(AppScreen.HOME)
                                    true
                                } else false
                            }
                            "btn_discard_and_leave" -> {
                                if (showExitConfirmDialog) {
                                    appViewModel.setDirty(false)
                                    showExitConfirmDialog = false
                                    appViewModel.navigateTo(AppScreen.HOME)
                                    true
                                } else false
                            }
                            "btn_cancel_leave" -> {
                                if (showExitConfirmDialog) {
                                    showExitConfirmDialog = false
                                    true
                                } else false
                            }
                            "btn_open_mcp_console" -> {
                                showMcpTrafficDialog = true
                                true
                            }
                            "btn_close_mcp_console" -> {
                                showMcpTrafficDialog = false
                                true
                            }
                            else -> false
                        }
                    }

                    override suspend fun setInputText(nodeId: String, text: String): Boolean {
                        // 预留全局输入槽位扩展
                        return false
                    }
                })
                onDispose {
                    org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.setActionHandler(null)
                }
            }

            // 监听 MCP UI 联动事件 (仅当开启前台视觉跟随时自动响应导航流转)
            LaunchedEffect(Unit) {
                val savedFollow = configManager.loadKey("MCP_UI_FOLLOW_ENABLED")
                if (savedFollow != null) {
                    org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.setUiFollowEnabled(savedFollow.toBoolean())
                }

                org.gemini.ui.forge.service.mcp.McpUiBridge.events.collect { event ->
                    if (!org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.isUiFollowEnabled.value) {
                        return@collect // 未开启视觉跟随时，后台静默执行，不干扰当前视图
                    }
                    when (event) {
                        is org.gemini.ui.forge.service.mcp.McpUiEvent.NavigateToProject -> {
                            val state = event.projectState ?: templateRepo.getTemplates().find { it.first == event.projectName }?.second
                            if (state != null) {
                                appViewModel.loadProject(event.projectName, state)
                                appViewModel.navigateTo(AppScreen.PROJECT_WORKSPACE)
                            }
                        }
                        is org.gemini.ui.forge.service.mcp.McpUiEvent.RefreshWorkspace -> {
                            // 刷新当前工程状态
                        }
                        else -> {}
                    }
                }
            }

            LaunchedEffect(Unit) {
                delay(100.milliseconds)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                }
                appViewModel.syncInitialSettings(settingsViewModel.getConfigManager())
                updateViewModel.checkForUpdates()
                // 启动系统与进程性能监控 (1.5秒实时轮询)
                org.gemini.ui.forge.service.SystemPerformanceMonitor.start()
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

            AppTheme(
                themeMode = globalState.themeMode,
                layoutMode = globalState.layoutMode,
                customTypography = typography
            ) {
                val coroutineScope = rememberCoroutineScope()
                val toastData by Toast.toastData.collectAsState()

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



                    if (showMcpDialog) {
                        org.gemini.ui.forge.ui.dialog.mcp.McpServerDialog(
                            onDismissRequest = { showMcpDialog = false },
                            configManager = configManager
                        )
                    }

                    if (showMcpTrafficDialog) {
                        McpTrafficInspectorDialog(
                            onDismissRequest = { showMcpTrafficDialog = false }
                        )
                    }

                    if (showExitConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitConfirmDialog = false },
                            // 引入标准弹窗黄金宽度 Design Token（480.dp），横向托平，杜绝硬编码数值
                            modifier = Modifier.width(LocalAppSpacing.current.dialogConfigWidth),
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
                                onGlobalStyleClicked = { appViewModel.dispatchGlobalStyleEvent() },
                                onSettingsClicked = {
                                    settingsInitialCategory = SettingCategory.GENERAL
                                    showSettingsDialog = true
                                },
                                onMcpClicked = { showMcpDialog = true },
                                onHelpClicked = { showHelpDialog = true }
                            )
                        },
                        bottomBar = {
                            val isMcpRunning by McpController.isRunning.collectAsState()
                            val mcpNodes by McpTrafficInspector.nodes.collectAsState()
                            val bottomBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .drawBehind {
                                        drawLine(
                                            color = bottomBorderColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // 左侧：状态消息
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = statusMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 右侧：系统与进程资源监视胶囊 + MCP 监控胶囊 (仅开启时显示) + 全局系统日志按钮
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. 系统与进程性能监控微胶囊 (常驻显示实时 RAM/CPU，Hover 展开 Bento 详情面板)
                                        SystemResourceCapsule()

                                        // 2. 当 MCP 启动运行后动态显现
                                        if (isMcpRunning) {
                                            Surface(
                                                shape = AppShapes.small,
                                                color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                                                modifier = Modifier
                                                    .clip(AppShapes.small)
                                                    .clickable { showMcpTrafficDialog = true }
                                                    .tip("点击查看 MCP 实时通信数据流与连接节点")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(Color(0xFF4CAF50), shape = CircleShape)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Terminal,
                                                        contentDescription = null,
                                                        tint = Color(0xFF2E7D32),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = if (mcpNodes.isNotEmpty()) "MCP 通信监控 (${mcpNodes.size} 节点)" else "MCP 通信监控",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF2E7D32),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { AppLogger.toggleLogViewer(true) },
                                            modifier = Modifier.size(24.dp).tip("查看系统运行日志")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "查看日志",
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                                        templateRepo = templateRepo,
                                        gameProjectViewModel = gameProjectViewModel
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

                                AppScreen.GAME_PROJECT_MANAGER -> {
                                    GameProjectCreateScreen(
                                        viewModel = gameProjectViewModel,
                                        appViewModel = appViewModel
                                    )
                                }

                                AppScreen.GAME_PROJECT_WORKSPACE -> {
                                    GameProjectWorkspaceScreen(viewModel = gameProjectViewModel)
                                }
                            }
                        }
                    }

                    // AI 自动执行且开启视觉跟随模式时：主界面手势拦截阻断层（仅放行底部 30.dp 状态栏）
                    val isAiExecuting by org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.isAiExecuting.collectAsState()
                    val isUiFollowEnabled by org.gemini.ui.forge.service.mcp.UiRoadmapRegistry.isUiFollowEnabled.collectAsState()
                    if (isAiExecuting && isUiFollowEnabled) {
                        var lastToastTime by remember { mutableStateOf(0L) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 30.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            event.changes.forEach { it.consume() }
                                            if (event.type == PointerEventType.Press) {
                                                val now = getCurrentTimeMillis()
                                                if (now - lastToastTime > 2000L) {
                                                    lastToastTime = now
                                                    Toast.show("AI 正在自动执行中，界面交互已锁定（底部状态栏可用）", ToastType.INFO)
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }

                    // 在所有 UI 的最上层挂载全局 Toast 容器
                    AppToastContainer(
                        toastData = toastData,
                        onDismiss = { Toast.hide() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // 挂载全局提示宿主 (Tooltip)
                    GlobalTooltipHost()

                    // 挂载 MCP 外部 AI 助手人机交互弹窗与通知宿主
                    org.gemini.ui.forge.ui.dialog.system.McpInteractionHost()
                }
            }
        }
    }
}
