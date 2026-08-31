package org.gemini.ui.forge.ui.feature.gameproject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import geminiuiforge.composeapp.generated.resources.Res
import geminiuiforge.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.gemini.ui.forge.model.gameproject.CloneStep
import org.gemini.ui.forge.model.gameproject.InstallChoice
import org.gemini.ui.forge.service.CloneRefType
import org.gemini.ui.forge.service.CommitSummary
import org.gemini.ui.forge.service.GitRefCatalog
import org.gemini.ui.forge.service.RefSelection
import org.gemini.ui.forge.ui.theme.AppShapes
import org.gemini.ui.forge.viewmodel.GameProjectViewModel

/**
 * 项目创建流程弹窗。
 * 左侧为步骤进度列表，右侧为执行日志 / 游戏选择覆盖层 / 依赖检查面板：
 * - 游戏扫描完成后在右侧覆盖弹出单选列表，选择后继续资源下载；
 * - 克隆完成后切换到 pnpm 依赖检查面板（缺失项红色显示，可选安装方式）；
 * - 全部处理完成后点击"进入项目"进入项目专属工作台。
 *
 * @param viewModel 游戏项目管理 ViewModel
 * @param onEnterWorkspace 进入工作台回调（由外部完成导航）
 */
@Composable
fun GameProjectCloneDialog(
    viewModel: GameProjectViewModel,
    onEnterWorkspace: () -> Unit = {}
) {
    if (!viewModel.cloneDialogVisible) return
    val step = viewModel.cloneProgress.step

    Dialog(
        onDismissRequest = {
            // 仅失败状态允许点外部关闭；执行中必须显式取消
            if (step == CloneStep.FAILED) viewModel.cancelClone()
        },
        // 关闭"平台默认宽度"限制：桌面端该选项默认开启时会强制按内容宽度换行测量，
        // 导致 fillMaxWidth 百分比尺寸失效（弹窗被挤成窄条）
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 尺寸按宿主窗口比例约束：相对占比不受全局紧凑缩放（Density 重映射）影响，避免弹窗视觉缩水
        Surface(shape = AppShapes.large, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(0.62f).fillMaxHeight(0.82f).padding(20.dp)
            ) {
                Text(
                    text = stringResource(Res.string.gp_clone_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                // 失败错误条
                viewModel.cloneErrorMessage?.let { error ->
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(modifier = Modifier.weight(1f)) {
                    // 左侧：步骤列表
                    Column(
                        modifier = Modifier.width(220.dp).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val stepItems = listOf(
                            CloneStep.CREATE_FOLDER to Res.string.gp_step_create_folder,
                            CloneStep.CONNECT_FETCH to Res.string.gp_step_connect_fetch,
                            CloneStep.SELECT_VERSION to Res.string.gp_step_select_version,
                            CloneStep.SCAN_GAMES to Res.string.gp_step_scan_games,
                            CloneStep.WAIT_SELECT_GAME to Res.string.gp_step_wait_select,
                            CloneStep.APPLY_FILTER to Res.string.gp_step_apply_filter,
                            CloneStep.DOWNLOAD_ASSETS to Res.string.gp_step_download,
                            CloneStep.CHECK_DEPENDENCIES to Res.string.gp_step_check_deps,
                            CloneStep.COMPLETE to Res.string.gp_step_complete
                        )
                        stepItems.forEach { (stepEnum, labelRes) ->
                            StepRow(
                                label = stringResource(labelRes),
                                state = when {
                                    step == CloneStep.FAILED && stepEnum.ordinal >= CloneStep.CONNECT_FETCH.ordinal -> StepState.FAILED
                                    stepEnum.ordinal < step.ordinal -> StepState.DONE
                                    // 终态 COMPLETE 属于"全部完成"的完结标记，自身直接显示对勾而非转圈
                                    stepEnum == step -> if (stepEnum == CloneStep.COMPLETE) StepState.DONE else StepState.ACTIVE
                                    else -> StepState.PENDING
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // 右侧：内容区（日志 / 依赖面板 + 游戏选择覆盖层）
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (step == CloneStep.CHECK_DEPENDENCIES || step == CloneStep.COMPLETE) {
                            DependencyPanel(viewModel)
                        } else {
                            LogPanel(viewModel)
                        }
                        // 游戏选择覆盖层
                        viewModel.pendingGames?.let { games ->
                            GameSelectOverlay(games = games, onConfirm = { viewModel.confirmGame(it) })
                        }
                        // 版本选择覆盖层（fetch 完成后挂起等待用户选择检出版本）
                        viewModel.pendingVersionCatalog?.let { catalog ->
                            VersionSelectOverlay(
                                catalog = catalog,
                                onConfirm = { viewModel.confirmVersion(it) },
                                onCancel = { viewModel.cancelVersionChoice() }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 底部操作按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (step) {
                        CloneStep.FAILED -> {
                            TextButton(onClick = { viewModel.cancelClone() }, shape = AppShapes.medium) {
                                Text(stringResource(Res.string.gp_clone_close))
                            }
                        }
                        CloneStep.COMPLETE -> {
                            Button(
                                onClick = {
                                    viewModel.finishProject()?.let { onEnterWorkspace() }
                                },
                                shape = AppShapes.medium
                            ) {
                                Text(stringResource(Res.string.gp_deps_enter))
                            }
                        }
                        else -> {
                            TextButton(onClick = { viewModel.cancelClone() }, shape = AppShapes.medium) {
                                Text(stringResource(Res.string.gp_clone_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 步骤行状态。
 */
private enum class StepState { PENDING, ACTIVE, DONE, FAILED }

/**
 * 单个步骤行（私有组件）：图标 + 文案。
 */
@Composable
private fun StepRow(label: String, state: StepState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            StepState.DONE -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            StepState.ACTIVE -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            StepState.FAILED -> Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            StepState.PENDING -> Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(12.dp)
            ) {}
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                StepState.DONE, StepState.ACTIVE -> MaterialTheme.colorScheme.onSurface
                StepState.FAILED -> MaterialTheme.colorScheme.error
                StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 执行日志面板（私有组件）：自动滚动到底部。
 */
@Composable
private fun LogPanel(viewModel: GameProjectViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.cloneLogs.size) {
        if (viewModel.cloneLogs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.cloneLogs.size - 1)
        }
    }
    Column {
        Text(
            text = stringResource(Res.string.gp_clone_log_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            // 克隆流程日志：包裹可选文本容器，支持鼠标拖选复制
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(viewModel.cloneLogs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 游戏选择覆盖层（私有组件）：单选列表 + 确认按钮。
 * 标题与确认按钮固定不滚动，仅游戏列表区域可滚动；点击整行即可选中。
 */
@Composable
private fun GameSelectOverlay(games: List<String>, onConfirm: (String) -> Unit) {
    var selected by remember(games) { mutableStateOf<String?>(null) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = AppShapes.small
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.gp_clone_select_hint),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            // 游戏列表：唯一滚动区域，整行可点击选中
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                games.forEach { game ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = game }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        // 圆点仅作状态展示，点击行为由整行接管
                        RadioButton(
                            selected = selected == game,
                            onClick = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = game,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected == game) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                shape = AppShapes.medium
            ) {
                Text(stringResource(Res.string.gp_clone_confirm))
            }
        }
    }
}

/**
 * 版本选择覆盖层（私有组件）：分支 / 标签 / 提交三类别切换 + 懒加载列表 + 手动输入。
 * 切换类别时才拉取对应列表（服务端已限流：分支/标签 ≤200 条、提交最近 50 条）；
 * 点击列表项自动填入手动输入框，检出时以输入框内容为准。
 */
@Composable
private fun VersionSelectOverlay(
    catalog: GitRefCatalog,
    onConfirm: (RefSelection) -> Unit,
    onCancel: () -> Unit
) {
    // 当前类别（默认分支）
    var refType by remember { mutableStateOf(CloneRefType.BRANCH) }
    // 三类列表缓存：切换回来不重复拉取
    var branchList by remember { mutableStateOf<List<String>?>(null) }
    var tagList by remember { mutableStateOf<List<String>?>(null) }
    var commitList by remember { mutableStateOf<List<CommitSummary>?>(null) }
    // 手动输入值（点击列表项自动填入；提交类别填入短哈希）
    var manualInput by remember { mutableStateOf("") }

    // 切换类别时懒加载对应列表（已加载则跳过）
    LaunchedEffect(catalog, refType) {
        when (refType) {
            CloneRefType.BRANCH -> if (branchList == null) branchList = catalog.branches()
            CloneRefType.TAG -> if (tagList == null) tagList = catalog.tags()
            CloneRefType.COMMIT -> if (commitList == null) commitList = catalog.commits()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = AppShapes.small
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.gp_clone_select_version_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            // 类别切换芯片（切换时清空手动输入，避免类别间语义混淆）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = refType == CloneRefType.BRANCH,
                    onClick = { refType = CloneRefType.BRANCH; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_branch)) }
                )
                FilterChip(
                    selected = refType == CloneRefType.TAG,
                    onClick = { refType = CloneRefType.TAG; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_tag)) }
                )
                FilterChip(
                    selected = refType == CloneRefType.COMMIT,
                    onClick = { refType = CloneRefType.COMMIT; manualInput = "" },
                    label = { Text(stringResource(Res.string.gp_form_ref_commit)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            // 列表区：唯一滚动区域（加载中 / 空态 / 数据列表三态）
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val currentList: List<String>? = when (refType) {
                    CloneRefType.BRANCH -> branchList
                    CloneRefType.TAG -> tagList
                    CloneRefType.COMMIT -> commitList?.map { "${it.shortHash}  ${it.subject}" }
                }
                when {
                    currentList == null -> {
                        // 加载中
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.gp_clone_ref_loading),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    currentList.isEmpty() -> {
                        // 空态
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.gp_clone_ref_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        // 数据列表：整行点击选中并回填输入框
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentList) { entry ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // 提交条目形如 "短哈希  说明"，回填短哈希供检出
                                            manualInput = entry.substringBefore("  ")
                                        }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = entry,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // 手动输入框（跟随当前类别语义；点击列表项自动填入）
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                placeholder = { Text(stringResource(Res.string.gp_clone_ref_manual_ph)) },
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // 操作区：默认分支快捷入口 + 取消 + 检出
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onConfirm(RefSelection(CloneRefType.BRANCH, null)) },
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.gp_clone_ref_default))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel, shape = AppShapes.medium) {
                    Text(stringResource(Res.string.gp_clone_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val value = manualInput.trim()
                        onConfirm(RefSelection(refType, value.ifBlank { null }))
                    },
                    // 标签 / 提交类别必须提供值；分支类别允许留空（使用远端默认分支）
                    enabled = refType == CloneRefType.BRANCH || manualInput.isNotBlank(),
                    shape = AppShapes.medium
                ) {
                    Text(stringResource(Res.string.gp_clone_ref_confirm))
                }
            }
        }
    }
}

/**
 * 依赖检查面板（私有组件）。
 * 已安装项正常显示，缺失项红色显示并提供安装方式选择（不安装 / pnpm 安装）；
 * 提供重新检查与安装所选操作；全部处理完成后可进入项目。
 */
@Composable
private fun DependencyPanel(viewModel: GameProjectViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.gp_deps_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { viewModel.recheckDependencies() }, shape = AppShapes.medium) {
                Text(stringResource(Res.string.gp_deps_recheck))
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)
            ) {
                items(viewModel.dependencies) { dep ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dep.name + if (dep.isDev) " (dev)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = dep.versionRange,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (dep.installed) {
                                stringResource(Res.string.gp_deps_installed)
                            } else {
                                stringResource(Res.string.gp_deps_missing)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (dep.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (!dep.installed) {
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = dep.installChoice == InstallChoice.SKIP,
                                onClick = { viewModel.updateDepChoice(dep.name, InstallChoice.SKIP) },
                                label = { Text(stringResource(Res.string.gp_deps_choice_skip), style = MaterialTheme.typography.labelSmall) }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = dep.installChoice == InstallChoice.PNPM,
                                onClick = { viewModel.updateDepChoice(dep.name, InstallChoice.PNPM) },
                                label = { Text(stringResource(Res.string.gp_deps_choice_pnpm), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
        // 安装日志区：安装中实时滚动输出；安装后保留（含完成/失败标记行）供回看
        if (viewModel.depsInstalling || viewModel.installLogs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = AppShapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    val installLogState = rememberLazyListState()
                    // 新日志到来时自动滚动到底部
                    LaunchedEffect(viewModel.installLogs.size) {
                        if (viewModel.installLogs.isNotEmpty()) {
                            installLogState.animateScrollToItem(viewModel.installLogs.size - 1)
                        }
                    }
                    // 安装日志：包裹可选文本容器，支持鼠标拖选复制
                    SelectionContainer {
                        LazyColumn(
                            state = installLogState,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            items(viewModel.installLogs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val hasPnpmChoice = viewModel.dependencies.any { it.installChoice == InstallChoice.PNPM }
            if (hasPnpmChoice) {
                Button(
                    onClick = { viewModel.installSelected() },
                    enabled = !viewModel.depsInstalling,
                    shape = AppShapes.medium
                ) {
                    if (viewModel.depsInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(Res.string.gp_deps_install))
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
