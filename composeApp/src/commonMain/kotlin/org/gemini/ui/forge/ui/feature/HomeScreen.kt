package org.gemini.ui.forge.ui.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import org.gemini.ui.forge.model.app.UIModule
import org.gemini.ui.forge.model.app.AppScreen
import org.gemini.ui.forge.model.gameproject.GameProjectInfo
import org.gemini.ui.forge.ui.feature.gameproject.GameProjectCard
import org.gemini.ui.forge.ui.feature.gameproject.NewGameProjectCard
import org.gemini.ui.forge.ui.feature.workspace.ModuleCard
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.AppViewModel
import org.gemini.ui.forge.viewmodel.GameProjectViewModel
import org.gemini.ui.forge.data.repository.TemplateRepository
import org.gemini.ui.forge.state.ui.ProjectState
import org.gemini.ui.forge.getPlatform

/**
 * 应用主界面（首页）。
 * 单一卡片展示模块：新建游戏项目入口卡片、已纳管的游戏项目卡片与
 * UI 模块模板卡片混排在同一条卡片带中，仅通过卡片风格区分类型
 * （游戏项目卡片为 secondaryContainer 色调 + "游戏项目"角标）。
 *
 * @param appViewModel 全局基础 ViewModel。
 * @param templateRepo 模板数据存储库。
 * @param gameProjectViewModel 游戏项目管理 ViewModel。
 */
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    templateRepo: TemplateRepository,
    gameProjectViewModel: GameProjectViewModel
) {
    var templatesList by remember { mutableStateOf(emptyList<Pair<String, ProjectState>>()) }
    val coroutineScope = rememberCoroutineScope()
    var moduleToDelete by remember { mutableStateOf<UIModule?>(null) }
    var projectToDelete by remember { mutableStateOf<GameProjectInfo?>(null) }

    LaunchedEffect(templateRepo) {
        templatesList = templateRepo.getTemplates()
    }

    val modules = remember(templatesList) {
        templatesList.map { (name, projectState) ->
            UIModule(id = name, nameStr = name, projectState = projectState)
        }
    }

    // 外层先量取视口尺寸：verticalScroll 内部的 fillMaxSize 高度分量不生效（无限高度约束），
    // 因此必须在滚动容器外获取真实视口高度，用于计算内容区的最小高度
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 内容区最小高度 = max(保底 440dp, 视口高)：窗口足够高时卡片带整体垂直居中，
        // 窗口过矮时保持 440dp 保底高度，配合垂直滚动保证内容完整可访问
        val contentMinHeight = maxOf(440.dp, maxHeight)

        // 整页垂直可滚动：小窗口下卡片带仍可完整访问
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // 统一卡片展示区：单条卡片带垂直居中
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = contentMinHeight),
                contentAlignment = Alignment.Center
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    // 新建游戏项目入口（恒显，虚线边框风格）
                    item(key = "new_game_project") {
                        NewGameProjectCard(
                            onCreate = { appViewModel.navigateTo(AppScreen.GAME_PROJECT_MANAGER) }
                        )
                    }

                    // 已纳管游戏项目卡片（secondaryContainer 色调 + 角标区分）
                    items(gameProjectViewModel.projects, key = { it.id }) { info ->
                        GameProjectCard(
                            info = info,
                            onOpen = {
                                gameProjectViewModel.openProject(info)
                                appViewModel.navigateTo(AppScreen.GAME_PROJECT_WORKSPACE)
                            },
                            onOpenFileDir = { getPlatform().openInFileExplorer(info.localPath) },
                            onDelete = { projectToDelete = info }
                        )
                    }

                    // UI 模块模板卡片（原有风格不变）
                    items(modules, key = { it.id }) { module ->
                        ModuleCard(
                            module = module,
                            onOpenWorkspace = {
                                appViewModel.loadProject(
                                    module.nameStr ?: module.id,
                                    module.projectState!!
                                )
                                appViewModel.navigateTo(AppScreen.PROJECT_WORKSPACE)
                            },
                            onOpenFileDir = {
                                coroutineScope.launch {
                                    templateRepo.openFileDir(module)
                                }
                            },
                            onDelete = { moduleToDelete = module }
                        )
                    }
                }
            }
        }
    }

    // 模板删除确认对话框
    moduleToDelete?.let { module ->
        val title = if (module.nameRes != null) stringResource(module.nameRes) else module.nameStr ?: "Unknown"
        AlertDialog(
            onDismissRequest = { moduleToDelete = null },
            title = { Text(stringResource(Res.string.dialog_delete_title)) },
            text = { Text("${stringResource(Res.string.dialog_delete_message)}\n($title)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            templateRepo.deleteTemplate(module.id)
                            templatesList = templateRepo.getTemplates()
                            moduleToDelete = null
                        }
                    },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.dialog_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { moduleToDelete = null },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.dialog_action_cancel))
                }
            }
        )
    }

    // 游戏项目移除确认对话框（默认仅移除注册记录；可勾选同时删除磁盘上的项目目录）
    projectToDelete?.let { info ->
        // 磁盘删除选项状态：每次弹窗（不同项目）重新打开时重置为不选中，避免误删
        val deleteFilesOnDisk = remember(info.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(Res.string.gp_delete_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.gp_delete_confirm_message, info.name))
                    Spacer(Modifier.height(8.dp))
                    // 可选项：同时删除磁盘文件（默认不选中）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFilesOnDisk.value = !deleteFilesOnDisk.value }
                    ) {
                        Checkbox(
                            checked = deleteFilesOnDisk.value,
                            onCheckedChange = { deleteFilesOnDisk.value = it }
                        )
                        Text(stringResource(Res.string.gp_delete_delete_files), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        gameProjectViewModel.deleteProject(info.id, deleteFilesOnDisk.value)
                        projectToDelete = null
                    },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.gp_card_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }, shape = AppShapes.medium) {
                    Text(stringResource(Res.string.gp_clone_cancel))
                }
            }
        )
    }
}
